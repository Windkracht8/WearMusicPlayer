/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer.ui

import android.content.res.Configuration
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windkracht8.wearmusicplayer.CommsBT.Status.CONNECTED
import com.windkracht8.wearmusicplayer.CommsBT.Status.CONNECTING
import com.windkracht8.wearmusicplayer.CommsBT.Status.DISCONNECTED
import com.windkracht8.wearmusicplayer.CommsBT.Status.ERROR
import com.windkracht8.wearmusicplayer.CommsBT.Status.PAIRING
import com.windkracht8.wearmusicplayer.CommsBT.Status.STARTING
import com.windkracht8.wearmusicplayer.R

@Composable
fun DeviceScreen(uiState: DeviceUiState, actions: DeviceActions) {
	val iconAnimation = AnimatedImageVector.animatedVectorResource(R.drawable.watch_connecting)
	var iconAnimationAtEnd by remember { mutableStateOf(false) }
	Column(Modifier.fillMaxSize().safeDrawingPadding()) {
		Row(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
			if(uiState.btStatus in listOf(CONNECTING, STARTING)) {
				IconButton(actions::onIconClick, Modifier.size(70.dp)) {
					Image(
						painter = rememberAnimatedVectorPainter(iconAnimation, iconAnimationAtEnd),
						contentDescription = stringResource(R.string.cd_watch_icon)
					)
					iconAnimationAtEnd = true
				}
			} else {
				IconButton(actions::onIconClick, Modifier.size(70.dp)) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.watch),
						tint = when(uiState.btStatus) {
							DISCONNECTED, null -> colorScheme.outline
							ERROR -> colorScheme.error
							else -> colorScheme.onBackground
						},
						contentDescription = stringResource(R.string.cd_watch_icon)
					)
				}
			}
			Column(Modifier.fillMaxWidth()) {
				Text(
					modifier = Modifier.fillMaxWidth(),
					text = when(uiState.btStatus) {
						DISCONNECTED -> stringResource(R.string.disconnected)
						PAIRING -> stringResource(R.string.pairing_with, uiState.deviceName)
						CONNECTING -> stringResource(R.string.connecting_to, uiState.deviceName)
						CONNECTED -> stringResource(R.string.connected_to, uiState.deviceName)
						STARTING, null -> stringResource(R.string.starting)
						ERROR -> stringResource(uiState.error)
					},
					fontSize = 18.sp
				)
				Text(
					modifier = Modifier.fillMaxWidth(),
					text = if(uiState.messageStatus <= 0) "" else stringResource(uiState.messageStatus),
					fontSize = 14.sp
				)
			}
		}
		when(uiState.btStatus) {
			DISCONNECTED -> DeviceSelect(
				uiState.knownDevices,
				uiState.bondedDevices,
				actions
			)
			PAIRING ->
				Text(
					modifier = Modifier.padding(top = 10.dp),
					text = stringResource(
						R.string.pair_confirm_code,
						uiState.pairCode
					),
					textAlign = TextAlign.Center
				)
			else -> {}
		}
	}
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PIXEL_8)
@Composable
fun PreviewDeviceScreen() {
	W8Theme(null, null) { Surface { DeviceScreen(
		uiState = DeviceUiState(
			btStatus = DISCONNECTED,
			pairCode = "123 456",
			deviceName = "Test watch",
			messageStatus = R.string.sync,
			error = -1
		),
		actions = DeviceActionsStub
	) } }
}
@Preview(device = Devices.PIXEL_8)
@Composable
fun PreviewDeviceScreenDay() { PreviewDeviceScreen() }
