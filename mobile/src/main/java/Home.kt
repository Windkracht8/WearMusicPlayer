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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
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
	var showPlaylists by remember { mutableStateOf(false) }
	var showWatchTracks by remember { mutableStateOf(false) }
	var columnSize by remember { mutableStateOf(IntSize.Zero) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().onSizeChanged { columnSize = it }) {
		val maxSectionHeight = with(LocalDensity.current) { (columnSize.height / 4).toDp() }
		Row(Modifier.fillMaxWidth().height(70.dp)) {
			Box(
				Modifier.size(70.dp),
				contentAlignment = Alignment.Center
			) {
				if(commsBTStatus in listOf(CommsBT.Status.CONNECTING, CommsBT.Status.STARTING)) {
					Image(
						modifier = Modifier.size(70.dp)
							.clickable { onIconClick() },
						painter = rememberAnimatedVectorPainter(
							iconWatchConnecting,
							iconWatchConnectingAtEnd
						),
						contentDescription = "watch icon"
					)
					iconWatchConnectingAtEnd = true
				} else {
					Icon(
						modifier = Modifier.size(70.dp)
							.clickable { onIconClick() },
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
			Column(Modifier.fillMaxWidth()) {
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
			LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
				itemsIndexed(Library.rootLibDir.libDirs) { i, it ->
					Item(it, i > 0, onItemIconClick, showPlaylists)
				}
				itemsIndexed(Library.rootLibDir.libTracks) { i, it ->
					Item(it, i == 0, onItemIconClick, showPlaylists)
				}
			}
			HorizontalDivider(
				modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
				color = colorScheme.onBackground
			)
			TextButton(
				modifier = Modifier.fillMaxWidth(),
				onClick = { showPlaylists = !showPlaylists }
			){ Text(
				text = stringResource(R.string.playlists_title),
				fontSize = 16.sp,
				color = colorScheme.onBackground
			) }
			HorizontalDivider(
				modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
				color = colorScheme.onBackground
			)
			if(showPlaylists) {
				LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxSectionHeight)) {
					item { PlaylistsCreateRow() }
					items(Playlists.all) { PlaylistRow(it) }
				}
			}
			if(Library.watchLibDir.status == LibItem.Status.NOT) {
				HorizontalDivider(
					modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
					color = colorScheme.onBackground
				)
				TextButton(
					modifier = Modifier.fillMaxWidth(),
					onClick = { showWatchTracks = !showWatchTracks }
				){ Text(
					text = stringResource(R.string.watch_tracks),
					fontSize = 16.sp,
					color = colorScheme.onBackground
				) }
				HorizontalDivider(
					modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
					color = colorScheme.onBackground
				)
				if(showWatchTracks) {
					LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxSectionHeight)) {
						itemsIndexed(Library.watchLibDir.libDirs.filter { it.status == LibItem.Status.NOT }) { i, it ->
							Item(it, i > 0, onItemIconClick, showPlaylists)
						}
						itemsIndexed(Library.watchLibDir.libTracks.filter { it.status != LibItem.Status.NOT }) { i, it ->
							Item(it, i == 0, onItemIconClick, showPlaylists)
						}
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
	onItemIconClick: (LibItem) -> Unit,
	showPlaylists: Boolean = true
) {
	var showSubItems by remember { mutableStateOf(false) }
	if(showDivider) {
		HorizontalDivider(
			modifier = Modifier.fillMaxWidth().padding(start = (libItem.depth * 7 + 48).dp),
			thickness = 1.dp
		)
	}
	Row(Modifier.fillMaxWidth().padding(start = (libItem.depth * 7).dp)) {
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
							imageVector =
								if(libItem is LibDir) {
									when(libItem.status) {
										LibItem.Status.FULL -> ImageVector.vectorResource(R.drawable.icon_full)
										LibItem.Status.PARTIAL -> ImageVector.vectorResource(R.drawable.icon_partial)
										else -> ImageVector.vectorResource(R.drawable.icon_empty)
									}
								} else {
									when(libItem.status) {
										LibItem.Status.FULL -> Icons.Default.Delete
										LibItem.Status.NOT -> ImageVector.vectorResource(R.drawable.icon_upload)
										else -> ImageVector.vectorResource(R.drawable.icon_empty)
									}
								},
							contentDescription = "Item icon and action button"
						)
					}
				}
			}
		}
		TextButton(
			modifier = Modifier.weight(1f),
			onClick = { if(libItem is LibDir) { showSubItems = !showSubItems } }
		) {
			Text(
				modifier = Modifier.weight(1f),
				text = libItem.name,
				color = colorScheme.onBackground,
				fontSize = 14.sp,
				textAlign = TextAlign.Start
			)
		}
		if(libItem is LibTrack && libItem.status == LibItem.Status.FULL && showPlaylists) {
			var menuOpen by remember { mutableStateOf(false) }
			Box {
				IconButton(
					modifier = Modifier.size(48.dp),
					onClick = { menuOpen = true }
				) {
					Icon(
						imageVector = ImageVector.vectorResource(R.drawable.icon_add_to_playlist),
						contentDescription = "Add to playlist"
					)
				}
				DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
					if(Playlists.all.isEmpty()) {
						DropdownMenuItem(
							text = { Text(stringResource(R.string.no_playlists)) },
							onClick = { menuOpen = false },
							enabled = false
						)
					} else {
						Playlists.all.forEach { pl ->
							DropdownMenuItem(
								text = { Text(pl.name) },
								onClick = {
									Playlists.addTrack(pl.id, libItem.path)
									menuOpen = false
								}
							)
						}
					}
				}
			}
		}
	}
	if(libItem is LibDir && showSubItems) {
		Column {
			libItem.libDirs.forEach { Item(it, true, onItemIconClick, showPlaylists) }
			libItem.libTracks.forEach { Item(it, false, onItemIconClick, showPlaylists) }
		}
	}
}

@Composable
fun PlaylistsCreateRow() {
    var newName by remember { mutableStateOf("") }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            modifier = Modifier
				.weight(1f)
				.padding(end = 5.dp),
            value = newName,
            onValueChange = { newName = it },
            label = { Text(R.string.playlists_new) }
        )
        OutlinedButton(
			onClick = { if(newName.isNotBlank()) { Playlists.create(newName.trim()); newName = "" } }
		) { Text(R.string.create) }
    }
}

@Composable
fun PlaylistRow(pl: Playlist) {
    var rename by remember(pl) { mutableStateOf(false) }
    var editName by remember(pl.name) { mutableStateOf(pl.name) }
    var showTracks by remember(pl) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        Row(
			Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.SpaceBetween,
			verticalAlignment = Alignment.CenterVertically
		) {
            if(rename) {
                Row(
					Modifier.weight(1f),
					horizontalArrangement = Arrangement.spacedBy(8.dp),
					verticalAlignment = Alignment.CenterVertically
				) {
                    OutlinedTextField(
                        modifier = Modifier.weight(1f),
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text(stringResource(R.string.playlists_rename)) }
                    )
                    IconButton(
                        modifier = Modifier.size(48.dp),
                        onClick = {
                            rename = false
                            Playlists.rename(pl.id, editName.trim())
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = stringResource(R.string.save)
                        )
                    }
                }
            } else {
                TextButton(
					modifier = Modifier.weight(1f),
					onClick = { showTracks = !showTracks }
				){
					Text(
						modifier = Modifier.weight(1f),
						text = pl.name,
						textAlign = TextAlign.Start,
						color = colorScheme.onBackground
					)
				}
                IconButton(
                    modifier = Modifier.size(48.dp),
                    onClick = { rename = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.rename)
                    )
                }
            }
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = { Playlists.delete(pl.id) }
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
        if(showTracks) {
            Column(Modifier
				.fillMaxWidth()
				.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)) {
                if(pl.trackPaths.isEmpty()) {
                    Text(stringResource(R.string.no_playlists))
                } else {
                    pl.trackPaths.forEach { path ->
                        Row(
							Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically
						) {
                             Text(
								modifier = Modifier
									.weight(1f)
									.padding(vertical = 2.dp),
								text = path,
								maxLines = 1,
								overflow = TextOverflow.StartEllipsis
							)
                            IconButton(
                                modifier = Modifier.size(48.dp),
                                onClick = { Playlists.removeTrack(pl.id, path) }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = stringResource(R.string.delete)
                                )
                            }
                        }
                    }
                }
            }
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

	Library.watchLibDir.status = LibItem.Status.NOT
	Library.watchLibDir.libTracks.add(track2)

	CommsBT.deviceName = "Test watch"
	CommsBT.freeSpace = " 10 GB"
	CommsBT.messageStatus = R.string.sync_done

	Playlists.init(LocalContext.current){}
	Playlists.all.clear()
	val playlist1 = Playlists.create("Favorites")
	playlist1.trackPaths.addAll(listOf("dir1/hit.mp3", track2.path))
	val playlist2 = Playlists.create("Chill")
	playlist2.trackPaths.addAll(listOf(track1.path))
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
fun PreviewHomeDay() { PreviewHome() }
