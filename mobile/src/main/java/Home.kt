/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.content.res.Configuration
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun Home(
	commsBTStatus: CommsBT.Status?,
	showLoading: Boolean,
	onIconClick: () -> Unit,
	onOpenFolderClick: () -> Unit,
	onItemIconClick: (LibItem) -> Unit
) {
	val iconWatchConnecting =
		AnimatedImageVector.animatedVectorResource(R.drawable.icon_watch_connecting)
	var iconWatchConnectingAtEnd by remember { mutableStateOf(false) }

	Column(modifier = Modifier.fillMaxSize().safeContentPadding()) {
		Row(modifier = Modifier.fillMaxWidth().height(70.dp)) {
			Box(
				Modifier.size(70.dp),
				contentAlignment = Alignment.Center
			) {
				if(commsBTStatus in listOf(CommsBT.Status.CONNECTING, CommsBT.Status.STARTING)) {
					Image(
						modifier = Modifier.size(70.dp).clickable { onIconClick() },
						painter = rememberAnimatedVectorPainter(
							iconWatchConnecting,
							iconWatchConnectingAtEnd
						),
						contentDescription = "watch icon"
					)
					iconWatchConnectingAtEnd = true
				} else {
					Icon(
						modifier = Modifier.size(70.dp).clickable { onIconClick() },
						imageVector = ImageVector.vectorResource(R.drawable.icon_watch),
						tint = when(commsBTStatus) {
							CommsBT.Status.DISCONNECTED, null -> colorScheme.onBackground.copy(alpha = 0.38f)
							CommsBT.Status.ERROR -> colorScheme.error
							else -> colorScheme.onBackground
						},
						contentDescription = "watch icon"
					)
				}
				val colorOnBackground = colorScheme.onBackground
				BasicText(
					modifier = Modifier.padding(18.dp),
					text = CommsBT.freeSpace,
					color = { colorOnBackground },
					maxLines = 1,
					autoSize = TextAutoSize.StepBased(minFontSize = 6.sp, maxFontSize = 20.sp)
				)
			}
			Column(modifier = Modifier.fillMaxWidth()) {
				Text(
					modifier = Modifier.fillMaxWidth(),
					text = when(commsBTStatus) {
						CommsBT.Status.DISCONNECTED ->
							stringResource(R.string.disconnected)
						CommsBT.Status.CONNECTING ->
							stringResource(R.string.connecting_to, CommsBT.deviceName)
						CommsBT.Status.CONNECTED ->
							stringResource(R.string.connected_to, CommsBT.deviceName)
						CommsBT.Status.STARTING, null ->
							if(Permissions.hasBT) stringResource(R.string.starting)
							else stringResource(R.string.no_permission)
						CommsBT.Status.ERROR -> stringResource(CommsBT.error)
					},
					fontSize = 18.sp
				)
				Text(
					modifier = Modifier.fillMaxWidth(),
					text =
						if(CommsBT.messageStatus <= 0) ""
						else stringResource(CommsBT.messageStatus),
					fontSize = 14.sp
				)
			}
		}
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.End
		) {
			OutlinedButton(onClick = onOpenFolderClick) { Text(R.string.open_folder) }
		}
		if(showLoading) {
			Text(
				modifier = Modifier.fillMaxWidth(),
				text = stringResource(id =
					if(Permissions.hasRead) R.string.loading
					else R.string.no_permission
				),
				fontSize = 24.sp,
				textAlign = TextAlign.Center
			)
		} else {
			LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
				Library.rootLibDir.libDirs.forEachIndexed { i, it ->
					item { Item(it, i > 0, onItemIconClick) }
				}
				Library.rootLibDir.libTracks.forEachIndexed { i, it ->
					item { Item(it, i == 0, onItemIconClick) }
				}
			}
			if(Library.watchLibDir.status == LibItem.Status.NOT) {
				HorizontalDivider(
					modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
					color = colorScheme.onBackground
				)
				Text(
					modifier = Modifier.fillMaxWidth(),
					text = stringResource(R.string.watch_tracks),
					fontSize = 16.sp,
					textAlign = TextAlign.Center
				)
				HorizontalDivider(
					modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
					color = colorScheme.onBackground
				)
				LazyColumn(modifier = Modifier.fillMaxWidth().weight(0.5f)) {
					Library.watchLibDir.libDirs.forEachIndexed { i, it ->
						if(it.status != LibItem.Status.NOT) return@forEachIndexed
						item { Item(it, i > 0, onItemIconClick) }
					}
					Library.watchLibDir.libTracks.forEachIndexed { i, it ->
						if(it.status == LibItem.Status.NOT) return@forEachIndexed
						item { Item(it, i == 0, onItemIconClick) }
					}
				}
			}
		}
	}
}
@Composable
fun Item(
	libItem: LibItem,
	showDivider: Boolean,
	onItemIconClick: (LibItem) -> Unit
) {
	var showSubItems by remember { mutableStateOf(false) }
	if(showDivider) {
		HorizontalDivider(
			modifier = Modifier.fillMaxWidth().padding(start = (libItem.depth * 7 + 48).dp),
			thickness = 1.dp
		)
	}
	Row(modifier = Modifier.fillMaxWidth().padding(start = (libItem.depth * 7).dp)) {
		Box(
			Modifier.size(48.dp),
			contentAlignment = Alignment.Center
		) {
			when(libItem.status) {
				LibItem.Status.PENDING -> {
					CircularProgressIndicator(
						trackColor = colorScheme.onBackground.copy(alpha = 0.2f)
					)
				}
				LibItem.Status.SENDING -> {
					CircularProgressIndicator(
						trackColor = colorScheme.onBackground.copy(alpha = 0.2f),
						progress = { libItem.progress / 100F }
					)
				}
				else -> {
					IconButton(
						modifier = Modifier.size(48.dp),
						onClick = { onItemIconClick(libItem) }
					) {
						Icon(
							imageVector = ImageVector.vectorResource(id =
								if(libItem is LibDir) {
									when(libItem.status) {
										LibItem.Status.FULL -> R.drawable.icon_full
										LibItem.Status.PARTIAL -> R.drawable.icon_partial
										else -> R.drawable.icon_empty
									}
								} else {
									when(libItem.status) {
										LibItem.Status.FULL -> R.drawable.icon_delete
										LibItem.Status.NOT -> R.drawable.icon_upload
										else -> R.drawable.icon_empty
									}
								}
							),
							contentDescription = "Item icon and action button"
						)
					}
				}
			}
		}
		TextButton(
			modifier = Modifier.fillMaxWidth(),
			onClick = { if(libItem is LibDir) { showSubItems = !showSubItems } }
		) {
			Text(
				modifier = Modifier.fillMaxWidth(),
				text = libItem.name,
				color = colorScheme.onBackground,
				fontSize = 14.sp,
				textAlign = TextAlign.Start
			)
		}
	}
	if(libItem is LibDir && showSubItems) {
		Column {
			libItem.libDirs.forEach { Item(it, true, onItemIconClick) }
			libItem.libTracks.forEach { Item(it, false, onItemIconClick) }
		}
	}
}
@Composable
fun Text(text: Int) = Text(stringResource(text))
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, apiLevel = 35)
@Composable
fun PreviewHome() {
	val dir1 = LibDir("dir1")
	dir1.status = LibItem.Status.PARTIAL
	Library.rootLibDir.libDirs.add(dir1)
	val dir2 = LibDir("dir2")
	dir2.status = LibItem.Status.FULL
	Library.rootLibDir.libDirs.add(dir2)
	val track1 = LibTrack("track1")
	track1.status = LibItem.Status.SENDING
	track1.progress = 35
	Library.rootLibDir.libTracks.add(track1)
	val track2 = LibTrack("track2")
	track2.status = LibItem.Status.FULL
	Library.rootLibDir.libTracks.add(track2)
	Library.rootLibDir.libTracks.add(track2)
	Library.rootLibDir.libTracks.add(track2)
	Library.rootLibDir.libTracks.add(track2)
	Library.rootLibDir.libTracks.add(track2)
	Library.rootLibDir.libTracks.add(track2)
	Library.rootLibDir.libTracks.add(track2)
	Library.rootLibDir.libTracks.add(track2)
	Library.rootLibDir.libTracks.add(track2)
	Library.rootLibDir.libTracks.add(track2)

	Library.watchLibDir.status = LibItem.Status.NOT
	Library.watchLibDir.libTracks.add(track2)

	CommsBT.deviceName = "Test watch"
	CommsBT.freeSpace = " 10 GB"
	CommsBT.messageStatus = R.string.sync_done
	W8Theme {
		Surface {
			Home(
				commsBTStatus = CommsBT.Status.CONNECTING,
				showLoading = false,
				onIconClick = {},
				onOpenFolderClick = {},
				onItemIconClick = {}
			)
		}
	}
}
@Preview(uiMode = Configuration.UI_MODE_NIGHT_NO, apiLevel = 35)
@Composable
fun PreviewHomeDay() {
	W8Theme {
		Surface {
			Home(
				commsBTStatus = CommsBT.Status.CONNECTING,
				showLoading = false,
				onIconClick = {},
				onOpenFolderClick = {},
				onItemIconClick = {}
			)
		}
	}
}