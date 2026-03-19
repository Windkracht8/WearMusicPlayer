/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
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
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import com.windkracht8.wearmusicplayer.data.LibItem
import com.windkracht8.wearmusicplayer.data.Library
import com.windkracht8.wearmusicplayer.data.Playlists
import com.windkracht8.wearmusicplayer.ui.PermissionsUtil.hasBTPermission
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64

object CommsBT {
	val WMP_UUID: UUID =
		if(BuildConfig.DEBUG) UUID.fromString("6f34da3f-188a-4c8c-989c-2baacf8ea6ed")
		else UUID.fromString("6f34da3f-188a-4c8c-989c-2baacf8ea6e1")
	var COMMS_VERSION = 2
	private const val CODE_PENDING = 1
	private const val CODE_OK = 0
	//const val CODE_FAIL = -1
	private const val CODE_UNKNOWN_REQUEST_TYPE = -2
	//const val CODE_FAIL_CREATE_DIRECTORY = -3
	//const val CODE_FAIL_CREATE_FILE = -4
	private const val CODE_FILE_EXISTS = -5
	//const val CODE_FAIL_DEL_FILE = -6
	private const val CODE_DECLINED = -6
	private var sharedPreferences: SharedPreferences? = null
	private var bluetoothAdapter: BluetoothAdapter? = null
	var bluetoothSocket: BluetoothSocket? = null
	var commsBTConnect: CommsBTConnect? = null
	var commsBTConnected: CommsBTConnected? = null
	data class Device(val name: String, val address: String, val device: BluetoothDevice?)
	@SuppressLint("MissingPermission")
	fun Device(device: BluetoothDevice): Device = Device(device.name, device.address, device)
	val knownDevices: MutableSet<Device> = mutableSetOf()
	var knownAddresses: MutableSet<String> = mutableSetOf()
	var knownDeviceKeys: MutableMap<String, String> = mutableMapOf()

	enum class Status { STARTING, DISCONNECTED, CONNECTING, PAIRING, CONNECTED, ERROR }
	val isConnected: Boolean
		get() = this.status.value == Status.CONNECTED

	val status = MutableStateFlow<Status?>(null)
	val error = MutableStateFlow(-1)
	val freeSpace = MutableStateFlow("")
	val messageStatus = MutableStateFlow(-1)
	var deviceName = ""
	var pairCode = ""
	var disconnect = false
	val itemExistsAskToDelete = MutableStateFlow<LibItem?>(null)

	@SuppressLint("MissingPermission")
	fun start(context: Context) {
		logD{"CommsBT.start()"}
		if(!context.hasBTPermission()) return onError(R.string.fail_BT_denied)
		status.value = Status.STARTING
		val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
		bluetoothAdapter = bm.adapter
		if(bluetoothAdapter?.state != BluetoothAdapter.STATE_ON) return onError(R.string.fail_BT_off)

		sharedPreferences = context.getSharedPreferences("CommsBT", MODE_PRIVATE)

		try {
			sharedPreferences?.getString("knownDeviceKeys", null)?.let { jsonString ->
				val jsonObject = JSONObject(jsonString)
				jsonObject.keys().forEach { knownDeviceKeys[it] = jsonObject.getString(it) }
			}
		} catch(e: Exception) { logE("CommsBT.start: ${e.message}") }
		try {
			val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
			keyStore.aliases().asSequence().forEach { address ->
				bluetoothAdapter?.bondedDevices?.find { it.address == address }
					?.let { knownDevices.add(Device(it)) }
			}
		} catch(e: Exception) { logE("CommsBT.start: ${e.message}") }
		//Try to connect to first known device, having multiple watches is rare, user will have to select from DeviceSelect
		if(knownDevices.isNotEmpty()) {//mar 2026, if a deviceKey is stored, use that 
			connectDevice(context, knownDevices.first())
			return
		}

		//mar 2026 because the watch app might not be updated yet, use knownAddresses as a fallback
		knownAddresses = sharedPreferences?.getStringSet("knownAddresses", null) ?: mutableSetOf()
		logD{"CommsBT.start ${knownAddresses.size} known addresses"}
		val bondedBTDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
		knownAddresses.forEach { checkKnownAddress(it, bondedBTDevices) }
		if(knownDevices.isNotEmpty()) connectDevice(context, knownDevices.first())
		else status.value = Status.DISCONNECTED
	}

	fun addKnownDevice(publicKeyString: String, device: Device) {
		knownDeviceKeys[device.address] = publicKeyString
		storeKnownDevices()
		knownDevices.add(device)
	}
	fun delKnownDevice(address: String) {
		knownDeviceKeys.remove(address)
		storeKnownDevices()
		knownDevices.removeIf { it.address == address }
		try {
			KeyStore.getInstance("AndroidKeyStore").apply {
				load(null)
				if(containsAlias(address)) deleteEntry(address)
			}
		} catch(e: Exception) {
			logE("CommsBT.delKnownDevice: ${e.message}")
		}
	}
	private fun storeKnownDevices() {
		try {
			sharedPreferences?.edit()?.apply {
				putString("knownDeviceKeys", JSONObject(knownDeviceKeys as Map<*, *>).toString())
				apply()
			}
		} catch(e: JSONException) {
			logE("CommsBT.storeKnownDevices: ${e.message}")
			onError(R.string.fail_BT_pair)
		}
	}
	private fun checkKnownAddress(knownAddress: String, bondedDevices: Set<BluetoothDevice>) {
		for(device in bondedDevices) {
			if(device.address == knownAddress) {
				knownDevices.add(Device(device))
				return
			}
		}
		delKnownAddress(knownAddress)
	}
	private fun delKnownAddress(address: String) {
		if(knownAddresses.remove(address)) storeKnownAddresses()
	}
	fun storeKnownAddresses() {
		sharedPreferences?.edit {
			if(knownAddresses.isEmpty()) remove("knownAddresses")
			else putStringSet("knownAddresses", knownAddresses)
		}
	}

	fun stop() {
		logD{"CommsBT.stop()"}
		disconnect = true
		status.value = Status.DISCONNECTED
		freeSpace.value = ""
		messageStatus.value = -1
		tryIgnore { bluetoothSocket?.close() }
		bluetoothSocket = null
		commsBTConnect = null
		commsBTConnected = null
	}

	@SuppressLint("MissingPermission")
	fun getBondedDevices(context: Context): List<Device>? {
		if(!context.hasBTPermission() || bluetoothAdapter == null) {
			onError(R.string.fail_BT_denied)
			return null
		}
		if(bluetoothAdapter?.state != BluetoothAdapter.STATE_ON) {
			onError(R.string.fail_BT_off)
			return null
		}

		return bluetoothAdapter?.bondedDevices?.map { Device(it) }
	}

	@SuppressLint("MissingPermission")
	fun connectDevice(context: Context, device: Device) {
		if(!context.hasBTPermission()) return onError(R.string.fail_BT_denied)
		logD{"CommsBT.connectDevice: ${device.name}"}
		if(status.value in listOf(Status.CONNECTED, Status.CONNECTING) ||
			bluetoothAdapter?.isEnabled != true ||
			device.device == null
		) return
		disconnect = false
		deviceName = device.name
		status.value = Status.CONNECTING
		commsBTConnect = CommsBTConnect(device.device)
		commsBTConnect?.start()
	}

	fun sendRequestSync() {
		commsBTConnected?.requestQueue?.let { requestQueue ->
			if(requestQueue.isEmpty())
				requestQueue.add(CommsBTConnected.Request(CommsBTConnected.Request.Type.SYNC))
		}
	}
	fun sendRequestSendFile(libItem: LibItem) {
		logD{"CommsBT.sendRequestSendFile ${libItem.name}"}
		Library.setItemStatus(libItem, LibItem.Status.PENDING)
		commsBTConnected?.requestQueue?.add(CommsBTConnected.Request(
			type = CommsBTConnected.Request.Type.SEND_FILE,
			libItem = libItem
		))
	}
	fun sendRequestDeleteFile(libItem: LibItem) {
		logD{"CommsBT.sendRequestDeleteFile ${libItem.name}"}
		Library.setItemStatus(libItem, LibItem.Status.PENDING)
		commsBTConnected?.requestQueue?.add(CommsBTConnected.Request(
			type = CommsBTConnected.Request.Type.DELETE_FILE,
			libItem = libItem
		))
	}
	fun confirmDeleteFile(libItem: LibItem) {
		itemExistsAskToDelete.value = null
		sendRequestDeleteFile(libItem)
	}
	fun sendRequestPutPlaylist(playlistId: Int) =
		commsBTConnected?.requestQueue?.add(CommsBTConnected.Request(
			type = CommsBTConnected.Request.Type.PUT_PLAYLIST,
			playlistId = playlistId
		))
	fun sendRequestDelPlaylist(playlistId: Int) =
		commsBTConnected?.requestQueue?.add(CommsBTConnected.Request(
			type = CommsBTConnected.Request.Type.DEL_PLAYLIST,
			playlistId = playlistId
		))
	fun sendRequestUpdPlaylist(playlistId: Int, oldPlaylistId: Int) {
		sendRequestPutPlaylist(playlistId)
		if(COMMS_VERSION < 3) sendRequestDelPlaylist(oldPlaylistId)
	}

	class CommsBTConnect(device: BluetoothDevice) : Thread() {
		init {
			logD{"CommsBTConnect $deviceName"}
			try {
				bluetoothSocket = device.createRfcommSocketToServiceRecord(WMP_UUID)
			} catch(e: Exception) {
				logE("CommsBTConnect: ${e.message}")
				status.value = Status.DISCONNECTED
			}
		}

		override fun run() {
			try {
				bluetoothSocket?.connect()
				commsBTConnected = CommsBTConnected()
				commsBTConnected?.start()
			} catch(e: Exception) {
				logD{"CommsBTConnect.run: ${e.message}"}
				tryIgnore { bluetoothSocket?.close() }
				status.value = Status.DISCONNECTED
			}
		}
	}

	@Suppress("BlockingMethodInNonBlockingContext")//only called in background thread
	class CommsBTConnected : Thread() {
		private var inputStream: InputStream? = null
		private var outputStream: OutputStream? = null
		val requestQueue: MutableSet<Request> = mutableSetOf()
		var request: Request? = null
		private var privateKey: Key? = null
		private var publicKey: Key? = null
		private fun getPrivateKey(address: String): Key = KeyStore.getInstance("AndroidKeyStore")
			.apply { load(null) }.getKey(address, null)
		private fun getPublicKey(address: String): Key = KeyFactory.getInstance("RSA")
			.generatePublic(X509EncodedKeySpec(
				Base64.decode(knownDeviceKeys[address]!!)
			))

		init {
			logD{"CommsBTConnected"}
			try {
				inputStream = bluetoothSocket!!.inputStream
				outputStream = bluetoothSocket!!.outputStream
			} catch(e: Exception) {
				logE("CommsBTConnected init: ${e.message}")
				status.value = Status.DISCONNECTED
			}
		}

		fun encrypt(input: String): String {
			val aesKey = KeyGenerator.getInstance("AES")
				.apply { init(256) }.generateKey()
			val aesIV = ByteArray(12)
			SecureRandom().nextBytes(aesIV)
			val aesParams = GCMParameterSpec(128, aesIV)
			val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
				.apply { init(Cipher.ENCRYPT_MODE, aesKey, aesParams) }

			val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
			val rsaParams = OAEPParameterSpec("SHA-256", "MGF1",
				MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
			rsaCipher.init(Cipher.ENCRYPT_MODE, publicKey, rsaParams)

			return Base64.encode(
				rsaCipher.doFinal(aesKey.encoded) + aesIV +
						aesCipher.doFinal(input.toByteArray(Charsets.UTF_8))
			)
		}
		private fun decrypt(encrypted: String): String {
			val decoded = Base64.decode(encrypted)
			val encryptedAESKey = decoded.sliceArray(0..255)
			val aesIV = decoded.sliceArray(256..267)
			val encryptedData = decoded.sliceArray(268..decoded.lastIndex)

			val rsaCipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding")
			val rsaParams = OAEPParameterSpec(
				"SHA-256",
				"MGF1",
				MGF1ParameterSpec.SHA1,
				PSource.PSpecified.DEFAULT
			)
			rsaCipher.init(Cipher.DECRYPT_MODE, privateKey, rsaParams)

			val aesKeyBytes = rsaCipher.doFinal(encryptedAESKey)
			val aesKey = SecretKeySpec(aesKeyBytes, 0, aesKeyBytes.size, "AES")
			val aesParams = GCMParameterSpec(128, aesIV)
			val aesCipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
				init(Cipher.DECRYPT_MODE, aesKey, aesParams)
			}

			return String(aesCipher.doFinal(encryptedData), Charsets.UTF_8)
		}
		private fun createKeys(): String {
			val parameterSpec = KeyGenParameterSpec.Builder(
				bluetoothSocket!!.remoteDevice.address,
				KeyProperties.PURPOSE_DECRYPT
			).run {
				setKeySize(2048)
				setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA1)
				setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
				build()
			}
			val keyPair = KeyPairGenerator.getInstance("RSA", "AndroidKeyStore")
				.apply { initialize(parameterSpec) }
				.generateKeyPair()
			privateKey = keyPair.private

			val publicKey = Base64.encode(keyPair.public.encoded)
			val hash = MessageDigest.getInstance("SHA-256")
				.digest(publicKey.toByteArray())
				.fold("") { str, it -> str + "%02x".format(it) }
				.uppercase()
			pairCode = hash.take(3) + " " + hash.substring(hash.length - 3)
			return publicKey
		}

		override fun run() {
			runInBackground {
				try{
					privateKey = getPrivateKey(bluetoothSocket!!.remoteDevice.address)
					publicKey = getPublicKey(bluetoothSocket!!.remoteDevice.address)
					status.value = Status.CONNECTED
					COMMS_VERSION = 3//watch is on version 3, we need to upgrade to it
					sendRequestSync()
				} catch (_: Exception) {
					requestQueue.add(Request(Request.Type.PAIR, createKeys()))
					status.value = Status.PAIRING
				}
				process()
			}
		}

		private fun close() {
			logD{"CommsBTConnected.close"}
			status.value = Status.DISCONNECTED
			freeSpace.value = ""
			messageStatus.value = -1
			tryIgnore {
				requestQueue.clear()
				bluetoothSocket?.close()
			}
			request = null
			bluetoothSocket = null
			commsBTConnected = null
			commsBTConnect = null
			privateKey = null
			publicKey = null
		}

		private suspend fun process() {
			while(!disconnect) {
				try {
					outputStream!!.write("".toByteArray())
				} catch(_: Exception) {
					logD{"Connection closed"}
					break
				}
				sendNextRequest()
				read()
				delay(100)
			}
			close()
		}

		private fun sendNextRequest() {
			try {
				outputStream!!.write("".toByteArray())
				if(requestQueue.isEmpty() || CommsWifi.isSending || request != null) return
				request = requestQueue.first()
				requestQueue.remove(request)
				logD{"CommsBTConnected.sendNextRequest: $request"}
				if(request!!.type == Request.Type.SEND_FILE) {
					val libItem = request!!.libItem!!
					runInBackground { CommsWifi.sendFile(libItem) }
				}
				outputStream!!.write(request.toString().toByteArray())
			} catch(e: Exception) {
				logE("CommsBTConnected.sendNextRequest: ${e.message}")
				onMessageError(R.string.fail_send_message)
				disconnect = true
			}
		}

		private suspend fun read() {
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
						request = null
						return onMessageError(R.string.fail_response)
					} else if(numBytes > 0) {
						lastReadTime = System.currentTimeMillis()
					}
					val temp = String(buffer)
					response += temp
					if(isValidJSON(response)) {
						logD{"CommsBTConnected.read got message: $response"}
						gotResponse(JSONObject(response))
						return
					}
				}
				logE("CommsBTConnected.read no valid message and no new data after 3 sec: $response")
				request = null
			} catch(e: Exception) {
				logE("CommsBTConnected.read: ${e.message}")
				request = null
			}
			onMessageError(R.string.fail_response)
		}

		private fun isValidJSON(json: String): Boolean {
			if(!json.endsWith("}")) return false
			try { JSONObject(json) }
			catch(_: JSONException) { return false }
			return true
		}
		private fun gotResponse(response: JSONObject) {
			try {
				if(response.getInt("version") > 3) {
					onError(R.string.fail_app_outdated)
					return
				}
				val requestType = response.getString("requestType")
				val responseData = response.get("responseData")
				val responseCode = responseData as? Int ?: 0
				when(requestType) {
					"pair" -> {
						if(responseCode == -2) {
							//the watch does not support pairing yet, stick with COMMS_VERSION 2
							status.value = Status.CONNECTED
							sendRequestSync()
							request = null
							if(knownDevices.add(Device(bluetoothSocket!!.remoteDevice)) &&
								knownAddresses.add(bluetoothSocket!!.remoteDevice.address)
							) { storeKnownAddresses() }
							return
						}
						if(responseCode < 0) {
							bluetoothSocket?.remoteDevice?.address?.let { delKnownDevice(it) }
							request = null
							status.value = Status.DISCONNECTED
							onError(R.string.pair_rejected)
							return
						}
						logD{"responseData: $responseData"}
						val response = decrypt(responseData as String)
						logD{"response: $response"}
						addKnownDevice(response, Device(bluetoothSocket!!.remoteDevice))
						publicKey = getPublicKey(bluetoothSocket!!.remoteDevice.address)
						status.value = Status.CONNECTED
						COMMS_VERSION = 3//watch is on version 3, we need to upgrade to it
						sendRequestSync()
						request = null
					}
					"sync" -> {
						//version 2: {"requestType":"sync","responseData":{"tracks":[{"path":"directory\/track1.mp3"},{"path":"track2.mp3"}],"freeSpace":5672968192,"playlistIds":[123,124]}}
						//version 3: {"requestType":"sync","responseData":{"tracks":[{"path":"directory\/track1.mp3"},{"path":"track2.mp3"}],"freeSpace":5672968192,"playlists":[{"id":123,"version":4}]}}
						//CODE_FAIL
						//CODE_UNKNOWN_REQUEST_TYPE
						val response =
							if(COMMS_VERSION == 2) responseData as JSONObject
							else JSONObject(decrypt(responseData as String))
						freeSpace.value = bytesToHuman(response.getLong("freeSpace"))
						runInBackground {
							Library.updateLibWithFilesOnWatch(
								response.getJSONArray("tracks")
							)
							if(requestQueue.isEmpty()) messageStatus.value = R.string.sync_done
						}
						if(response.has("playlistIds")) {//COMMS_VERSION 2
							val playlistIds = response.getJSONArray("playlistIds")
							val remotePlaylistIds = (0 until playlistIds.length())
								.map { playlistIds.getInt(it) }.toSet()
							val localPlaylistIds = Playlists.all.value.map { it.id }.toSet()
							remotePlaylistIds
								.filter { it !in localPlaylistIds }
								.forEach { sendRequestDelPlaylist(it) }
							Playlists.all.value
								.filter { it.trackPaths.isNotEmpty() && it.id !in remotePlaylistIds }
								.forEach { sendRequestPutPlaylist(it.id) }
						} else if(response.has("playlists")) {//COMMS_VERSION 3
							val playlistsJson = response.getJSONArray("playlists")
							val remotePlaylists = (0 until playlistsJson.length())
								.map { playlistsJson.getJSONObject(it) }
								.associate { it.getInt("id") to it.getInt("version") }
							val localPlaylists = Playlists.all.value
								.filter { it.trackPaths.isNotEmpty() }
								.associate { it.id to it.version }
							remotePlaylists.keys.forEach { id ->
								if(id !in localPlaylists) sendRequestDelPlaylist(id)
							}
							localPlaylists.forEach { (id, version) ->
								if(remotePlaylists[id] != version) sendRequestPutPlaylist(id)
							}
						}
						request = null
					}
					"fileDetails" -> {
						//{"requestType":"fileDetails","responseData":1}
						//CODE_PENDING = 1
						if(responseCode < 1) {
							CommsWifi.stop()
							logE("CommsBT.gotResponse fileDetails responseData: $responseData")
							when(responseCode){
								//CODE_FAIL -> R.string.fail_send_file
								CODE_UNKNOWN_REQUEST_TYPE -> messageStatus.value = R.string.fail_app_outdated
								//CODE_FAIL_CREATE_DIRECTORY -> R.string.fail_send_file
								//CODE_FAIL_CREATE_FILE -> R.string.fail_send_file
								CODE_FILE_EXISTS -> {
									messageStatus.value = R.string.fail_file_exists
									request?.libItem?.let { libItem ->
										itemExistsAskToDelete.value = libItem
									}
								}
								else -> messageStatus.value = R.string.fail_send_file
							}
						}
						request = null
					}
					"fileBinary" -> {
						//{"requestType":"fileBinary","responseData":{"path":"directory\/track.mp3","freeSpace":12345}}
						//CODE_FAIL
						//CODE_UNKNOWN_REQUEST_TYPE
						val response =
							if(COMMS_VERSION == 2) responseData as JSONObject
							else JSONObject(decrypt(responseData as String))
						freeSpace.value = bytesToHuman(response.getLong("freeSpace"))
						request?.libItem?.let{ Library.setItemUploaded(it) }
						logD{"CommsBT.gotResponse fileBinary lastRequest: ${request?.libItem?.path}"}
						messageStatus.value = R.string.file_sent
						request = null
					}
					"deleteFile" -> {
						//{"requestType":"deleteFile","responseData":0}
						//CODE_PENDING
						//CODE_OK
						//CODE_FAIL
						//CODE_UNKNOWN_REQUEST_TYPE
						//CODE_FAIL_DEL_FILE
						//CODE_DECLINED
						when(responseCode) {
							CODE_PENDING -> messageStatus.value = R.string.delete_file_confirm
							CODE_OK -> {
								request?.libItem?.let{ Library.setItemDeleted(it) }
								messageStatus.value = R.string.file_deleted
								request = null
							}
							CODE_DECLINED -> {
								request?.libItem?.let{ Library.setItemUploaded(it) }
								messageStatus.value = R.string.delete_file_declined
								request = null
							}
							else -> {
								logE("CommsBT.gotResponse deleteFile: $responseData")
								messageStatus.value = R.string.fail_delete_file
								request = null
							}
						}
					}
					"putPlaylist", "delPlaylist" -> {
						//{"requestType":"put/delPlaylist","responseData":0}
						//CODE_OK
						//CODE_FAIL
						if(responseCode == CODE_OK) {
							if(requestQueue.isEmpty()) messageStatus.value = R.string.sync_done
						} else {
							logE("CommsBT.gotResponse put/delPlaylist: $responseData")
							messageStatus.value = R.string.fail_sync_playlists
						}
						request = null
					}
					else -> throw Exception("I don't know requestType $requestType")
				}
			} catch(e: Exception) {
				logE("CommsBT.gotResponse: ${e.message}")
				onMessageError(R.string.fail_response)
				request = null
			}
		}
		class Request(
			val type: Type,
			private val publicKey: String? = null,
			val libItem: LibItem? = null,
			val playlistId: Int? = null
		) {
			enum class Type { PAIR, SYNC, SEND_FILE, DELETE_FILE, PUT_PLAYLIST, DEL_PLAYLIST }
			override fun toString(): String = JSONObject().apply {
				put("version", COMMS_VERSION)
				when(type) {
					Type.PAIR -> {
						put("requestType", "pair")
						put("publicKey", publicKey)
					}
					Type.SYNC -> {
						put("requestType", "sync")
						messageStatus.value = R.string.sync
					}
					Type.SEND_FILE -> {
						//{"requestType":"fileDetails","requestData":{"path":"Music/track.mp3","length":12345,"ip":"192.168.1.100","port":9002}}
						put("requestType", "fileDetails")
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
						libItem.length = file.length().toFloat()
						val requestData = JSONObject().apply {
							put("path", libItem.path)
							put("length", libItem.length)
							put("ip", CommsWifi.ipAddress)
							put("port", CommsWifi.PORT_NUMBER)
						}
						put("requestData",
							if(COMMS_VERSION == 2) requestData
							else commsBTConnected?.encrypt(requestData.toString())
						)
						messageStatus.value = R.string.send_file
					}
					Type.DELETE_FILE -> {
						//{"requestType":"deleteFile","requestData":"track.mp3"}
						put("requestType", "deleteFile")
						val path = libItem?.path
						if(path == null) {
							onMessageError(R.string.fail_send_message)
							return ""
						}
						put(
							"requestData",
							if(COMMS_VERSION == 2) path
							else commsBTConnected?.encrypt(path)
						)
						messageStatus.value = R.string.delete_file
					}
					Type.PUT_PLAYLIST -> {
						//{"requestType":"putPlaylist","requestData":{"id":123,"name":"Favorites","tracks":["path1","path2"]}}
						put("requestType", "putPlaylist")
						val requestData = Playlists.all.value.first { it.id == playlistId }.toJson()
						put("requestData",
							if(COMMS_VERSION == 2) requestData
							else commsBTConnected?.encrypt(requestData.toString())
						)
						messageStatus.value = R.string.sync
					}
					Type.DEL_PLAYLIST -> {
						//{"requestType":"delPlaylist","requestData":123}
						put("requestType", "delPlaylist")
						put("requestData",
							if(COMMS_VERSION == 2) playlistId
							else commsBTConnected?.encrypt(playlistId.toString())
						)
						messageStatus.value = R.string.sync
					}
				}
			}.toString()
		}
	}

	fun onError(message: Int) {
		error.value = message
		status.value = Status.ERROR
	}

	fun onMessageError(message: Int) {
		messageStatus.value = message
		commsBTConnected?.request?.libItem?.let{
			Library.setItemStatus(it, LibItem.Status.UNKNOWN)
		}
	}
}
