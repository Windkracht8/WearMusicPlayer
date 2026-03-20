/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 */
package com.windkracht8.wearmusicplayer.data

import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

object Library {
	private val _phoneRoot = MutableStateFlow(LibDir("Music", null, ""))
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
			val newRoot = LibDir(Environment.getExternalStorageDirectory().toString() + "/$dir", null, "")
			scanFilesDir(newRoot, newRoot.fullPath)
			_phoneRoot.value = newRoot
			_status.value = Status.READY
		}
	}
	private fun scanFilesDir(libDir: LibDir, rootPath: String) {
		val files = File(libDir.fullPath).listFiles() ?: return
		for(file in files) {
			if(file.name.startsWith(".")) continue
			if(file.isDirectory) {
				val libDirSub = LibDir(file.absolutePath, libDir, rootPath)
				scanFilesDir(libDirSub, rootPath)
				if(libDirSub.libDirs.isEmpty() && libDirSub.libTracks.isEmpty()) continue
				libDir.libDirs.add(libDirSub)
			}
			if(!file.name.isTrack()) continue
			libDir.libTracks.add(LibTrack(file.absolutePath, libDir, rootPath))
		}
		libDir.sort()
	}

	suspend fun gotTracksFromWatch(trackPaths: List<String>) {
		status.first { it == Status.READY }//sometimes the sync with the watch is faster than loading the local files
		val newWatchRoot = LibDir("", null, "")
		fun ensureWatchDir(fullPath: String): LibDir {
			val path = fullPath.substring(0..fullPath.lastIndexOf("/"))
			if(path.isEmpty()) return newWatchRoot
			var watchDir = newWatchRoot
			path.split("/").forEach { segment ->
				watchDir.libDirs.find { it.name == segment }?.let {
					watchDir = it
				} ?: run {
					watchDir = LibDir(watchDir.fullPath + "/$segment", watchDir, "")
					watchDir.libDirs.add(watchDir)
				}
			}
			return watchDir
		}
		val newItemStates = mutableMapOf<LibItem, LibItemState>()
		trackPaths.forEach { trackPath ->
			var phoneDir: LibDir? = _phoneRoot.value
			val segmentCount = trackPath.count { it == '/' }
			trackPath.split("/").forEachIndexed { index, segment ->
				if(index == segmentCount) {
					phoneDir?.libTracks?.find { it.name == segment }?.let {
						newItemStates[it] = LibItemState(status = LibItem.Status.FULL)
					} ?: run {
						val watchDir = ensureWatchDir(trackPath)
						val watchTrack = LibTrack(trackPath, watchDir, "")
						watchDir.libTracks.add(watchTrack)
					}
				} else {
					phoneDir = phoneDir?.libDirs?.find { it.name == segment }
				}
			}
		}
		fun checkPhoneStatuses(libDir: LibDir) {
			libDir.libDirs.forEach { checkPhoneStatuses(it) }
			if(libDir.libTracks.all { it in newItemStates } &&
				libDir.libDirs.all { newItemStates[it]?.status == LibItem.Status.FULL }
			) {
				newItemStates[libDir] = LibItemState(status = LibItem.Status.FULL)
			} else if(libDir.libTracks.any { it in newItemStates } ||
				libDir.libDirs.any { it in newItemStates }
			) {
				newItemStates[libDir] = LibItemState(status = LibItem.Status.PARTIAL)
			}
		}
		checkPhoneStatuses(_phoneRoot.value)
		newWatchRoot.sort()
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

	fun setTrackStatus(libTrack: LibTrack, status: LibItem.Status) {
		_itemStates.update { current ->
			current.toMutableMap().apply {
				this[libTrack] = LibItemState(status = status)
			}
		}
	}
	fun setTrackProgress(libTrack: LibTrack, progress: Float) {
		_itemStates.update { current ->
			current.toMutableMap().apply {
				this[libTrack] = LibItemState(
					status = LibItem.Status.SENDING,
					progress = progress
				)
				libTrack.parent?.let { updatePhoneStatuses(it, this) }
			}
		}
	}
	fun setTrackUploaded(libTrack: LibTrack) {
		_itemStates.update { current ->
			current.toMutableMap().apply {
				this[libTrack] = LibItemState(status = LibItem.Status.FULL)
				libTrack.parent?.let { updatePhoneStatuses(it, this) }
			}
		}
	}
	fun setTrackDeleted(libTrack: LibTrack) {
		if(libTrack.isDescendantOf(_watchRoot.value)) {
			val newWatchRoot = _watchRoot.value?.copy()
			var libDir = libTrack.parent
			libDir?.libTracks?.remove(libTrack)
			while(libDir != null) {
				if(libDir.libTracks.isEmpty() && libDir.libDirs.isEmpty())
					libDir.parent?.libDirs?.remove(libDir)
				libDir = libDir.parent
			}
			_watchRoot.value = newWatchRoot
		} else {
			_itemStates.update { current ->
				current.toMutableMap().apply {
					remove(libTrack)
					libTrack.parent?.let { updatePhoneStatuses(it, this) }
				}
			}
		}
	}
	private fun updatePhoneStatuses(libDir: LibDir, newItemStates: MutableMap<LibItem, LibItemState>) {
		if(libDir.libTracks.all { it in newItemStates }) {
			newItemStates[libDir] = LibItemState(status = LibItem.Status.FULL)
		} else if(libDir.libTracks.any { it in newItemStates }) {
			newItemStates[libDir] = LibItemState(status = LibItem.Status.PARTIAL)
		} else {
			newItemStates.remove(libDir)
		}
		libDir.parent?.let { updatePhoneStatuses(it, newItemStates) }
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
open class LibItem(val fullPath: String, var parent: LibDir?, rootPath: String) : Comparable<LibItem> {
	enum class Status { UNKNOWN, FULL, PARTIAL, PENDING, SENDING }
	val path: String =
		try { fullPath.removePrefix("$rootPath/").removePrefix("/") }
		catch(_: Exception) { fullPath.removePrefix("/") }
	val name: String = path.substring(path.lastIndexOf("/") + 1)
	val depth: Int = path.count { it == '/' }
	var length: Float = 1F
	private val sortParts: List<String> by lazy {
		Regex("(\\d+)|(\\D+)").findAll(name).map { it.value }.toList()
	}

	override fun compareTo(other: LibItem): Int {
		for(i in 0 until minOf(this.sortParts.size, other.sortParts.size)) {
			val p1 = this.sortParts[i]
			val p2 = other.sortParts[i]
			val result =
				if(p1[0].isDigit() && p2[0].isDigit()) p1.toInt().compareTo(p2.toInt())
				else p1.compareTo(p2, ignoreCase = true)
			if(result != 0) return result
		}
		return this.sortParts.size - other.sortParts.size
	}
}
class LibTrack(fullPath: String, parent: LibDir, rootPath: String) : LibItem(fullPath, parent, rootPath) {
	fun isDescendantOf(root: LibDir?): Boolean {
		var libDir = parent
		while(libDir != null) {
			if(libDir === root) return true
			libDir = libDir.parent
		}
		return false
	}
}
class LibDir(fullPath: String, parent: LibDir?, rootPath: String) : LibItem(fullPath, parent, rootPath) {
	val libTracks = mutableListOf<LibTrack>()
	val libDirs = mutableListOf<LibDir>()
	fun sort() {
		libTracks.sort()
		libDirs.sort()
		libDirs.forEach { it.sort() }
	}
	fun copy(): LibDir = LibDir(fullPath, null, "").apply {
		libTracks.addAll(this@LibDir.libTracks)
		libDirs.addAll(this@LibDir.libDirs)
		libTracks.forEach{ it.parent = this }
		libDirs.forEach{ it.parent = this }
	}
}
