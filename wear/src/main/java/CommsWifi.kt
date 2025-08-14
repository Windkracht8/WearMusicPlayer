/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.app.Activity
import android.content.Context.CONNECTIVITY_SERVICE
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.io.FileOutputStream
import java.net.Socket

object CommsWifi {
	var connectivityManager: ConnectivityManager? = null
	var path: String = ""
	enum class Status { DONE, PREPARING, RECEIVING, ERROR }
	val status = MutableSharedFlow<Status>()
	enum class ConnectionType { REQUESTING, FAST, SLOW }
	var connectionType by mutableStateOf(null as ConnectionType?)
	var progress by mutableFloatStateOf(0F)
	var error = 0

	fun init(activity: Activity) {
		connectivityManager = activity.getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager?
	}

	fun receiveFile(
		path: String,
		length: Long,
		ip: String,
		port: Int
	) {
		logD("CommsWifi path: $path length: $length ip: $ip port: $port")
		if(connectivityManager == null){
			error = R.string.fail_wifi
			CoroutineScope(Dispatchers.Default).launch { status.emit(Status.ERROR) }
			return
		}
		val connectivityManager = connectivityManager ?: return
		this.path = path
		CoroutineScope(Dispatchers.Default).launch { status.emit(Status.PREPARING) }
		connectionType = ConnectionType.REQUESTING
		progress = 0F

		try {
			connectivityManager.requestNetwork(
				NetworkRequest.Builder()
					.addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
					.build(),
				object : ConnectivityManager.NetworkCallback() {
					override fun onAvailable(network: Network) {
						super.onAvailable(network)
						logD("CommsWifi.NetworkCallback.onAvailable")
						connectivityManager.bindProcessToNetwork(network)
						CoroutineScope(Dispatchers.Default).launch {
							connectionType = ConnectionType.FAST
							readFileFromStream(connectivityManager, path, length, ip, port)
						}
					}
					override fun onUnavailable() {
						super.onUnavailable()
						logD("CommsWifi.NetworkCallback.onUnavailable")
						CoroutineScope(Dispatchers.Default).launch {
							connectionType = ConnectionType.SLOW
							readFileFromStream(connectivityManager, path, length, ip, port)
						}
					}
				},
				5000
			)
		} catch (e: Exception) {
			logE("CommsWifi " + e.message)
			CoroutineScope(Dispatchers.Default).launch {
				connectionType = ConnectionType.SLOW
				readFileFromStream(connectivityManager, path, length, ip, port)
			}
		}
	}

	suspend fun readFileFromStream(
		connectivityManager: ConnectivityManager,
		path: String,
		length: Long,
		ip: String,
		port: Int
	) {
		logD("CommsWifi.readFileFromStream")
		CoroutineScope(Dispatchers.Default).launch { status.emit(Status.RECEIVING) }
		var bytesDone: Long = 0
		try {
			Socket(ip, port).use { socket ->
				socket.getInputStream().use { inputStream ->
					FileOutputStream(Library.musicDir + path).use { fileOutputStream ->
						var lastReadTime = System.currentTimeMillis()
						while (System.currentTimeMillis() - lastReadTime < 3000) {
							if (inputStream.available() == 0) {
								delay(100)
								continue
							}
							val buffer = ByteArray(2048)
							val numBytes = inputStream.read(buffer)
							if (numBytes < 0) {
								logE("CommsWifi.receiveFile read error")
								error = R.string.fail_read_wifi
								CoroutineScope(Dispatchers.Default).launch { status.emit(Status.ERROR) }
								connectivityManager.bindProcessToNetwork(null)
								return
							}
							fileOutputStream.write(buffer, 0, numBytes)
							bytesDone += numBytes.toLong()
							progress = bytesDone.toFloat() / length.toFloat()
							//logD("CommsWifi.read bytesDone: $bytesDone length: $length progress: $progress")
							if (bytesDone >= length) {
								CoroutineScope(Dispatchers.Default).launch { status.emit(Status.DONE) }
								connectivityManager.bindProcessToNetwork(null)
								return
							}
							lastReadTime = System.currentTimeMillis()
						}
						error = R.string.fail_read_wifi
						CoroutineScope(Dispatchers.Default).launch { status.emit(Status.ERROR) }
					}
				}
			}
		} catch (e: Exception) {
			logE("CommsWifi.receiveFile exception: $e")
			logE("CommsWifi.receiveFile exception: " + e.message)
			error = R.string.fail_wifi
			CoroutineScope(Dispatchers.Default).launch { status.emit(Status.ERROR) }
		}
		//if we get here it failed
		connectivityManager.bindProcessToNetwork(null)
		error = R.string.fail_read_wifi
		CoroutineScope(Dispatchers.Default).launch { status.emit(Status.ERROR) }
	}
}
