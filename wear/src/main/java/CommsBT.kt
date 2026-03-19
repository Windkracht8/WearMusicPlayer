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
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.windkracht8.wearmusicplayer.data.Library
import com.windkracht8.wearmusicplayer.data.Playlists
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
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

val WMP_UUID: UUID =
	if(BuildConfig.DEBUG) UUID.fromString("6f34da3f-188a-4c8c-989c-2baacf8ea6ed")
	else UUID.fromString("6f34da3f-188a-4c8c-989c-2baacf8ea6e1")
const val COMMS_VERSION = 3

object CommsBT {
	private var sharedPreferences: SharedPreferences? = null
	const val CODE_PENDING = 1
	const val CODE_OK = 0
	const val CODE_FAIL = -1
	private const val CODE_UNKNOWN_REQUEST_TYPE = -2
	const val CODE_FAIL_CREATE_DIRECTORY = -3
	const val CODE_FAIL_CREATE_FILE = -4
	const val CODE_FILE_EXISTS = -5
	const val CODE_FAIL_DEL_FILE = -6
	const val CODE_DECLINED = -6

	var bluetoothAdapter: BluetoothAdapter? = null
	var bluetoothServerSocket: BluetoothServerSocket? = null
	var bluetoothSocket: BluetoothSocket? = null
	private var commsBTConnect: CommsBTConnect? = null
	var commsBTConnected: CommsBTConnected? = null
	val error = MutableSharedFlow<Int>()

	var pairing by mutableStateOf(false)
	var pairCode by mutableStateOf<String?>(null)
	var knownDeviceNames = mutableStateMapOf<String, String>()
	var publicKeyString: String? = null
	var knownDeviceKeys: MutableMap<String, String> = mutableMapOf()

	var disconnect = false
	val responseQueue: MutableSet<JSONObject> = mutableSetOf()
	val deleteFilePath = MutableStateFlow("")

	fun start(context: Context) {
		//logD{"CommsBT.start"}
		CommsWifi.init(context)
		disconnect = false
		if(bluetoothAdapter == null) {
			val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
			bluetoothAdapter = bluetoothManager.adapter
			if(bluetoothAdapter?.isEnabled != true) {
				logD{"CommsBT.start bluetooth disabled"}
				return
			}
		}

		sharedPreferences = context.getSharedPreferences("CommsBT", MODE_PRIVATE)
		try {
			sharedPreferences?.getString("knownDeviceKeys", null)?.let { jsonString ->
				val jsonObject = JSONObject(jsonString)
				jsonObject.keys().forEach { knownDeviceKeys[it] = jsonObject.getString(it) }
			}
			sharedPreferences?.getString("knownDeviceNames", null)?.let { jsonString ->
				val jsonObject = JSONObject(jsonString)
				jsonObject.keys().forEach { knownDeviceNames[it] = jsonObject.getString(it) }
			}
		} catch(e: Exception) { logE("CommsBT.start: ${e.message}") }

		if(commsBTConnect == null) {
			logD{"CommsBT.start start listening"}
			commsBTConnect = CommsBTConnect()
			commsBTConnect?.start()
		}
	}
	fun restart(){
		disconnect = true
		tryIgnore { bluetoothServerSocket?.close() }
		tryIgnore { bluetoothSocket?.close() }
		responseQueue.clear()
		bluetoothServerSocket = null
		bluetoothSocket = null
		commsBTConnect = null
		commsBTConnected = null
		disconnect = false
		commsBTConnect = CommsBTConnect()
		commsBTConnect?.start()
	}
	fun stop() {
		//logD{"CommsBT.stop"}
		disconnect = true
		tryIgnore { bluetoothServerSocket?.close() }
		tryIgnore { bluetoothSocket?.close() }
		bluetoothServerSocket = null
		bluetoothSocket = null
		bluetoothAdapter = null
		commsBTConnect = null
		commsBTConnected = null
	}
	private fun addKnownDevice(publicKey: String, address: String, deviceName: String) {
		knownDeviceKeys[address] = publicKey
		knownDeviceNames[address] = deviceName
		storeKnownDevices()
	}
	fun delKnownDevice(address: String) {
		knownDeviceKeys.remove(address)
		knownDeviceNames.remove(address)
		storeKnownDevices()
		try {
			KeyStore.getInstance("AndroidKeyStore").apply {
				load(null)
				if(containsAlias(address)) deleteEntry(address)
			}
		} catch(e: Exception) { logE("CommsBT.delKnownDevice: ${e.message}") }
	}
	private fun storeKnownDevices() {
		try {
			sharedPreferences?.edit()?.apply {
				putString("knownDeviceKeys", JSONObject(knownDeviceKeys as Map<*, *>).toString())
				putString("knownDeviceNames", JSONObject(knownDeviceNames as Map<*, *>).toString())
				apply()
			}
		} catch(e: Exception) {
			logE("CommsBT.storeKnownDevices: ${e.message}\n$e")
			onError(R.string.fail_bt_pair)
		}
	}

	fun rejectPairRequest(){
		publicKeyString = null
		pairing = false
		pairCode = null
		try {
			sendResponse("pair", CODE_DECLINED)
		} catch(e: Exception) {
			logE("CommsBT.rejectPairRequest: ${e.message}\n$e")
			onError(R.string.fail_respond)
		}
	}

	@SuppressLint("MissingPermission")//is handled in Main
	fun acceptPairRequest(){
		pairing = false
		pairCode = null
		try {
			addKnownDevice(publicKeyString!!, bluetoothSocket!!.remoteDevice.address, bluetoothSocket!!.remoteDevice.name)
			val responseData = commsBTConnected?.createKeys()
			if(responseData is String) sendResponse("pair", responseData)
			else sendResponse("pair", CODE_FAIL)
		} catch(e: Exception) {
			logE("CommsBT.acceptPairRequest: ${e.message}\n$e")
			onError(R.string.fail_respond)
		}
		publicKeyString = null
	}

	fun sendSyncResponse() {
		sendResponse("sync", JSONObject().apply {
			put("tracks", Library.getTracksJson())
			put("freeSpace", Library.getFreeSpace())
			put("playlists", JSONArray().apply {
				Playlists.all.forEach { put(
					JSONObject().apply {
						put("id", it.id)
						put("version", it.version)
					}
				) }
			})
		}.toString())
	}
	fun sendFileBinaryResponse() {
		sendResponse("fileBinary", JSONObject().apply {
			put("path", CommsWifi.path)
			put("freeSpace", Library.getFreeSpace())
		}.toString())
		logD{"CommsBT.sendFileBinaryResponse ${responseQueue.lastOrNull()}"}
	}
	fun sendResponse(requestType: String, responseData: Int) {
		responseQueue.add(JSONObject().apply {
			put("version", COMMS_VERSION)
			put("requestType", requestType)
			put("responseData", responseData)
		})
		logD{"CommsBT.sendResponse ${responseQueue.lastOrNull()}"}
	}
	private fun sendResponse(requestType: String, responseData: String) {
		responseQueue.add(JSONObject().apply {
			put("version", COMMS_VERSION)
			put("requestType", requestType)
			put("responseData", commsBTConnected?.encrypt(responseData))
		})
		logD{"CommsBT.sendResponse ${responseQueue.lastOrNull()}"}
	}

	class CommsBTConnect @SuppressLint("MissingPermission") //Permissions are handled in Main
	constructor() : Thread() {
		init {
			try {
				//logD{"CommsBTConnect"}
				bluetoothServerSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(
					"WearMusicPlayer",
					WMP_UUID
				)
			} catch(e: Exception) {
				if(!disconnect) logE("CommsBTConnect: ${e.message}")
			}
		}

		override fun run() {
			try {
				bluetoothSocket = bluetoothServerSocket?.accept()
				if(!disconnect) {
					logD{"CommsBTConnect.run accepted"}
					commsBTConnected = CommsBTConnected()
					commsBTConnected?.start()
				}
			} catch(e: Exception) {
				if(!disconnect) logE("CommsBTConnect.run: ${e.message}")
			}
		}
	}

	@Suppress("BlockingMethodInNonBlockingContext")//only called in background thread
	class CommsBTConnected : Thread() {
		private var inputStream: InputStream? = null
		private var outputStream: OutputStream? = null
		private var privateKey: Key? = null
		private var publicKey: Key? = null
		private fun getPrivateKey(address: String): Key = KeyStore.getInstance("AndroidKeyStore")
			.apply { load(null) }.getKey(address, null)
		private fun getPublicKey(address: String): Key = KeyFactory.getInstance("RSA")
			.generatePublic(X509EncodedKeySpec(
				Base64.decode(knownDeviceKeys[address]!!)
			))

		init {
			try {
				inputStream = bluetoothSocket?.inputStream
				outputStream = bluetoothSocket?.outputStream
			} catch(e: Exception) {
				logE("CommsBTConnected init: ${e.message}")
			}
		}
		override fun run() {
			logD{"CommsBTConnected.run"}
			runInBackground { process() }
		}
		private suspend fun process() {
			while(!disconnect) {
				read()
				try {
					outputStream!!.write("".toByteArray())
				} catch(_: Exception) {
					logD{"Connection closed"}
					restart()
					return
				}
				if(responseQueue.isNotEmpty() && !sendNextResponse()) {
					restart()
					return
				}
				delay(100)
			}
		}

		fun encrypt(input: String): String? {
			if(publicKey == null) {
				try {
					publicKey = getPublicKey(bluetoothSocket!!.remoteDevice.address)
				} catch(e: Exception) {
					logE("CommsBTConnected.encrypt: ${e.message}\n$e")
					rejectPairRequest()
					return null
				}
			}
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
		private fun decrypt(encrypted: String): String? {
			if(privateKey == null) {
				try {
					privateKey = getPrivateKey(bluetoothSocket!!.remoteDevice.address)
				} catch(e: Exception) {
					logE("CommsBTConnected.decrypt: ${e.message}\n$e")
					rejectPairRequest()
					return null
				}
			}
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
		fun createKeys(): String {
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

		private fun sendNextResponse(): Boolean {
			try {
				val response = responseQueue.first()
				responseQueue.remove(response)
				logD{"CommsBTConnected.sendNextResponse: $response"}
				outputStream!!.write(response.toString().toByteArray())
			} catch(e: Exception) {
				logE("CommsBTConnected.sendNextResponse: ${e.message}")
				onError(R.string.fail_respond)
				return false
			}
			return true
		}

		private suspend fun read() {
			try {
				if((inputStream?.available() ?: 0) < 5) return
				var lastReadTime = System.currentTimeMillis()
				var request = ""
				while(System.currentTimeMillis() - lastReadTime < 3000) {
					if((inputStream?.available() ?: 0) == 0) {
						delay(100)
						continue
					}
					val buffer = ByteArray(inputStream?.available() ?: 0)
					val numBytes = inputStream?.read(buffer) ?: 0
					if(numBytes < 0) {
						logE("CommsBTConnected.read read error, request: $request")
						onError(R.string.fail_request)
						return
					}
					val temp = String(buffer)
					request += temp
					if(isValidJSON(request)) {
						gotRequest(request)
						return
					}
					lastReadTime = System.currentTimeMillis()
				}
				logE("CommsBTConnected.read no valid message and no new data after 3 sec: $request")
			} catch(e: Exception) {
				logE("CommsBTConnected.read: ${e.message}")
			}
			onError(R.string.fail_request)
		}

		private fun isValidJSON(json: String): Boolean {
			if(!json.endsWith("}")) return false
			try { JSONObject(json) }
			catch(_: JSONException) { return false }
			return true
		}
		private fun gotRequest(request: String) {
			logD{"CommsBTConnected.gotRequest: $request"}
			var requestType = "unknown"
			try {
				val requestMessage = JSONObject(request)
				requestType = requestMessage.getString("requestType")
				when(requestType) {
					"pair" -> {
						//{"requestType":"pair","publicKey":"123456"]}
						if(!pairing) rejectPairRequest()
						try {
							publicKeyString = requestMessage.getString("publicKey")
							val hash = MessageDigest.getInstance("SHA-256")
								.digest(publicKeyString!!.toByteArray())
								.fold("") { str, it -> str + "%02x".format(it) }
								.uppercase()
							pairCode = hash.take(3) + " " + hash.substring(hash.length - 3)
						} catch(e: Exception) {
							logE("CommsBT.gotRequest: ${e.message}")
							onError(R.string.fail_request)
						}
						//{"requestType":"pair","publicKey":"234567"]}
					}
					"sync" -> {
						//{"requestType":"sync"}
						sendSyncResponse()
						//version 2:{"requestType":"sync","responseData":{"tracks":[{"path":"directory\/track1.mp3"},{"path":"track2.mp3"}],"freeSpace":5672968192,"playlistIds":[123,124]}}
						//version 3:{"requestType":"sync","responseData":{"tracks":[{"path":"directory\/track1.mp3"},{"path":"track2.mp3"}],"freeSpace":5672968192,"playlistIds":[{"id":123,"version":4}]}}
					}
					"fileDetails" -> {
						//{"requestType":"fileDetails","requestData":{"path":"directory\/track3.mp3","length":12345,"ip":"192.168.1.100","port":9002}}
						val requestData = JSONObject(decrypt(requestMessage.getString("requestData"))!!)
						val path = requestData.getString("path")
						if(path.isPathUnsave()){
							logE("CommsBTConnected.gotRequest fileDetails isPathUnsave() says no")
							onError(R.string.fail_request)
							sendResponse("fileDetails", CODE_FAIL)
							return
						}
						val reason: Int = Library.ensurePath(path)
						if(reason < 0) {
							logE("CommsBTConnected.gotRequest fileDetails result: $reason")
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
						val path = decrypt(requestMessage.getString("requestData"))!!
						if(path.isPathUnsave()){
							logE("CommsBTConnected.gotRequest deleteFile isPathUnsave() says no")
							onError(R.string.fail_request)
							sendResponse("deleteFile", CODE_FAIL)
							return
						}
						deleteFilePath.value = path
						//{"requestType":"deleteFile","responseData":0}
					}
					"putPlaylist" -> {
						//{"requestType":"putPlaylist","requestData":{"id":123,"name":"Favorites","tracks":["path1","path2"]}}
						val requestData = JSONObject(decrypt(requestMessage.getString("requestData"))!!)
						val playlist = Playlists.create(requestData)
						sendResponse("putPlaylist",
							if(playlist == null) CODE_FAIL else CODE_OK
						)
						//{"requestType":"putPlaylist","responseData":0}
					}
					"delPlaylist" -> {
						//{"requestType":"delPlaylist","requestData":123}
						val requestData = decrypt(requestMessage.getString("requestData"))!!.toInt()
						Playlists.delete(requestData)
						sendResponse("delPlaylist", CODE_OK)
						//{"requestType":"delPlaylist","responseData":0}
					}
					else -> {
						logE("CommsBTConnected.gotRequest Unknown requestType: $requestType")
						sendResponse(requestType, CODE_UNKNOWN_REQUEST_TYPE)
					}
				}
			} catch(e: Exception) {
				logE("CommsBTConnected.gotRequest: ${e.message}")
				onError(R.string.fail_request)
				sendResponse(requestType, CODE_FAIL)
			}
		}
	}
	fun onError(message: Int) = runInBackground { error.emit(message) }
}
