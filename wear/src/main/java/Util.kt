/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

const val LOG_TAG = "WearMusicPlayer"
fun logE(message: String) = Log.e(LOG_TAG, message)
fun logI(message: () -> String) { if(BuildConfig.DEBUG) { Log.i(LOG_TAG, message()) } }
fun logD(message: () -> String) { if(BuildConfig.DEBUG) { Log.d(LOG_TAG, message()) } }

fun Context.toast(message: Int) =
	Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
fun Context.toastFromBG(message: Int) = CoroutineScope(Dispatchers.Main).launch { toast(message) }

fun Context.hasPermission(permission: String): Boolean =
	ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

fun String.isPathUnsave(): Boolean =
	contains("..") || contains("/./") || contains("//") ||
	contains("\\u0000") ||
	startsWith("/") || endsWith("/") ||
	startsWith(".") || endsWith(".") ||
	startsWith(" ") || endsWith(" ")

fun tryIgnore(block: () -> Unit) = try { block() } catch (_: Exception) {}
fun runInBackground(block: suspend () -> Unit) =
	CoroutineScope(Dispatchers.Default).launch { block() }
