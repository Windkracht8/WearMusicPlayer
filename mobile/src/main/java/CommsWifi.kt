/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.content.Context
import android.net.ConnectivityManager
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.ServerSocket

object CommsWifi {
	const val PORT_NUMBER: Int = 9002
	var ipAddress: String? = null
	var serverSocket: ServerSocket? = null
	var isSending = false
	fun sendFile(libItem: LibItem) {
		if(ipAddress == null) return CommsBT.onMessageError(R.string.fail_no_wifi)
		logD("CommsWifi.sendFile " + libItem.name)
		isSending = true
		libItem.status = LibItem.Status.SENDING
		try {
			ServerSocket(PORT_NUMBER).use { serverSocket ->
				this.serverSocket = serverSocket
				serverSocket.accept().use { socket ->
					FileInputStream(libItem.fullPath).use { fileInputStream ->
						var bytesDone: Long = 0
						socket.outputStream.use { outputStream ->
							while(fileInputStream.available() > 0) {
								val buffer = ByteArray(2048)
								val numBytes: Int = fileInputStream.read(buffer)
								if(numBytes < 0) {
									logE("CommsWifi.sendFile read error")
									return CommsBT.onMessageError(R.string.fail_send_file)
								}
								outputStream.write(buffer, 0, numBytes)
								bytesDone += numBytes.toLong()
								libItem.progress = (bytesDone * 100 / libItem.length).toInt()
							}
						}
					}
				}
			}
		} catch(e: Exception) {
			logE("CommsWifi.sendFile exception: $e")
			logE("CommsWifi.sendFile exception: " + e.message)
			CommsBT.onMessageError(R.string.fail_send_file)
			return
		} finally {
			serverSocket = null
			isSending = false
		}
	}

	fun stop() = tryIgnore { serverSocket?.close() }
	fun getIpAddress(context: Context): Boolean {
		val connectivityManager =
			context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
		connectivityManager.activeNetwork?.let { network ->
			connectivityManager.getLinkProperties(network)?.let { linkProperties ->
				for(linkAddress in linkProperties.linkAddresses) {
					if(linkAddress.address is Inet4Address) {
						ipAddress = linkAddress.address.hostAddress ?: continue
						return true
					}
				}
			}
		}
		ipAddress = null
		return false
	}
}
