/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class Main : ComponentActivity() {
	var commsBTStatus by mutableStateOf(CommsBT.status.value)
	var showLoading by mutableStateOf(true)
	override fun onCreate(savedInstanceState: Bundle?) {
		installSplashScreen()
		super.onCreate(savedInstanceState)
		setContent {
			W8Theme (window, resources) {
				Surface {
					Home(
						commsBTStatus,
						showLoading,
						::onIconClick,
						::onOpenFolderClick,
						::onItemIconClick
					)
				}
			}
		}

		lifecycleScope.launch {
			Library.status.collect { libraryStatus ->
				logD{"Main: Library status change: $libraryStatus"}
				when(libraryStatus) {
					Library.Status.SCAN -> showLoading = true
					Library.Status.READY -> {
						showLoading = false
						if(CommsBT.status.value == CommsBT.Status.CONNECTED) CommsBT.sendRequestSync()
					}
					null -> {}
				}
			}
		}
		lifecycleScope.launch {
			CommsBT.status.collect { status ->
				logD{"Main: CommsBT status change: $status"}
				commsBTStatus = CommsBT.status.value
				when(commsBTStatus) {
					CommsBT.Status.CONNECTING ->
						startActivity(Intent(this@Main, DeviceConnect::class.java))
					CommsBT.Status.DISCONNECTED, CommsBT.Status.ERROR -> {
						Library.rootLibDir.clearStatuses()
						Library.watchLibDir = LibDir("")
					}
					else -> {}
				}
			}
		}

		Permissions.checkPermissions(this)
		if(!Permissions.hasBT || !Permissions.hasRead) startActivity(
			Intent(
				this,
				Permissions::class.java
			)
		)
	}

	override fun onResume() {
		super.onResume()
		if(Permissions.hasRead && Library.status.value == null) Library.scanFiles()
		if(Permissions.hasBT) {
			registerReceiver(
				btBroadcastReceiver,
				IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
			)
			if(CommsBT.status.value == null) runInBackground { CommsBT.start(this@Main) }
		}
		Playlists.init(this) { toast(R.string.fail_read_playlist) }
	}

	override fun onPause() {
		super.onPause()
		tryIgnore { unregisterReceiver(btBroadcastReceiver) }
	}

	override fun onDestroy() {
		super.onDestroy()
		runInBackground { tryIgnore { CommsBT.stop() } }
	}

	fun onIconClick() {
		logD{"onIconClick: ${CommsBT.status.value}"}
		if(!Permissions.hasBT || !Permissions.hasRead) {
			startActivity(Intent(this, Permissions::class.java))
		} else if(CommsBT.status.value == CommsBT.Status.DISCONNECTED) {
			startActivity(Intent(this, DeviceSelect::class.java))
		} else {
			CommsBT.stop()
		}
	}

	fun onOpenFolderClick() {
		try {
			openFolderResult.launch(null)
		} catch(e: Exception) {
			logE("Main.onOpenFolderClick Exception: " + e.message)
			toast(R.string.fail_open_folder_request)
		}
	}


	val openFolderResult =
		registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
			logD{"Main.openFolderResult: ${uri?.path}"}
			val fullPath = uri?.path
			if(fullPath == null) {
				logE("Main.openFolderResult empty uri")
				toast(R.string.fail_open_folder)
			} else {
				val path = fullPath.removePrefix("/tree/primary:")
				showLoading = true
				Library.scanFiles(path)
			}
		}

	fun onItemIconClick(libItem: LibItem) {
		logD{"Main.onItemIconClick: ${libItem.name}"}
		if(libItem is LibDir || CommsBT.status.value != CommsBT.Status.CONNECTED) return
		if(libItem.status == LibItem.Status.FULL) {
			CommsBT.sendRequestDeleteFile(libItem)
		} else if(libItem.status == LibItem.Status.NOT) {
			CommsWifi.getIpAddress(this)
			CommsBT.sendRequestSendFile(libItem)
		}
	}

	val btBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent) {
			if(BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
				val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
				if(btState == BluetoothAdapter.STATE_TURNING_OFF) {
					CommsBT.onError(R.string.fail_BT_off)
					CommsBT.stop()
				} else if(btState == BluetoothAdapter.STATE_ON) {
					CommsBT.start(this@Main)
				}
			}
		}
	}
}
