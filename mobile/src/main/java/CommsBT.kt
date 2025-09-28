/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

@SuppressLint("MissingPermission")//handled by Permissions
object CommsBT {
	val WMP_UUID: UUID = UUID.fromString("6f34da3f-188a-4c8c-989c-2baacf8ea6e1")
	const val CODE_PENDING = 1
	const val CODE_OK = 0
	//const val CODE_FAIL = -1
	//const val CODE_UNKNOWN_REQUEST_TYPE = -2 TODO give more feedback to user
	//const val CODE_FAIL_CREATE_DIRECTORY = -3
	//const val CODE_FAIL_CREATE_FILE = -4
	//const val CODE_FILE_EXISTS = -5
	//const val CODE_FAIL_DEL_FILE = -6
	const val CODE_DECLINED = -6
	var sharedPreferences: SharedPreferences? = null
	var bluetoothAdapter: BluetoothAdapter? = null
	var bluetoothSocket: BluetoothSocket? = null
	var commsBTConnect: CommsBTConnect? = null
	var commsBTConnected: CommsBTConnected? = null
	val knownDevices: MutableSet<BluetoothDevice> = mutableSetOf()
	var knownAddresses: MutableSet<String> = mutableSetOf()

	enum class Status { STARTING, DISCONNECTED, CONNECTING, CONNECTED, ERROR }

	val status = MutableStateFlow(null as Status?)
	var error by mutableIntStateOf(-1)
	var freeSpace by mutableStateOf("")
	var messageStatus by mutableIntStateOf(-1)
	val messageError = MutableSharedFlow<Int>()
	var deviceName = ""
	var disconnect = false
	val requestQueue: MutableSet<Request> = mutableSetOf()
	var lastRequest: Request? = null
	fun start(context: Context) {
		if(!Permissions.hasBT) return onError(R.string.fail_BT_denied)
		status.value = Status.STARTING
		val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
		bluetoothAdapter = bm.adapter
		if(bluetoothAdapter?.state != BluetoothAdapter.STATE_ON) return onError(R.string.fail_BT_off)

		sharedPreferences = context.getSharedPreferences("CommsBT", MODE_PRIVATE)
		knownAddresses = sharedPreferences?.getStringSet("knownAddresses", null) ?: mutableSetOf()
		logD("CommsBT.start " + knownAddresses.size + " known addresses")
		val bondedBTDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
		//Find and clean known devices
		knownAddresses.forEach { checkKnownAddress(it, bondedBTDevices) }
		//Try to connect to known device
		if(knownDevices.isNotEmpty()) {
			connectDevice(knownDevices.first())
			return //having multiple watches is rare, user will have to select from DeviceSelect
		}
		status.value = Status.DISCONNECTED
	}

	fun checkKnownAddress(knownAddress: String, bondedDevices: Set<BluetoothDevice>) {
		for(device in bondedDevices) {
			if(device.address == knownAddress) {
				knownDevices.add(device)
				return
			}
		}
		delKnownAddress(knownAddress)
	}

	fun delKnownAddress(address: String) {
		if(knownAddresses.remove(address)) storeKnownAddresses()
	}

	fun storeKnownAddresses() {
		sharedPreferences?.edit {
			if(knownAddresses.isEmpty()) {
				remove("knownAddresses")
			} else {
				putStringSet("knownAddresses", knownAddresses)
			}
		}
	}

	fun stop() {
		disconnect = true
		requestQueue.clear()
		lastRequest = null
		status.value = Status.DISCONNECTED
		freeSpace = ""
		messageStatus = -1
		tryIgnore { bluetoothSocket?.close() }
		bluetoothSocket = null
		commsBTConnect = null
		commsBTConnected = null
	}

	fun getBondedDevices(): Set<BluetoothDevice>? {
		if(bluetoothAdapter == null) {
			onError(R.string.fail_BT_denied)
			return null
		}
		if(bluetoothAdapter?.state != BluetoothAdapter.STATE_ON) {
			onError(R.string.fail_BT_off)
			return null
		}
		return bluetoothAdapter?.bondedDevices
	}

	fun connectDevice(device: BluetoothDevice) {
		logD("CommsBT.connectDevice: " + device.name)
		if(status.value in listOf(Status.CONNECTED, Status.CONNECTING) ||
			bluetoothAdapter?.isEnabled != true
		) return
		disconnect = false
		deviceName = device.name ?: "<no name>"
		status.value = Status.CONNECTING
		commsBTConnect = CommsBTConnect(device)
		commsBTConnect?.start()
	}

	fun sendRequestSync() = requestQueue.add(Request(Request.Type.SYNC))
	fun sendRequestSendFile(libItem: LibItem) {
		logD("CommsBT.sendRequestSendFile " + libItem.name)
		libItem.status = LibItem.Status.PENDING
		requestQueue.add(
			Request(
				type = Request.Type.SEND_FILE,
				libItem = libItem
			)
		)
	}

	fun sendRequestDeleteFile(libItem: LibItem) {
		logD("CommsBT.sendRequestDeleteFile " + libItem.name)
		libItem.status = LibItem.Status.PENDING
		requestQueue.add(
			Request(
				type = Request.Type.DELETE_FILE,
				libItem = libItem
			)
		)
	}

	fun gotResponse(response: JSONObject) {
		try {
			val requestType = response.getString("requestType")
			val responseData = response.get("responseData")
			when(requestType) {
				"sync" -> {
					//{"requestType":"sync","responseData":{"tracks":[{"path":"directory\/track1.mp3"},{"path":"track2.mp3"}],"freeSpace":5672968192}}
					//CODE_FAIL
					//CODE_UNKNOWN_REQUEST_TYPE
					if(responseData is JSONObject) {
						freeSpace = bytesToHuman(responseData.getLong("freeSpace"))
						runInBackground {
							Library.updateLibWithFilesOnWatch(
								responseData.getJSONArray("tracks")
							)
							messageStatus = R.string.sync_done
						}
					} else {
						logE("CommsBT.gotResponse sync responseData: $responseData")
						messageStatus = R.string.fail_response
					}
					lastRequest = null
				}
				"fileDetails" -> {
					//{"requestType":"fileDetails","responseData":1}
					//CODE_PENDING
					//CODE_FAIL
					//CODE_UNKNOWN_REQUEST_TYPE
					//CODE_FAIL_CREATE_DIRECTORY
					//CODE_FAIL_CREATE_FILE
					//CODE_FILE_EXISTS
					if(responseData is Int && responseData < 1) {
						CommsWifi.stop()
						logE("CommsBT.gotResponse fileDetails responseData: $responseData")
						messageStatus = R.string.fail_send_file
					}
					lastRequest = null
				}
				"fileBinary" -> {
					//{"requestType":"fileBinary","responseData":{"path":"directory\/track.mp3","freeSpace":12345}}
					//CODE_FAIL
					//CODE_UNKNOWN_REQUEST_TYPE
					if(responseData is JSONObject) {
						freeSpace = bytesToHuman(responseData.getLong("freeSpace"))
						lastRequest?.libItem?.setStatusFull()
						logD("CommsBT.gotResponse fileBinary lastRequest: " + lastRequest?.libItem?.path)
						messageStatus = R.string.file_sent
					} else {
						logE("CommsBT.gotResponse fileBinary responseData: $responseData")
						messageStatus = R.string.fail_send_file
					}
					lastRequest = null
				}
				"deleteFile" -> {
					//{"requestType":"deleteFile","responseData":0}
					//CODE_PENDING
					//CODE_OK
					//CODE_FAIL
					//CODE_UNKNOWN_REQUEST_TYPE
					//CODE_FAIL_DEL_FILE
					//CODE_DECLINED
					when(responseData) {
						CODE_PENDING -> messageStatus = R.string.delete_file_confirm
						CODE_OK -> {
							lastRequest?.libItem?.setStatusNot()
							messageStatus = R.string.file_deleted
							lastRequest = null
						}
						CODE_DECLINED -> {
							lastRequest?.libItem?.setStatusFull()
							messageStatus = R.string.delete_file_declined
							lastRequest = null
						}
						else -> {
							logE("CommsBT.gotResponse deleteFile")
							messageStatus = R.string.fail_delete_file
							lastRequest = null
						}
					}
				}
				else -> throw Exception()
			}
		} catch(e: Exception) {
			logE("CommsBT.gotResponse: " + e.message)
			onMessageError(R.string.fail_response)
			lastRequest = null
		}
	}

	class CommsBTConnect(device: BluetoothDevice) : Thread() {
		init {
			logD("CommsBTConnect " + device.name)
			try {
				bluetoothSocket = device.createRfcommSocketToServiceRecord(WMP_UUID)
			} catch(e: Exception) {
				logE("CommsBTConnect Exception: " + e.message)
				status.value = Status.DISCONNECTED
			}
		}

		override fun run() {
			try {
				bluetoothSocket?.connect()
				commsBTConnected = CommsBTConnected()
				commsBTConnected?.start()
			} catch(e: Exception) {
				logD("CommsBTConnect.run failed: " + e.message)
				tryIgnore { bluetoothSocket?.close() }
				status.value = Status.DISCONNECTED
			}
		}
	}

	class CommsBTConnected : Thread() {
		var inputStream: InputStream? = null
		var outputStream: OutputStream? = null

		init {
			logD("CommsBTConnected")
			try {
				inputStream = bluetoothSocket!!.inputStream
				outputStream = bluetoothSocket!!.outputStream
				status.value = Status.CONNECTED
				knownDevices.add(bluetoothSocket!!.remoteDevice)
				if(knownAddresses.add(bluetoothSocket!!.remoteDevice.address)) {
					storeKnownAddresses()
				}
			} catch(e: Exception) {
				logE("CommsBTConnected init Exception: " + e.message)
				status.value = Status.DISCONNECTED
			}
		}

		override fun run() {
			sendRequestSync()
			runBlocking { process() }
		}

		fun close() {
			logD("CommsBTConnected.close")
			status.value = Status.DISCONNECTED
			freeSpace = ""
			messageStatus = -1
			tryIgnore {
				requestQueue.clear()
				bluetoothSocket?.close()
			}
			bluetoothSocket = null
			commsBTConnected = null
			commsBTConnect = null
		}

		suspend fun process() {
			while(!disconnect) {
				try {
					outputStream!!.write("".toByteArray())
				} catch(_: Exception) {
					logD("Connection closed")
					break
				}
				sendNextRequest()
				read()
				delay(100)
			}
			close()
		}

		fun sendNextRequest() {
			try {
				outputStream!!.write("".toByteArray())
				if(requestQueue.isEmpty() || CommsWifi.isSending || lastRequest != null) return
				lastRequest = requestQueue.first()
				requestQueue.remove(lastRequest)
				logD("CommsBTConnected.sendNextRequest: $lastRequest")
				if(lastRequest!!.type == Request.Type.SEND_FILE) {
					val libItem = lastRequest!!.libItem!!
					runInBackground { CommsWifi.sendFile(libItem) }
				}
				outputStream!!.write(lastRequest.toString().toByteArray())
			} catch(e: Exception) {
				logE("CommsBTConnected.sendNextRequest Exception: " + e.message)
				onMessageError(R.string.fail_send_message)
				disconnect = true
			}
		}

		suspend fun read() {
			try {
				if(inputStream!!.available() < 5) return
				var lastReadTime = System.currentTimeMillis()
				var response = ""
				while(System.currentTimeMillis() - lastReadTime < 3000) {
					if(inputStream!!.available() == 0) {
						delay(100)
						continue
					}
					val buffer = ByteArray(inputStream!!.available())
					val numBytes = inputStream!!.read(buffer)
					if(numBytes < 0) {
						logE("CommsBTConnected.read read error, response: $response")
						lastRequest = null
						return onMessageError(R.string.fail_response)
					} else if(numBytes > 0) {
						lastReadTime = System.currentTimeMillis()
					}
					val temp = String(buffer)
					response += temp
					if(isValidJSON(response)) {
						logD("CommsBTConnected.read got message: $response")
						gotResponse(JSONObject(response))
						return
					}
				}
				logE("CommsBTConnected.read no valid message and no new data after 3 sec: $response")
				lastRequest = null
			} catch(e: Exception) {
				logE("CommsBTConnected.read Exception: " + e.message)
				lastRequest = null
			}
			onMessageError(R.string.fail_response)
		}

		fun isValidJSON(json: String): Boolean {
			if(!json.endsWith("}")) return false
			try { JSONObject(json) }
			catch (_: JSONException) { return false }
			return true
		}
	}

	class Request(val type: Type, val libItem: LibItem? = null) {
		enum class Type { SYNC, SEND_FILE, DELETE_FILE }
		override fun toString(): String {
			val request = JSONObject()
			request.put("version", 1)
			when(type) {
				Type.SYNC -> {
					//{"requestType":"sync","requestData":{}}
					request.put("requestType", "sync")
					request.put("requestData", JSONObject())
					messageStatus = R.string.sync
				}
				Type.SEND_FILE -> {
					//{"requestType":"fileDetails","requestData":{"path":"Music/track.mp3","length":12345,"ip":"192.168.1.100","port":9002}}
					request.put("requestType", "fileDetails")
					if(CommsWifi.ipAddress == null) {
						onMessageError(R.string.fail_no_wifi)
						return ""
					}
					val libItem = libItem
					if(libItem == null) {
						onMessageError(R.string.fail_send_message)
						return ""
					}
					val file = File(libItem.fullPath)
					libItem.length = file.length()
					request.put("requestData",
						JSONObject()
							.put("path", libItem.path)
							.put("length", libItem.length)
							.put("ip", CommsWifi.ipAddress)
							.put("port", CommsWifi.PORT_NUMBER)
					)
					messageStatus = R.string.send_file
				}
				Type.DELETE_FILE -> {
					//{"requestType":"deleteFile","requestData":"track.mp3"}
					request.put("requestType", "deleteFile")
					val path = libItem?.path
					if(path == null) {
						onMessageError(R.string.fail_send_message)
						return ""
					}
					request.put("requestData", path)
					messageStatus = R.string.delete_file
				}
			}
			return request.toString()
		}
	}

	fun onError(message: Int) {
		error = message
		status.value = Status.ERROR
	}

	fun onMessageError(message: Int) {
		messageStatus = message
		runInBackground { messageError.emit(message) }
		lastRequest?.libItem?.status = LibItem.Status.UNKNOWN
	}
}
