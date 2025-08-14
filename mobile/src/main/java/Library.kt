/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.net.Uri
import android.os.Environment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.windkracht8.wearmusicplayer.Library.rootLibDir
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.File
import java.util.TreeSet

object Library {
	var rootLibDir: LibDir = LibDir("Music")
	var watchLibDir: LibDir = LibDir("")
	enum class Status { SCAN, READY }
	val status = MutableStateFlow(null as Status?)

	fun scanFiles(dir: String = "Music") {
		if (!Permissions.hasRead) return
		status.value = Status.SCAN
		rootLibDir = LibDir(Environment.getExternalStorageDirectory().toString() + "/$dir")
		scanFilesDir(rootLibDir)
		status.value = Status.READY
	}
	fun scanFilesDir(libDir: LibDir) {
		val files = File(libDir.fullPath).listFiles() ?: return
		for (file in files) {
			if (file.name.startsWith(".")) continue
			if (file.isDirectory) {
				val libDirSub = LibDir(file.absolutePath)
				scanFilesDir(libDirSub)
				if (libDirSub.libDirs.isEmpty() && libDirSub.libTracks.isEmpty()) continue
				libDir.libDirs.add(libDirSub)
			}
			if (!file.name.isTrack()) continue
			libDir.libTracks.add(LibTrack(file.absolutePath))
		}
		libDir.sort()
	}

	fun updateLibWithFilesOnWatch(filesOnWatch: JSONArray) {
		val pathsOnWatch = TreeSet<String>()
		try {
			for (i in 0..<filesOnWatch.length()) {
				pathsOnWatch.add(Uri.decode(
					filesOnWatch.getJSONObject(i).getString("path")
				))
			}
			CoroutineScope(Dispatchers.Default).launch { updateWithFilesOnWatch(pathsOnWatch) }
		} catch (e: Exception) {
			logE("Library.updateLibWithFilesOnWatch: " + e.message)
		}
	}

	suspend fun updateWithFilesOnWatch(pathsOnWatch: TreeSet<String>? = null) {
		waitForReady()
		rootLibDir.clearStatuses()
		watchLibDir = LibDir("")
		if(pathsOnWatch == null) return
		var somethingNotOnPhone = false
		pathsOnWatch.forEach { fullPath ->
			var watchDir = watchLibDir
			var phoneDir: LibDir? = rootLibDir
			val segmentCount = fullPath.count { it == '/' }
			fullPath.split("/").forEachIndexed { index, segment ->
				if(index == segmentCount) {
					val watchTrack = LibTrack(fullPath)
					watchDir.libTracks.add(watchTrack)
					val phoneTrack = phoneDir?.libTracks?.firstOrNull { it.path == watchTrack.path }
					if(phoneTrack == null) {
						watchTrack.status = LibItem.Status.FULL
						watchDir.status = LibItem.Status.NOT
						somethingNotOnPhone = true
					} else {
						watchTrack.status = LibItem.Status.NOT
						phoneTrack.status = LibItem.Status.FULL
					}
				} else {
					if(watchDir.libDirs.none { it.name == segment }) {
						watchDir.libDirs.add(LibDir(watchDir.fullPath + "/$segment"))
					}
					watchDir = watchDir.libDirs.first { it.name == segment }
					phoneDir = phoneDir?.libDirs?.firstOrNull { it.name == segment }
				}
			}
		}
		checkStatuses(rootLibDir)
		if(somethingNotOnPhone) watchLibDir.status = LibItem.Status.NOT
	}

	fun String.isTrack(): Boolean = endsWith(".mp3") || endsWith(".m4a")
	fun checkStatuses(libDir: LibDir) {
		libDir.libDirs.forEach { checkStatuses(it) }
		if (libDir.libTracks.all { it.status == LibItem.Status.FULL } &&
			libDir.libDirs.all { it.status == LibItem.Status.FULL }
		) {
			libDir.status = LibItem.Status.FULL
		} else if (libDir.libTracks.any { it.status == LibItem.Status.FULL } ||
			libDir.libDirs.any {
				it.status == LibItem.Status.FULL || it.status == LibItem.Status.PARTIAL
			}
		) {
			libDir.status = LibItem.Status.PARTIAL
		} else {
			libDir.status = LibItem.Status.NOT
		}
	}
	suspend fun waitForReady() {
		repeat (100) {
			if (status.value == Status.READY) return
			delay(100)
		}
	}
}

open class LibItem(fullPath: String) : Comparable<LibItem?> {
	enum class Status { UNKNOWN, FULL, PARTIAL, NOT, PENDING, SENDING }
	var status by mutableStateOf(Status.UNKNOWN)
	val fullPath: String = fullPath.removePrefix("/")
	val path: String = try { this.fullPath.removePrefix(rootLibDir.fullPath + "/") }
		catch (_: Exception) { this.fullPath } //above fails for rootLibDir itself
	val name: String = path.substring(path.lastIndexOf("/") + 1)
	val depth: Int = path.count { it == '/' }
	var length: Long = 1
	var progress by mutableIntStateOf(-1)
	//init { logD("libItem name:$name depth:$depth path:$path fullPath:${this.fullPath}") }
	fun setStatusFull() {
		status = Status.FULL
		Library.checkStatuses(rootLibDir)
	}
	fun setStatusNot() {
		status = Status.NOT
		Library.checkStatuses(rootLibDir)
	}
	override fun compareTo(other: LibItem?): Int = name.compareTo(other?.name ?: "")
}

class LibTrack(fullPath: String) : LibItem(fullPath)
class LibDir(fullPath: String) : LibItem(fullPath) {
	val libTracks: ArrayList<LibTrack> = ArrayList()
	val libDirs: ArrayList<LibDir> = ArrayList<LibDir>()
	fun sort() {
		libTracks.sort()
		libDirs.sort()
	}
	fun clearStatuses() {
		libTracks.forEach { it.status = Status.UNKNOWN }
		libDirs.forEach {
			it.clearStatuses()
			it.status = Status.UNKNOWN
		}
	}
}
