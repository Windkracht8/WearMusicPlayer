/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.media.AudioManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.TextButton
import androidx.wear.tooling.preview.devices.WearDevices

@Composable
fun Home(
	onLibraryClick: () -> Unit,
	onTrackClick: () -> Unit,
	onPreviousClick: () -> Unit,
	onPlayPauseClick: () -> Unit,
	onNextClick: () -> Unit,
	hasPrevious: Boolean,
	hasNext: Boolean,
	isPlaying: Boolean,
	currentTrackTitle: String,
	currentTrackArtist: String,
	audioManager: AudioManager?
) {
	Column(modifier = Modifier.fillMaxSize().padding(10.dp, 10.dp, 10.dp, 0.dp)) {
		Row(modifier = Modifier.fillMaxWidth().weight(1F)) {
			Spacer(modifier = Modifier.weight(2F))
			IconButton(
				modifier = Modifier.weight(6F),
				onClick = {
					audioManager?.adjustVolume(
						AudioManager.ADJUST_LOWER,
						AudioManager.FLAG_SHOW_UI
					)
				}
			) {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.icon_volume_low),
					contentDescription = "volume down"
				)
			}
			IconButton(
				modifier = Modifier.weight(6F),
				onClick = {
					audioManager?.adjustVolume(
						AudioManager.ADJUST_RAISE,
						AudioManager.FLAG_SHOW_UI
					)
				}
			) {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.icon_volume),
					contentDescription = "volume up"
				)
			}
			Spacer(modifier = Modifier.weight(2F))
		}
		Row(modifier = Modifier.fillMaxWidth().weight(1F)) {
			IconButton(
				modifier = Modifier.weight(1F),
				onClick = onPreviousClick
			) {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.icon_previous),
					tint = if (hasPrevious) ColorWhite else ColorDisabled,
					contentDescription = "previous song"
				)
			}
			IconButton(
				modifier = Modifier.weight(1F),
				onClick = onPlayPauseClick
			) {
				Icon(
					imageVector = ImageVector.vectorResource(
						if (isPlaying) R.drawable.icon_pause
						else R.drawable.icon_play
					),
					tint = Color.Unspecified,
					contentDescription = "play or pause"
				)
			}
			IconButton(
				modifier = Modifier.weight(1F),
				onClick = onNextClick
			) {
				Icon(
					imageVector = ImageVector.vectorResource(R.drawable.icon_next),
					tint = if (hasNext) ColorWhite else ColorDisabled,
					contentDescription = "next song"
				)
			}
		}
		TextButton(
			modifier = Modifier.fillMaxWidth().weight(1F),
			onClick = onTrackClick
		) {
			BasicText(
				text = currentTrackTitle,
				color = { ColorW8Blue },
				maxLines = 2,
				autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 20.sp),
				style = TextStyle.Default.copy(textAlign = TextAlign.Center)
			)
		}
		TextButton(
			modifier = Modifier.fillMaxWidth().weight(1F).padding(horizontal = 5.dp),
			onClick = onTrackClick
		) {
			BasicText(
				text = currentTrackArtist,
				color = { ColorWhite },
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 20.sp)
			)
		}
		IconButton(
			modifier = Modifier.fillMaxWidth().weight(1F),
			onClick = onLibraryClick
		) {
			Icon(
				imageVector = ImageVector.vectorResource(R.drawable.icon_library),
				contentDescription = "open library",
			)
		}
	}
}

@Preview(device = WearDevices.LARGE_ROUND)
@Composable
fun PreviewHome() {
	W8Theme {
		Home(
			{}, {}, {}, {}, {},
			hasPrevious = false,
			hasNext = true,
			isPlaying = false,
			currentTrackTitle = "Track name",
			currentTrackArtist = "Artist name",
			audioManager = null
		)
	}
}
