/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.windkracht8.wearmusicplayer.ui

import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.windkracht8.wearmusicplayer.CommsBT
import com.windkracht8.wearmusicplayer.R
import com.windkracht8.wearmusicplayer.data.Library
import com.windkracht8.wearmusicplayer.logD
import com.windkracht8.wearmusicplayer.logE
import com.windkracht8.wearmusicplayer.runInBackground
import com.windkracht8.wearmusicplayer.toast
import com.windkracht8.wearmusicplayer.tryIgnore
import com.windkracht8.wearmusicplayer.ui.PermissionsUtil.hasAllPermissions
import com.windkracht8.wearmusicplayer.ui.PermissionsUtil.hasBTPermission
import com.windkracht8.wearmusicplayer.ui.PermissionsUtil.hasReadPermission

class Home : ComponentActivity() {
	private val viewModel: HomeViewModel by viewModels()
	override fun onCreate(savedInstanceState: Bundle?) {
		installSplashScreen()
		super.onCreate(savedInstanceState)
		setContent {
			val uiState by viewModel.uiState.collectAsState()
			W8Theme(window, resources) {
				Surface {
					HomeScreen(
						uiState,
						::onIconClick,
						onOpenFolderClick = ::openFolder,
						viewModel::onItemIconClick,
						viewModel::dismissDeleteDialog,
						viewModel::confirmDeleteFile,
						viewModel::playlistCreate,
						viewModel::playlistRename,
						viewModel::playlistDelete,
						viewModel::playlistAddTrack,
						viewModel::playlistDelTrack
					)
				}
			}
		}
		if(!hasAllPermissions())
			startActivity(Intent(this, Permissions::class.java))
	}

	override fun onResume() {
		super.onResume()
		val hasBT = hasBTPermission()
		viewModel.onResume(hasBT, hasReadPermission())
		if(hasBT) registerReceiver(btBroadcastReceiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
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
		if(!hasAllPermissions()) startActivity(Intent(this, Permissions::class.java))
		else if(CommsBT.isConnected) CommsBT.stop()
		else startActivity(Intent(this, Device::class.java))
	}
	private fun openFolder() {
		try {
			openFolderResult.launch(null)
		} catch(e: Exception) {
			logE("Main.onOpenFolderClick: ${e.message}")
			toast(R.string.fail_open_folder_request)
		}
	}

	private val openFolderResult =
		registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
			logD { "Main.openFolderResult: ${uri?.path}" }
			val fullPath = uri?.path
			if(fullPath == null) {
				logE("Main.openFolderResult empty uri")
				toast(R.string.fail_open_folder)
			} else {
				val path = fullPath.removePrefix("/tree/primary:")
				Library.scanFiles(path)
			}
		}

	private val btBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent) {
			if(BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
				val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
				if(btState == BluetoothAdapter.STATE_TURNING_OFF) {
					CommsBT.onError(R.string.fail_BT_off)
					CommsBT.stop()
				} else if(btState == BluetoothAdapter.STATE_ON) {
					CommsBT.start(this@Home)
				}
			}
		}
	}
}
