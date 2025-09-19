/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class DeviceConnect : ComponentActivity() {
	public override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if(CommsBT.status.value != CommsBT.Status.CONNECTING) finishAndRemoveTask()
		lifecycleScope.launch {
			CommsBT.status.collect {
				if(it != CommsBT.Status.CONNECTING) finishAndRemoveTask()
			}
		}
		enableEdgeToEdge()
		setContent { W8Theme { Surface { DeviceConnectScreen() } } }
	}
}
@Composable
fun DeviceConnectScreen() {
	val iconWatchConnecting =
		AnimatedImageVector.animatedVectorResource(R.drawable.icon_watch_connecting)
	var iconWatchConnectingAtEnd by remember { mutableStateOf(false) }
	Column(Modifier.fillMaxSize().safeContentPadding()) {
		Text(
			modifier = Modifier.fillMaxWidth(),
			text = stringResource(R.string.connecting_to, CommsBT.deviceName),
			textAlign = TextAlign.Center,
			color = colorScheme.primary,
			fontSize = 20.sp,
			fontWeight = FontWeight.Bold
		)
		Text(
			modifier = Modifier.fillMaxWidth(),
			text = stringResource(R.string.device_connect_instruct),
			textAlign = TextAlign.Center,
			fontSize = 14.sp,
			fontWeight = FontWeight.Bold
		)
		Image(
			modifier = Modifier.fillMaxSize(),
			painter = rememberAnimatedVectorPainter(
				iconWatchConnecting,
				iconWatchConnectingAtEnd
			),
			contentDescription = "watch icon"
		)
		iconWatchConnectingAtEnd = true
	}
}
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, apiLevel = 35)
@Composable
fun PreviewDeviceConnect() {
	CommsBT
	W8Theme { Surface { DeviceConnectScreen() } }
}
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, apiLevel = 35)
@Composable
fun PreviewDeviceConnectDay() {
	W8Theme { Surface { DeviceConnectScreen() } }
}
