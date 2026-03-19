/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.windkracht8.wearmusicplayer.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.tooling.preview.devices.WearDevices
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding
import com.windkracht8.wearmusicplayer.CommsBT
import com.windkracht8.wearmusicplayer.R
import com.windkracht8.wearmusicplayer.runInBackground
import com.windkracht8.wearmusicplayer.ui.ColorError
import com.windkracht8.wearmusicplayer.ui.ColorW8
import com.windkracht8.wearmusicplayer.ui.ConfirmDialog
import com.windkracht8.wearmusicplayer.ui.Text
import com.windkracht8.wearmusicplayer.ui.W8Theme
import kotlinx.coroutines.delay

@Composable
fun MenuBT(
	btEnabled: Boolean?,
	onBTEnableClick: () -> Unit
){
	val currentView = LocalView.current
	val columnState = rememberTransformingLazyColumnState()
	val transformationSpec = rememberTransformationSpec()
	var confirmDelete by remember { mutableStateOf<String?>(null) }

	LaunchedEffect(CommsBT.pairing) {
		if(CommsBT.pairing) {
			currentView.keepScreenOn = true
			delay(180000L)
			CommsBT.pairing = false
			CommsBT.pairCode = null
			currentView.keepScreenOn = false
		}
	}
	DisposableEffect(Unit) {
		onDispose {
			CommsBT.pairing = false
			CommsBT.pairCode = null
			currentView.keepScreenOn = false
		}
	}

	ScreenScaffold(
		modifier = Modifier,
		scrollState = columnState,
		contentPadding = rememberResponsiveColumnPadding()
	) { contentPadding ->
		TransformingLazyColumn(state = columnState, contentPadding = contentPadding) {
			item { Text(R.string.BT) }
			item { OutlinedButton(
				modifier = Modifier.fillMaxWidth(),
				transformation = SurfaceTransformation(transformationSpec),
				onClick = onBTEnableClick
			) {
				Column {
					Text(R.string.BT)
					if(btEnabled == null) Text(R.string.permission_denied, ColorError)
					else if(btEnabled) Text(R.string.enabled, ColorW8)
					else Text(R.string.disabled, ColorW8)
				}
			} }
			if(btEnabled == true) {
				item { OutlinedButton(
					modifier = Modifier.fillMaxWidth(),
					transformation = SurfaceTransformation(transformationSpec),
					onClick = {
						CommsBT.pairCode = null
						CommsBT.pairing = !CommsBT.pairing
					}
				) {
					Column {
						Text(R.string.pair_phone)
						if(CommsBT.pairing) Text(R.string.enabled, ColorW8)
					}
				} }
			}
			if(CommsBT.knownDeviceNames.isNotEmpty()) {
				item { Text(
					modifier = Modifier.padding(vertical = 5.dp),
					text = stringResource(R.string.paired_phones),
					color = ColorW8
				) }
			}
			CommsBT.knownDeviceNames.forEach { (address, deviceName) ->
				item { OutlinedButton(
					modifier = Modifier.fillMaxWidth(),
					transformation = SurfaceTransformation(transformationSpec),
					onClick = { confirmDelete = address }
				) { Text(deviceName) } }
			}
		}
		if(CommsBT.pairing) {
			CommsBT.pairCode?.let { pairCode ->
				ConfirmDialog(
					textString = stringResource(R.string.accept_pair_code, pairCode),
					confirmText = R.string.accept,
					dismissText = R.string.reject,
					onConfirm = {
						CommsBT.pairing = false
						runInBackground { CommsBT.acceptPairRequest() }
					},
					onDismiss = {
						CommsBT.pairing = false
						runInBackground { CommsBT.rejectPairRequest() }
					}
				)
			}
		}
		confirmDelete?.let{
			ConfirmDialog(
				text = R.string.unlink_phone,
				confirmText = R.string.delete,
				dismissText = R.string.cancel,
				onConfirm = {
					confirmDelete = null
					CommsBT.delKnownDevice(it)
				},
				onDismiss = { confirmDelete = null }
			)
		}
	}
}

@Preview(device = WearDevices.SMALL_ROUND)
@Composable
fun PreviewSettingsBT(){
	CommsBT.knownDeviceNames["address"] = "Some phone"
	W8Theme { MenuBT(
		btEnabled = true,
		onBTEnableClick = {}
	) }
}
@Preview(device = WearDevices.LARGE_ROUND)
@Composable
fun PreviewSettingsBTLarge() { PreviewSettingsBT() }
