/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.content.Context
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

const val LOG_TAG = "WearMusicPlayer"
fun logE(message: String) = Log.e(LOG_TAG, message)
fun logD(message: () -> String) { if(BuildConfig.DEBUG) { Log.d(LOG_TAG, message()) } }

fun Context.toast(message: Int) =
	Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

const val GB: Long = 1073741824
const val MB: Long = 1048576
const val KB: Long = 1024
fun bytesToHuman(bytes: Long): String {
	if(bytes > GB * 10) {
		val gbs = bytes.toDouble() / GB
		return String.format(Locale.ROOT, "%.0f GB", gbs)
	} else if(bytes > GB * 5) {
		val gbs = bytes.toDouble() / GB
		return String.format(Locale.ROOT, "%.3f GB", gbs)
	} else if(bytes > MB) {
		val mbs = bytes.toDouble() / MB
		return String.format(Locale.ROOT, "%.0f MB", mbs)
	} else if(bytes > KB) {
		val kbs = bytes.toDouble() / KB
		return String.format(Locale.ROOT, "%.0f KB", kbs)
	}
	return "$bytes B"
}

fun tryIgnore(block: () -> Unit) = try { block() } catch(_: Exception) {}
fun runInBackground(block: suspend () -> Unit) =
	CoroutineScope(Dispatchers.Default).launch { block() }
