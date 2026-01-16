/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.ProgressIndicatorDefaults
import androidx.wear.tooling.preview.devices.WearDevices
import kotlinx.coroutines.launch

class Progress : ComponentActivity() {
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setTheme(android.R.style.Theme_DeviceDefault)
		setContent { W8Theme { AppScaffold { ProgressScreen() } } }

		lifecycleScope.launch {
			CommsWifi.status.collect { wifiStatus ->
				logD{"ProgressActivity: CommsWifi status change: $wifiStatus"}
				when (wifiStatus) {
					CommsWifi.Status.ERROR, CommsWifi.Status.DONE -> finish()
					else -> {}
				}
			}
		}
	}
}

@Composable
fun ProgressScreen() {
	LocalActivity.current?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
	CircularProgressIndicator(
		modifier = Modifier.fillMaxSize(),
		colors = ProgressIndicatorDefaults.colors(
			indicatorColor = ColorW8Blue,
			trackColor = ColorBlack
		),
		progress = { CommsWifi.progress }
	)
	Column(
		modifier = Modifier.fillMaxSize().padding(10.dp, 10.dp, 10.dp, 0.dp),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	) {
		BasicText(
			text = stringResource( when (CommsWifi.connectionType) {
				CommsWifi.ConnectionType.REQUESTING, null -> R.string.requesting
				CommsWifi.ConnectionType.FAST -> R.string.fast
				CommsWifi.ConnectionType.SLOW -> R.string.slow
			}),
			color = { ColorWhite },
			maxLines = 1,
			autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 20.sp)
		)
		BasicText(
			modifier = Modifier.padding(horizontal = 10.dp),
			text = stringResource(
				R.string.receiving,
				CommsWifi.path.substringAfterLast('/')
			),
			color = { ColorWhite },
			maxLines = 2,
			autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 20.sp),
			style = TextStyle.Default.copy(textAlign = TextAlign.Center)
		)
	}
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
fun PreviewProgress() {
	CommsWifi.path = "Music/directory 1/track.mp3"
	CommsWifi.progress = 0.3F
	CommsWifi.connectionType = CommsWifi.ConnectionType.SLOW
	W8Theme { ProgressScreen() }
}
