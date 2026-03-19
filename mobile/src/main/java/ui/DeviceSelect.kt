/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.windkracht8.wearmusicplayer.CommsBT
import com.windkracht8.wearmusicplayer.R

@Composable
fun DeviceSelect(
	knownDevices: Set<CommsBT.Device>,
	bondedDevices: List<CommsBT.Device>?,
	actions: DeviceActions
) {
	var confirmDelDevice by remember { mutableStateOf<String?>(null) }

	LazyColumn(Modifier.fillMaxSize()) {
		if(knownDevices.isNotEmpty() && bondedDevices?.isEmpty() != false) {
			item { Text(
				modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
				text = stringResource(R.string.connect_instructions)
			) }
		}
		items(knownDevices.toList()) { device ->
			Row {
				Button(
					modifier = Modifier.weight(1f).height(60.dp).padding(10.dp),
					onClick = { actions.connectDevice(device) }
				) { Text(device.name) }
				IconButton(onClick = { confirmDelDevice = device.address }){
					Icon(
						imageVector = Icons.Default.Delete,
						contentDescription = stringResource(R.string.cd_delete)
					)
				}
			}
		}
		if(bondedDevices == null) {
			item {
				OutlinedButton(
					modifier = Modifier.fillMaxWidth().height(60.dp).padding(10.dp),
					onClick = actions::getBondedDevices
				) {
					Text(
						text = stringResource(R.string.device_select_new),
						color = colorScheme.onBackground
					)
				}
			}
		}
		bondedDevices?.let{ bondedDevices ->
			item { Text(
				modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
				text =
					if(bondedDevices.isEmpty()) stringResource(R.string.device_select_none)
					else stringResource(R.string.pair_instructions)
			) }
			items(bondedDevices.toList()) { device ->
				OutlinedButton(
					modifier = Modifier.fillMaxWidth().height(60.dp).padding(10.dp),
					onClick = { actions.connectDevice(device) }
				) { Text(device.name) }
			}
		}
		confirmDelDevice?.let {
			item {
				AlertDialog(
					title = { Text(R.string.delete_device) },
					onDismissRequest = { confirmDelDevice = null },
					confirmButton = {
						TextButton(
							onClick = {
								actions.delKnownDevice(it)
								confirmDelDevice = null
							}
						) { Text(R.string.delete) }
					},
					dismissButton = {
						TextButton(onClick = { confirmDelDevice = null }) { Text(R.string.cancel) }
					}
				)
			}
		}
	}
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PIXEL_8)
@Composable
fun PreviewDeviceSelect() {
	W8Theme(null, null) { Surface { DeviceSelect(
		knownDevices = setOf(
			CommsBT.Device("Watch 1", "", null)
		),
		bondedDevices = null,
		actions = DeviceActionsStub
	) } }
}
@Preview(device = Devices.PIXEL_8)
@Composable
fun PreviewDeviceSelectDay() { PreviewDeviceSelect() }
