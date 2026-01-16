/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@SuppressLint("MissingPermission") //Handled by Permissions
class DeviceSelect : ComponentActivity() {
	var showNewDevices by mutableStateOf(false)
	var bondedDevices: Set<BluetoothDevice>? = null
	public override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if(!Permissions.hasBT) finishAndRemoveTask()

		lifecycleScope.launch {
			CommsBT.status.collect { status ->
				logD{"DeviceSelect: CommsBT status change: $status"}
				when(status) {
					CommsBT.Status.CONNECTING -> {
						startActivity(Intent(this@DeviceSelect, DeviceConnect::class.java))
					}
					CommsBT.Status.CONNECTED, CommsBT.Status.ERROR -> finishAndRemoveTask()
					CommsBT.Status.STARTING, CommsBT.Status.DISCONNECTED, null -> {}
				}
			}
		}

		setContent {
			W8Theme (window, resources) {
				Surface {
					DeviceSelectScreen(
						onDeviceClick = ::onDeviceClick,
						onNewDeviceClick = ::onNewDeviceClick,
						showNewDevices = showNewDevices,
						bondedDevices = bondedDevices
					)
				}
			}
		}
	}

	fun onDeviceClick(device: BluetoothDevice) {
		logD{"onDeviceClick: ${device.name}"}
		runInBackground { CommsBT.connectDevice(device) }
	}

	fun onNewDeviceClick() {
		logD{"onNewDeviceClick"}
		runInBackground {
			bondedDevices = CommsBT.getBondedDevices()
			showNewDevices = true
		}
	}
}
@SuppressLint("MissingPermission") //Handled by Permissions
@Composable
fun DeviceSelectScreen(
	onDeviceClick: (BluetoothDevice) -> Unit,
	onNewDeviceClick: () -> Unit,
	showNewDevices: Boolean,
	bondedDevices: Set<BluetoothDevice>?
) {
	val longPressTimeoutMillis = LocalViewConfiguration.current.longPressTimeoutMillis
	var confirmDelDevice by remember { mutableStateOf("") }
	var showNewWatch by remember { mutableStateOf(true) }
	LazyColumn(Modifier.fillMaxSize().safeDrawingPadding()) {
		item {
			Text(
				modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
				text = stringResource(R.string.device_select_title),
				fontSize = 20.sp,
				textAlign = TextAlign.Center
			)
		}
		items(CommsBT.knownDevices.toList()) { device ->
			var isShort = true
			val interactionSource = remember { MutableInteractionSource() }
			LaunchedEffect(interactionSource) {
				interactionSource.interactions.collectLatest { interaction ->
					when(interaction) {
						is PressInteraction.Press -> {
							isShort = true
							delay(longPressTimeoutMillis)
							isShort = false
							confirmDelDevice = device.address
						}
						is PressInteraction.Release -> if(isShort) onDeviceClick(device)
					}
				}
			}
			Button(
				modifier = Modifier.fillMaxWidth().height(60.dp).padding(10.dp),
				interactionSource = interactionSource,
				onClick = {}
			) { Text(device.name ?: "<no name>") }
		}
		if(showNewWatch) {
			item {
				OutlinedButton(
					modifier = Modifier.fillMaxWidth().height(60.dp).padding(10.dp),
					onClick = {
						showNewWatch = false
						onNewDeviceClick()
					}
				) { Text(R.string.device_select_new) }
			}
		}
		if(showNewDevices && bondedDevices != null) {
			if(bondedDevices.isEmpty()) {
				item { Text(R.string.device_select_none) }
			}
			items(bondedDevices.toList()) { device ->
				OutlinedButton(
					modifier = Modifier.fillMaxWidth().height(60.dp).padding(10.dp),
					onClick = { onDeviceClick(device) }
				) { Text(device.name ?: "<no name>") }
			}
		}
		if(confirmDelDevice.isNotEmpty()) {
			item {
				AlertDialog(
					title = { Text(R.string.delete_device) },
					onDismissRequest = { confirmDelDevice = "" },
					confirmButton = {
						TextButton(
							onClick = {
								CommsBT.delKnownAddress(confirmDelDevice)
								confirmDelDevice = ""
							}
						) { Text(R.string.delete) }
					},
					dismissButton = {
						TextButton(
							onClick = { confirmDelDevice = "" }
						) { Text(R.string.cancel) }
					}
				)
			}
		}
	}
}
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, apiLevel = 35)
@Composable
fun PreviewDeviceSelect() {
	CommsBT
	W8Theme (null, null) {
		Surface {
			DeviceSelectScreen(
				{}, {}, false, null
			)
		}
	}
}
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, apiLevel = 35)
@Composable
fun PreviewDeviceSelectDay() { PreviewDeviceSelect() }
