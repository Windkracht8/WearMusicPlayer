/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.media.AudioManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.filled.KeyboardDoubleArrowRight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.edgeSwipeToDismiss
import androidx.wear.compose.foundation.rememberSwipeToDismissBoxState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.SwipeToDismissBox
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.tooling.preview.devices.WearDevices
import java.util.Locale
import kotlinx.coroutines.delay

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
	currentPosition: Long,
	currentDuration: Long,
	seek: (Long) -> Unit,
	audioManager: AudioManager?,
	playbackSpeed: Float,
	setPlaybackSpeed: (Float) -> Unit
) {
	var showProgress by remember { mutableStateOf(false) }
	var buttonPressCounter by remember { mutableIntStateOf(0) }
	var currentProgress by remember { mutableFloatStateOf(0f) }
	var currentPositionString by remember { mutableStateOf("") }
	LaunchedEffect(currentPosition, currentDuration) {
		currentProgress = if (currentDuration <= 0) 0f
			else currentPosition.toFloat() / currentDuration.toFloat()
		currentPositionString = "${currentPosition / 60000}:${(currentPosition / 1000 % 60).toString().padStart(2, '0')}"
	}
	Column(Modifier.fillMaxSize()
		.padding(10.dp, 10.dp, 10.dp, 0.dp)) {
		Row(Modifier.fillMaxWidth().weight(1F)) {
			Spacer(Modifier.weight(2F))
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
					imageVector = Icons.AutoMirrored.Default.VolumeDown,
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
					imageVector = Icons.AutoMirrored.Default.VolumeUp,
					contentDescription = "volume up"
				)
			}
			Spacer(Modifier.weight(2F))
		}
		Row(Modifier.fillMaxWidth().weight(1F)) {
			IconButton(
				modifier = Modifier.weight(1F),
				onClick = onPreviousClick
			) {
				Icon(
					modifier = Modifier.fillMaxSize(),
					imageVector = Icons.Default.SkipPrevious,
					tint = if (hasPrevious) ColorWhite else ColorDisabled,
					contentDescription = "previous song"
				)
			}
			PlayButton(
				modifier = Modifier.weight(1F),
				onPlayPauseClick = onPlayPauseClick,
				isPlaying
			)
			IconButton(
				modifier = Modifier.weight(1F),
				onClick = onNextClick
			) {
				Icon(
					modifier = Modifier.fillMaxSize(),
					imageVector = Icons.Default.SkipNext,
					tint = if (hasNext) ColorWhite else ColorDisabled,
					contentDescription = "next song"
				)
			}
		}
		Column(
			Modifier.fillMaxWidth().weight(1F)
				.clickable(onClick = { showProgress = true }),
			horizontalAlignment = Alignment.CenterHorizontally
		) {
			LinearProgressIndicator(
				modifier = Modifier
					.fillMaxWidth()
					.padding(5.dp)
					.weight(1F),
				progress = { currentProgress }
			)
			BasicText(
				modifier = Modifier.weight(0.8F),
				text = currentPositionString,
				color = { ColorWhite },
				autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 20.sp)
			)
		}
		TextButton(
			modifier = Modifier
				.fillMaxWidth()
				.weight(0.6F),
			onClick = onTrackClick
		) {
			BasicText(
				text = currentTrackTitle,
				color = { ColorW8Blue },
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 20.sp)
			)
		}
		TextButton(
			modifier = Modifier
				.fillMaxWidth()
				.weight(0.6F)
				.padding(horizontal = 15.dp),
			onClick = onTrackClick
		) {
			BasicText(
				text = currentTrackArtist,
				color = { ColorWhite },
				maxLines = 1,
				overflow = TextOverflow.Ellipsis,
				autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 20.sp)
			)
		}
		IconButton(
			modifier = Modifier.fillMaxWidth().weight(1F),
			onClick = onLibraryClick
		) {
			Icon(
				modifier = Modifier.fillMaxSize(),
				imageVector = Icons.AutoMirrored.Default.QueueMusic,
				contentDescription = "open library",
			)
		}
	}
	if(showProgress){
		val swipeToDismissBoxState = rememberSwipeToDismissBoxState()
		SwipeToDismissBox(
			state = swipeToDismissBoxState,
			onDismissed = { showProgress = false },
			modifier = Modifier.edgeSwipeToDismiss(swipeToDismissBoxState)
		) {
			LaunchedEffect(buttonPressCounter) {
				delay(10000)
				showProgress = false
			}
			Column(
				modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 20.dp),
				horizontalAlignment = Alignment.CenterHorizontally
			) {
				Row(
					Modifier.fillMaxWidth().weight(1F),
					verticalAlignment = Alignment.CenterVertically
				) {
					Box(
						modifier = Modifier.weight(1F)
							.clickable {
								buttonPressCounter++
								if(playbackSpeed > 1.0) setPlaybackSpeed(playbackSpeed - 0.25f)
								else if(playbackSpeed > 0.2) setPlaybackSpeed(playbackSpeed - 0.1f)
							},
						contentAlignment = Alignment.CenterEnd
					){ Text(text = if(playbackSpeed > 1.0) "- .25" else "- .1") }
					Text(
						modifier = Modifier.padding(horizontal = 10.dp),
						text = String.format(Locale.ROOT, "%.2fx", playbackSpeed),
						color = ColorW8Blue
					)
					Box(
						modifier = Modifier.weight(1F)
							.clickable {
								buttonPressCounter++
								if(playbackSpeed >= 1.0) setPlaybackSpeed(playbackSpeed + 0.25f)
								else setPlaybackSpeed(playbackSpeed + 0.1f)
							},
						contentAlignment = Alignment.CenterStart
					){ Text(text = if(playbackSpeed >= 1.0) "+ .25" else "+ .1") }
				}
				LinearProgressIndicator(
					modifier = Modifier.fillMaxWidth().padding(5.dp).weight(1F)
						.pointerInput(currentDuration, currentPosition) {
							detectTapGestures { offset ->
									if (currentDuration > 0) {
										val newProgress = (offset.x / size.width).coerceIn(0f, 1f)
										val newPosition = (newProgress * currentDuration).toLong()
										seek(newPosition - currentPosition)
										buttonPressCounter++
									}
								}
						},
					progress = { currentProgress }
				)
				BasicText(
					modifier = Modifier.fillMaxWidth().weight(0.5F),
					text = currentPositionString,
					style = TextStyle(textAlign = TextAlign.Center),
					color = { ColorWhite },
					autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 20.sp)
				)
				Row(
					Modifier.fillMaxWidth().weight(1F),
					verticalAlignment = Alignment.CenterVertically
				) {
					IconButton(
						modifier = Modifier.weight(1F),
						onClick = {
							seek(-120000)
							buttonPressCounter++
						}
					) {
						Icon(modifier = Modifier.fillMaxSize(),
							imageVector = Icons.Default.FastRewind,
							contentDescription = "back 2 minutes",
						)
					}
					Text(
						text = "2 min",
						color = ColorW8Blue
					)
					IconButton(
						modifier = Modifier.weight(1F),
						onClick = {
							seek(120000)
							buttonPressCounter++
						}
					) {
						Icon(modifier = Modifier.fillMaxSize(),
							imageVector = Icons.Default.FastForward,
							contentDescription = "forward 2 minutes",
						)
					}
				}
				Row(
					Modifier.fillMaxWidth().weight(1F),
					verticalAlignment = Alignment.CenterVertically
				) {
					Box(
						modifier = Modifier.weight(1F)
							.clickable {
								seek(-30000)
								buttonPressCounter++
							},
						contentAlignment = Alignment.CenterEnd
					) {
						Icon(
							modifier = Modifier.fillMaxSize(),
							imageVector = Icons.Default.KeyboardDoubleArrowLeft,
							contentDescription = "back 30 seconds",
						)
					}
					Text(
						text = "30 sec",
						color = ColorW8Blue
					)
					Box(
						modifier = Modifier.weight(1F)
							.clickable {
								seek(30000)
								buttonPressCounter++
							},
						contentAlignment = Alignment.CenterStart
					) {
						Icon(
							modifier = Modifier.fillMaxSize(),
							imageVector = Icons.Default.KeyboardDoubleArrowRight,
							contentDescription = "forward 30 seconds",
						)
					}
				}
			}
		}
	}
}

@Composable
fun PlayButton(
	modifier: Modifier,
	onPlayPauseClick: () -> Unit,
	isPlaying: Boolean
){
	IconButton(
		onClick = onPlayPauseClick,
		modifier
	) {
		Icon(
			modifier = Modifier.fillMaxSize(),
			imageVector = if(isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
			contentDescription = "play or pause"
		)
	}
}

@Preview(device = WearDevices.SMALL_ROUND)
@Composable
fun PreviewHome() {
	W8Theme {
		Home(
			{}, {}, {}, {}, {},
			hasPrevious = false,
			hasNext = true,
			isPlaying = false,
			currentTrackTitle = "Ding Dong Song",
			currentTrackArtist = "Gunther",
			currentPosition = 90000,
			currentDuration = 200000,
			seek = {},
			audioManager = null,
			playbackSpeed = 1.0f,
			setPlaybackSpeed = {}
		)
	}
}
@Preview(device = WearDevices.LARGE_ROUND)
@Composable
fun PreviewHomeLarge() { PreviewHome() }
