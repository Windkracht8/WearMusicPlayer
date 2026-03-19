/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 */
package com.windkracht8.wearmusicplayer.data

import android.net.Uri
import android.os.Environment
import com.windkracht8.wearmusicplayer.logE
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File

object Library {
	private val _phoneRoot = MutableStateFlow(LibDir("Music", null))
	val phoneRoot = _phoneRoot.asStateFlow()
	private val _watchRoot = MutableStateFlow<LibDir?>(null)
	val watchRoot = _watchRoot.asStateFlow()

	enum class Status { SCAN, READY }
	private val _status = MutableStateFlow<Status?>(null)
	val status = _status.asStateFlow()
	private val _itemStates = MutableStateFlow<Map<LibItem, LibItemState>>(emptyMap())
	val itemStates = _itemStates.asStateFlow()

	fun scanFiles(dir: String = "Music") {
		_status.value = Status.SCAN
		CoroutineScope(Dispatchers.IO).launch {
			val newRoot = LibDir(Environment.getExternalStorageDirectory().toString() + "/$dir", null)
			scanFilesDir(newRoot)
			_phoneRoot.value = newRoot
			_status.value = Status.READY
		}
	}
	private fun scanFilesDir(libDir: LibDir) {
		val files = File(libDir.fullPath).listFiles() ?: return
		for(file in files) {
			if(file.name.startsWith(".")) continue
			if(file.isDirectory) {
				val libDirSub = LibDir(file.absolutePath, libDir)
				scanFilesDir(libDirSub)
				if(libDirSub.libDirs.isEmpty() && libDirSub.libTracks.isEmpty()) continue
				libDir.libDirs.add(libDirSub)
			}
			if(!file.name.isTrack()) continue
			libDir.libTracks.add(LibTrack(file.absolutePath, libDir))
		}
		libDir.sort()
	}

	suspend fun updateLibWithFilesOnWatch(filesOnWatch: JSONArray) {
		try {
			val pathsOnWatch = (0 until filesOnWatch.length())
				.map { Uri.decode(filesOnWatch.getJSONObject(it).getString("path")) }
			repeat(100) {
				if(_status.value == Status.READY) return@repeat
				delay(100)
			}
			updateWithFilesOnWatch(pathsOnWatch)
		} catch(e: Exception) {
			logE("Library.updateLibWithFilesOnWatch: ${e.message}")
		}
	}
	private fun updateWithFilesOnWatch(pathsOnWatch: List<String>) {
		val newPhoneRoot = _phoneRoot.value
		val newWatchRoot = LibDir("", null)
		val newItemStates = mutableMapOf<LibItem, LibItemState>()
		pathsOnWatch.forEach { fullPath ->
			var phoneDir: LibDir? = newPhoneRoot
			var watchDir = newWatchRoot
			val segmentCount = fullPath.count { it == '/' }
			fullPath.split("/").forEachIndexed { index, segment ->
				if(index == segmentCount) {
					val watchTrack = LibTrack(fullPath, watchDir)
					watchDir.libTracks.add(watchTrack)
					val phoneTrack = phoneDir?.libTracks?.find { it.path == watchTrack.path }
					if(phoneTrack == null) {
						newItemStates[watchTrack] = LibItemState(status = LibItem.Status.NOT)
					} else {
						newItemStates[phoneTrack] = LibItemState(status = LibItem.Status.FULL)
					}
				} else {
					if(watchDir.libDirs.none { it.name == segment }) {
						watchDir.libDirs.add(LibDir(watchDir.fullPath + "/$segment", watchDir))
					}
					watchDir = watchDir.libDirs.first { it.name == segment }
					phoneDir = phoneDir?.libDirs?.find { it.name == segment }
				}
			}
		}
		newPhoneRoot.sort()
		fun checkPhoneStatuses(libDir: LibDir) {
			libDir.libDirs.forEach { checkPhoneStatuses(it) }
			if(libDir.libTracks.all { it in newItemStates }) {
				newItemStates[libDir] = LibItemState(status = LibItem.Status.FULL)
			} else if(libDir.libTracks.any { it in newItemStates }) {
				newItemStates[libDir] = LibItemState(status = LibItem.Status.PARTIAL)
			}
		}
		checkPhoneStatuses(newPhoneRoot)
		newWatchRoot.sort()
		fun checkWatchStatuses(libDir: LibDir) {
			libDir.libDirs.forEach { checkWatchStatuses(it) }
			if(libDir.libTracks.any { it in newItemStates } ||
				libDir.libDirs.any { it in newItemStates }
			) {
				newItemStates[libDir] = LibItemState(status = LibItem.Status.NOT)
			}
		}
		checkWatchStatuses(newWatchRoot)
		_phoneRoot.value = newPhoneRoot
		_watchRoot.value = newWatchRoot
		_itemStates.value = newItemStates
	}

	private fun String.isTrack(): Boolean = endsWith(".mp3") ||
		endsWith(".m4a") ||
		endsWith(".aac") ||
		endsWith(".amr") ||
		endsWith(".flac") ||
		endsWith(".ogg") ||
		endsWith(".opus") ||
		endsWith(".wav")

	fun setItemStatus(libItem: LibItem, status: LibItem.Status) {
		val newItemStates = _itemStates.value.toMutableMap()
		newItemStates[libItem] = LibItemState(status = status)
		_itemStates.value = newItemStates
	}
	fun setItemProgress(libItem: LibItem, progress: Float) {
		val newItemStates = _itemStates.value.toMutableMap()
		newItemStates[libItem] = LibItemState(
			status = LibItem.Status.SENDING,
			progress = progress
		)
		_itemStates.value = newItemStates
	}
	fun setItemUploaded(libItem: LibItem) {
		val newItemStates = _itemStates.value.toMutableMap()
		newItemStates[libItem] = LibItemState(status = LibItem.Status.FULL)
		if(libItem.parent != null) updatePhoneStatuses(libItem.parent, newItemStates)
		_itemStates.value = newItemStates
	}
	fun setItemDeleted(libItem: LibItem) {
		val newItemStates = _itemStates.value.toMutableMap()
		newItemStates.remove(libItem)
		if(libItem.parent != null) updatePhoneStatuses(libItem.parent, newItemStates)
		_itemStates.value = newItemStates
	}
	private fun updatePhoneStatuses(libDir: LibDir, newItemStates: MutableMap<LibItem, LibItemState>) {
		if(libDir.libTracks.all { it in newItemStates }) {
			newItemStates[libDir] = LibItemState(status = LibItem.Status.FULL)
		} else if(libDir.libTracks.any { it in newItemStates }) {
			newItemStates[libDir] = LibItemState(status = LibItem.Status.PARTIAL)
		} else {
			newItemStates.remove(libDir)
		}
		if(libDir.parent != null) updatePhoneStatuses(libDir.parent, newItemStates)
	}
	fun clearStatuses() {//called after watch disconnect
		_itemStates.value = emptyMap()
		_watchRoot.value = null
	}
}

data class LibItemState(
	val status: LibItem.Status = LibItem.Status.UNKNOWN,
	val progress: Float = -1F
)
open class LibItem(val fullPath: String, val parent: LibDir?) : Comparable<LibItem> {
	enum class Status { UNKNOWN, FULL, PARTIAL, NOT, PENDING, SENDING }
	val path: String =
		try { this.fullPath.removePrefix(Library.phoneRoot.value.fullPath + "/").removePrefix("/") }
		catch(_: Exception) { this.fullPath.removePrefix("/") }
	val name: String = path.substring(path.lastIndexOf("/") + 1)
	val depth: Int = path.count { it == '/' }
	var length: Float = 1F

	override fun compareTo(other: LibItem): Int {
		val regex = Regex("(\\d+)|(\\D+)")
		val parts1 = regex.findAll(this.name).map { it.value }.toList()
		val parts2 = regex.findAll(other.name).map { it.value }.toList()
		for(i in 0 until minOf(parts1.size, parts2.size)) {
			val p1 = parts1[i]
			val p2 = parts2[i]
			val result =
				if(p1[0].isDigit() && p2[0].isDigit()) p1.toInt().compareTo(p2.toInt())
				else p1.compareTo(p2, ignoreCase = true)
			if(result != 0) return result
		}
		return parts1.size - parts2.size
	}
}
class LibTrack(fullPath: String, parent: LibDir) : LibItem(fullPath, parent)
class LibDir(fullPath: String, parent: LibDir?) : LibItem(fullPath, parent) {
	val libTracks = mutableListOf<LibTrack>()
	val libDirs = mutableListOf<LibDir>()
	fun sort() {
		libTracks.sort()
		libDirs.sort()
		libDirs.forEach { it.sort() }
	}
}
