/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONException
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

val WMP_UUID: UUID = UUID.fromString("6f34da3f-188a-4c8c-989c-2baacf8ea6e1")

object CommsBT {
	const val CODE_PENDING = 1
	const val CODE_OK = 0
	const val CODE_FAIL = -1
	const val CODE_UNKNOWN_REQUEST_TYPE = -2
	const val CODE_FAIL_CREATE_DIRECTORY = -3
	const val CODE_FAIL_CREATE_FILE = -4
	const val CODE_FILE_EXISTS = -5
	const val CODE_FAIL_DEL_FILE = -6
	const val CODE_DECLINED = -6

	var bluetoothAdapter: BluetoothAdapter? = null
	var bluetoothServerSocket: BluetoothServerSocket? = null
	var bluetoothSocket: BluetoothSocket? = null
	var commsBTConnect: CommsBTConnect? = null
	var commsBTConnected: CommsBTConnected? = null
	val error = MutableSharedFlow<Int>()

	var disconnect = false
	val responseQueue: MutableSet<JSONObject> = mutableSetOf()
	val deleteFilePath = MutableStateFlow("")

	fun gotRequest(request: String) {
		logD("CommsBT.gotRequest: $request")
		var requestType = "unknown"
		try {
			val requestMessage = JSONObject(request)
			requestType = requestMessage.getString("requestType")
			when (requestType) {
				"sync" -> {
					//{"requestType":"sync","requestData":{}}
					sendSyncResponse()
					//{"requestType":"sync","responseData":{"tracks":[{"path":"directory\/track1.mp3"},{"path":"track2.mp3"}],"freeSpace":5672968192}}
				}
				"fileDetails" -> {
					//{"requestType":"fileDetails","requestData":{"path":"directory\/track3.mp3","length":12345,"ip":"192.168.1.100","port":9002}}
					val requestData = requestMessage.getJSONObject("requestData")
					val path = requestData.getString("path")
					if(path.isPathUnsave()){
						logE("CommsBT.gotRequest fileDetails isPathUnsave() says no")
						onError(R.string.fail_request)
						sendResponse("fileDetails", CODE_FAIL)
						return
					}
					val reason: Int = Library.ensurePath(path)
					if (reason < 0) {
						logE("CommsBT.gotRequest fileDetails result: $reason")
						if(reason == CODE_FILE_EXISTS) onError(R.string.fail_file_exists)
						else onError(R.string.fail_create_file)
						sendResponse("fileDetails", reason)
						//{"requestType":"fileDetails","responseData":-1}
						return
					}
					runInBackground {
						CommsWifi.receiveFile(
							path,
							requestData.getLong("length"),
							requestData.getString("ip"),
							requestData.getInt("port")
						)
					}
					//after CommsWifi.receiveFile
					//{"requestType":"fileBinary","responseData":{"path":"directory\/track3.mp3","freeSpace":12345}}
				}
				"deleteFile" -> {
					//{"requestType":"deleteFile","requestData":"directory\/track1.mp3"}
					val path = requestMessage.getString("requestData")
					if(path.isPathUnsave()){
						logE("CommsBT.gotRequest deleteFile isPathUnsave() says no")
						onError(R.string.fail_request)
						sendResponse("deleteFile", CODE_FAIL)
						return
					}
					deleteFilePath.value = path
					//{"requestType":"deleteFile","responseData":0}
				}
				else -> {
					logE("CommsBT.gotRequest Unknown requestType: $requestType")
					sendResponse(requestType, CODE_UNKNOWN_REQUEST_TYPE)
				}
			}
		} catch (e: Exception) {
			logE("CommsBT.gotRequest Exception: " + e.message)
			onError(R.string.fail_request)
			sendResponse(requestType, CODE_FAIL)
		}
	}
	fun sendSyncResponse() {
		sendResponse("sync", JSONObject()
			.put("tracks", Library.getTracksJson())
			.put("freeSpace", Library.getFreeSpace())
		)
	}
	fun sendFileBinaryResponse() {
		sendResponse("fileBinary", JSONObject()
			.put("path", CommsWifi.path)
			.put("freeSpace", Library.getFreeSpace())
		)
		logD("CommsBT.sendFileBinaryResponse " + responseQueue.lastOrNull())
	}
	fun sendResponse(requestType: String, responseData: Any) {
		responseQueue.add(JSONObject()
			.put("version", 1)
			.put("requestType", requestType)
			.put("responseData", responseData)
		)
		logD("CommsBT.sendResponse " + responseQueue.lastOrNull())
	}

	fun start(activity: Activity) {
		//logD("CommsBT.start")
		CommsWifi.init(activity)
		disconnect = false
		if (bluetoothAdapter == null) {
			val bluetoothManager = activity.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
			bluetoothAdapter = bluetoothManager.adapter
			if (bluetoothAdapter?.isEnabled != true) {
				logD("CommsBT.start bluetooth disabled")
				return
			}
		}
		if (commsBTConnect == null) {
			logD("CommsBT.start start listening")
			commsBTConnect = CommsBTConnect()
			commsBTConnect?.start()
		}
	}
	fun restart(){
		disconnect = true
		tryIgnore { bluetoothServerSocket?.close() }
		tryIgnore { bluetoothSocket?.close() }
		bluetoothServerSocket = null
		bluetoothSocket = null
		commsBTConnect = null
		commsBTConnected = null
		disconnect = false
		commsBTConnect = CommsBTConnect()
		commsBTConnect?.start()
	}
	fun stop() {
		//logD("CommsBT.stop")
		disconnect = true
		tryIgnore { bluetoothServerSocket?.close() }
		tryIgnore { bluetoothSocket?.close() }
		bluetoothServerSocket = null
		bluetoothSocket = null
		bluetoothAdapter = null
		commsBTConnect = null
		commsBTConnected = null
	}

	class CommsBTConnect @SuppressLint("MissingPermission") //Permissions are handled in initBT
	constructor() : Thread() {
		init {
			try {
				//logD("CommsBTConnect")
				bluetoothServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
					"WearMusicPlayer",
					WMP_UUID
				)
			} catch (e: Exception) {
				if (!disconnect) logE("CommsBTConnect Exception: " + e.message)
			}
		}

		override fun run() {
			try {
				bluetoothSocket = bluetoothServerSocket?.accept()
				if (!disconnect) {
					logD("CommsBTConnect.run accepted")
					commsBTConnected = CommsBTConnected()
					commsBTConnected?.start()
				}
			} catch (e: Exception) {
				if (!disconnect) logE("CommsBTConnect.run Exception: " + e.message)
			}
		}
	}

	class CommsBTConnected : Thread() {
		var inputStream: InputStream? = null
		var outputStream: OutputStream? = null

		init {
			try {
				inputStream = bluetoothSocket!!.inputStream
				outputStream = bluetoothSocket!!.outputStream
			} catch (e: Exception) {
				logE("CommsBTConnected init Exception: " + e.message)
			}
		}

		override fun run() {
			logD("CommsBTConnected.run")
			runInBackground { process() }
		}

		suspend fun process() {
			while (!disconnect) {
				read()
				try {
					outputStream!!.write("".toByteArray())
				} catch (_: Exception) {
					logD("Connection closed")
					restart()
					return
				}
				if (responseQueue.isNotEmpty() && !sendNextResponse()) {
					restart()
					return
				}
				delay(100)
			}
		}

		fun sendNextResponse(): Boolean {
			try {
				val response = responseQueue.first()
				responseQueue.remove(response)
				logD("CommsBTConnected.sendNextResponse: $response")
				outputStream?.write(response.toString().toByteArray())
			} catch (e: Exception) {
				logE("CommsBTConnected.sendNextResponse Exception: " + e.message)
				onError(R.string.fail_respond)
				return false
			}
			return true
		}

		suspend fun read() {
			try {
				if ((inputStream?.available() ?: 0) < 5) return
				var lastReadTime = System.currentTimeMillis()
				var request = ""
				while (System.currentTimeMillis() - lastReadTime < 3000) {
					if ((inputStream?.available() ?: 0) == 0) {
						delay(100)
						continue
					}
					val buffer = ByteArray(inputStream?.available() ?: 0)
					val numBytes = inputStream?.read(buffer) ?: 0
					if (numBytes < 0) {
						logE("CommsBTConnected.read read error, request: $request")
						onError(R.string.fail_request)
						return
					}
					val temp = String(buffer)
					request += temp
					if (isValidJSON(request)) {
						gotRequest(request)
						return
					}
					lastReadTime = System.currentTimeMillis()
				}
				logE("CommsBTConnected.read no valid message and no new data after 3 sec: $request")
			} catch (e: Exception) {
				logE("CommsBTConnected.read Exception: " + e.message)
			}
			onError(R.string.fail_request)
		}

		fun isValidJSON(json: String): Boolean {
			if (!json.endsWith("}")) return false
			try { JSONObject(json) }
			catch (_: JSONException) { return false }
			return true
		}
	}
	fun onError(message: Int) = runInBackground { error.emit(message) }
}
