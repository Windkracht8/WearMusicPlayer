/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windkracht8.wearmusicplayer.CommsBT.Status.CONNECTED
import com.windkracht8.wearmusicplayer.R
import com.windkracht8.wearmusicplayer.data.LibDir
import com.windkracht8.wearmusicplayer.data.LibItem
import com.windkracht8.wearmusicplayer.data.LibItemState
import com.windkracht8.wearmusicplayer.data.LibTrack
import com.windkracht8.wearmusicplayer.data.Playlist

@Composable
fun HomeScreen(
	uiState: HomeUiState,
	onIconClick: () -> Unit,
	onOpenFolderClick: () -> Unit,
	onItemIconClick: (LibItem) -> Unit,
	dismissDeleteDialog: () -> Unit,
	confirmDeleteFile: (LibItem) -> Unit,
	playlistCreate: (String) -> Unit,
	playlistRename: (Int, String) -> Unit,
	playlistDelete: (Int) -> Unit,
	playlistAddTrack: (Int, String) -> Unit,
	playlistDelTrack: (Int, String) -> Unit,
) {
	val showPlaylists = remember { mutableStateOf(false) }
	var showWatchTracks by remember { mutableStateOf(false) }
	var columnSize by remember { mutableStateOf(IntSize.Zero) }

	Column(Modifier.fillMaxSize().safeDrawingPadding().onSizeChanged { columnSize = it }) {
		val maxSectionHeight = with(LocalDensity.current) { (columnSize.height / 4).toDp() }
		HomeDevice(
			uiState.hasBTPermission,
			uiState.freeSpace,
			uiState.btStatus,
			uiState.btDeviceName,
			uiState.btMessageStatus,
			uiState.btError,
			onIconClick,
		)
		Row(
			modifier = Modifier.fillMaxWidth(),
			horizontalArrangement = Arrangement.End
		) {
			OutlinedButton(onClick = onOpenFolderClick) { Text(R.string.open_folder) }
		}
		if(uiState.showLoading) {
			Text(
				modifier = Modifier.fillMaxWidth(),
				text = stringResource(id =
					if(uiState.hasReadPermission) R.string.loading
					else R.string.no_permission
				),
				fontSize = 24.sp,
				textAlign = TextAlign.Center
			)
		} else {
			LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
				itemsIndexed(uiState.phoneRoot.libDirs) { i, libItem ->
					Item(
						uiState.itemStates[libItem],
						uiState.itemStates,
						isConnected = uiState.watchRoot != null,
						uiState.playlists,
						libItem,
						showDivider = i > 0,
						onItemIconClick,
						playlistAddTrack,
						showPlaylists.value
					)
				}
				itemsIndexed(uiState.phoneRoot.libTracks) { i, libItem ->
					Item(
						uiState.itemStates[libItem],
						uiState.itemStates,
						isConnected = uiState.watchRoot != null,
						uiState.playlists,
						libItem,
						showDivider = i == 0,
						onItemIconClick,
						playlistAddTrack,
						showPlaylists.value
					)
				}
			}
			HomePlaylists(
				maxSectionHeight,
				showPlaylists,
				uiState.playlists,
				playlistCreate,
				playlistRename,
				playlistDelete,
				playlistDelTrack,
			)
			if(uiState.watchRoot != null) {
				HorizontalDivider(
					modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
					color = colorScheme.onBackground
				)
				TextButton(
					modifier = Modifier.fillMaxWidth(),
					onClick = { showWatchTracks = !showWatchTracks }
				){ Text(
					text = stringResource(R.string.watch_tracks),
					style = typography.titleMedium,
					color = colorScheme.onBackground
				) }
				HorizontalDivider(
					modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
					color = colorScheme.onBackground
				)
				if(showWatchTracks) {
					LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxSectionHeight)) {
						itemsIndexed(
							items = uiState.watchRoot.libDirs
								.filter { uiState.itemStates[it]?.status == LibItem.Status.WATCH_ONLY },
							key = { _, it -> it.fullPath }
						) { i, libItem ->
							Item(
								uiState.itemStates[libItem],
								uiState.itemStates,
								isConnected = true,
								uiState.playlists,
								libItem,
								showDivider = i > 0,
								onItemIconClick,
								playlistAddTrack,
								showPlaylists.value
							)
						}
						itemsIndexed(
							items = uiState.watchRoot.libTracks
								.filter { uiState.itemStates[it]?.status == LibItem.Status.WATCH_ONLY },
							key = { _, it -> it.fullPath }
						) { i, libItem ->
							Item(
								uiState.itemStates[libItem],
								uiState.itemStates,
								isConnected = true,
								uiState.playlists,
								libItem,
								showDivider = i == 0,
								onItemIconClick,
								playlistAddTrack,
								showPlaylists.value
							)
						}
					}
				}
			}
		}
	}
	uiState.itemExistsAskToDelete?.let { libItem ->
		AlertDialog(
			onDismissRequest = { dismissDeleteDialog() },
			title = { Text("File exists") },
			text = { Text(stringResource(R.string.file_send_delete, libItem.name)) },
			confirmButton = {
				TextButton(onClick = { confirmDeleteFile(libItem) }) { Text("Yes") }
			},
			dismissButton = {
				TextButton(onClick = { dismissDeleteDialog() }) { Text("No") }
			}
		)
	}
}
@Composable
fun Item(
	itemState: LibItemState?,
	itemStates: Map<LibItem, LibItemState>,
	isConnected: Boolean,
	playlists: List<Playlist>,
	libItem: LibItem,
	showDivider: Boolean,
	onItemIconClick: (LibItem) -> Unit,
	playlistAddTrack: (Int, String) -> Unit,
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
			when(itemState?.status) {
				LibItem.Status.PENDING -> {
					CircularProgressIndicator(
						trackColor = colorScheme.onBackground.copy(alpha = 0.2f)
					)
				}
				LibItem.Status.SENDING -> {
					CircularProgressIndicator(
						trackColor = colorScheme.onBackground.copy(alpha = 0.2f),
						progress = { itemState.progress }
					)
				}
				else -> {
					IconButton(
						modifier = Modifier.size(48.dp),
						onClick = { onItemIconClick(libItem) }
					) {
						if(isConnected) {
							val imageVector = if(libItem is LibDir) {
								when(itemState?.status) {
									LibItem.Status.FULL -> Icons.Default.DoneAll
									LibItem.Status.PARTIAL -> Icons.Default.Check
									else -> null
								}
							} else {
								when(itemState?.status) {
									LibItem.Status.FULL, LibItem.Status.WATCH_ONLY -> Icons.Default.Delete
									else -> Icons.Default.Upload
								}
							}
							if(imageVector != null) {
								Icon(
									imageVector = imageVector,
									contentDescription = "item icon and action"
								)
							}
						}
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
		if(showPlaylists && libItem is LibTrack && itemState?.status == LibItem.Status.FULL) {
			var menuOpen by remember { mutableStateOf(false) }
			Box {
				IconButton(
					modifier = Modifier.size(48.dp),
					onClick = { menuOpen = true }
				) {
					Icon(
						imageVector = Icons.AutoMirrored.Default.PlaylistAdd,
						contentDescription = "add to playlist"
					)
				}
				DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
					if(playlists.isEmpty()) {
						DropdownMenuItem(
							text = { Text(stringResource(R.string.no_playlists)) },
							onClick = { menuOpen = false },
							enabled = false
						)
					} else {
						playlists.forEach { pl ->
							DropdownMenuItem(
								text = { Text(pl.name) },
								onClick = {
									playlistAddTrack(pl.id, libItem.path)
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
			libItem.libDirs.forEach { Item(
				itemStates[it],
				itemStates,
				isConnected,
				playlists,
				it,
				true,
				onItemIconClick,
				playlistAddTrack,
				showPlaylists
			) }
			libItem.libTracks.forEach { Item(
				itemStates[it],
				itemStates,
				isConnected,
				playlists,
				it,
				false,
				onItemIconClick,
				playlistAddTrack,
				showPlaylists
			) }
		}
	}
}

@Composable
fun Text(text: Int) = Text(stringResource(text))
@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PIXEL_8)
@Composable
fun PreviewHomeScreen() {
	val uiState = HomeUiState(
		hasBTPermission = true,
		hasReadPermission = true,
		showLoading = false,
		btStatus = CONNECTED,
		btDeviceName = "Test watch",
		btMessageStatus = R.string.sync_done,
		freeSpace = " 10 GB",
		playlists = listOf(
			Playlist(0, 0, "Favorites", trackPaths = listOf("dir1/hit.mp3", "")),
			Playlist(1, 0, "Chill hits", trackPaths = listOf(""))
		)
	)
	/*var dir = LibDir("dir1")
	dir.status = LibItem.Status.PARTIAL
	uiState.phoneRoot.libDirs.add(dir)
	dir = LibDir("dir2")
	dir.status = LibItem.Status.FULL
	uiState.phoneRoot.libDirs.add(dir)
	var track = LibTrack("track1")
	track.status = LibItem.Status.SENDING
	track.progress = 35
	uiState.phoneRoot.libTracks.add(track)
	track = LibTrack("track2")
	track.status = LibItem.Status.FULL
	uiState.phoneRoot.libTracks.add(track)
	track = LibTrack("track3")
	track.status = LibItem.Status.NOT
	uiState.phoneRoot.libTracks.add(track)

	uiState.watchRoot.status = LibItem.Status.NOT
	uiState.watchRoot.libTracks.add(track)*/

	W8Theme(null, null) {
		Surface {
			HomeScreen(
				uiState,
				onIconClick = {},
				onOpenFolderClick = {},
				onItemIconClick = {},
				dismissDeleteDialog = {},
				confirmDeleteFile = {},
				playlistCreate = {},
				playlistRename = { _, _ -> },
				playlistDelete = {},
				playlistAddTrack = { _, _ -> },
				playlistDelTrack = { _, _ -> },
			)
		}
	}
}
@Preview(device = Devices.PIXEL_8)
@Composable
fun PreviewHomeScreenDay() { PreviewHomeScreen() }
