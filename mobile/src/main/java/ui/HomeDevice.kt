/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.windkracht8.wearmusicplayer.ui

import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windkracht8.wearmusicplayer.CommsBT
import com.windkracht8.wearmusicplayer.CommsBT.Status.CONNECTED
import com.windkracht8.wearmusicplayer.CommsBT.Status.CONNECTING
import com.windkracht8.wearmusicplayer.CommsBT.Status.DISCONNECTED
import com.windkracht8.wearmusicplayer.CommsBT.Status.ERROR
import com.windkracht8.wearmusicplayer.CommsBT.Status.PAIRING
import com.windkracht8.wearmusicplayer.CommsBT.Status.STARTING
import com.windkracht8.wearmusicplayer.R

@Composable
fun HomeDevice(
	hasBTPermission: Boolean,
	freeSpace: String,
	btStatus: CommsBT.Status?,
	btDeviceName: String,
	btMessageStatus: Int,
	btError: Int,
	onIconClick: () -> Unit,
) {
	val iconAnimation = AnimatedImageVector.animatedVectorResource(R.drawable.watch_connecting)
	var iconAnimationAtEnd by remember { mutableStateOf(false) }
	Row(Modifier.fillMaxWidth().height(70.dp)) {
		Box(
			Modifier.size(70.dp),
			contentAlignment = Alignment.Center
		) {
			if(btStatus in listOf(CONNECTING, STARTING)) {
				Image(
					modifier = Modifier.size(70.dp).clickable { onIconClick() },
					painter = rememberAnimatedVectorPainter(
						iconAnimation,
						iconAnimationAtEnd
					),
					contentDescription = "watch icon"
				)
				iconAnimationAtEnd = true
			} else {
				Icon(
					modifier = Modifier.size(70.dp).clickable { onIconClick() },
					imageVector = ImageVector.vectorResource(R.drawable.watch),
					tint = when(btStatus) {
						DISCONNECTED, null -> colorScheme.onBackground.copy(alpha = 0.38f)
						ERROR -> colorScheme.error
						else -> colorScheme.onBackground
					},
					contentDescription = "watch icon"
				)
			}
			val colorOnBackground = colorScheme.onBackground
			BasicText(
				modifier = Modifier.padding(18.dp),
				text = freeSpace,
				color = { colorOnBackground },
				maxLines = 1,
				autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 20.sp)
			)
		}
		Column(Modifier.fillMaxWidth()) {
			Text(
				modifier = Modifier.fillMaxWidth(),
				text = when(btStatus) {
					DISCONNECTED -> stringResource(R.string.disconnected)
					PAIRING -> stringResource(R.string.pairing_with, btDeviceName)
					CONNECTING -> stringResource(R.string.connecting_to, btDeviceName)
					CONNECTED -> stringResource(R.string.connected_to, btDeviceName)
					STARTING, null ->
						if(hasBTPermission) stringResource(R.string.starting)
						else stringResource(R.string.no_permission)

					ERROR -> if(btError > 0) stringResource(btError) else ""
				},
				fontSize = 18.sp
			)
			Text(
				modifier = Modifier.fillMaxWidth(),
				text = if(btMessageStatus <= 0) "" else stringResource(btMessageStatus),
				fontSize = 14.sp
			)
		}
	}
}