/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.windkracht8.wearmusicplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.windkracht8.wearmusicplayer.R
import com.windkracht8.wearmusicplayer.data.Playlist

@Composable
fun HomePlaylists(
	maxSectionHeight: Dp,
	showPlaylists: MutableState<Boolean>,
	playlists: List<Playlist>,
	playlistCreate: (String) -> Unit,
	playlistRename: (Int, String) -> Unit,
	playlistDelete: (Int) -> Unit,
	playlistDelTrack: (Int, String) -> Unit
) {
	HorizontalDivider(
		modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
		color = colorScheme.onBackground
	)
	TextButton(
		modifier = Modifier.fillMaxWidth(),
		onClick = { showPlaylists.value = !showPlaylists.value }
	){ Text(
		text = stringResource(R.string.playlists_title),
		style = typography.titleMedium,
		color = colorScheme.onBackground
	) }
	HorizontalDivider(
		modifier = Modifier.fillMaxWidth().padding(bottom = 5.dp),
		color = colorScheme.onBackground
	)
	if(showPlaylists.value) {
		LazyColumn(Modifier.fillMaxWidth().heightIn(max = maxSectionHeight)) {
			item { HomePlaylistsCreate(playlistCreate) }
			items(playlists) { HomePlaylist(
				it,
				playlistRename,
				playlistDelete,
				playlistDelTrack
			) }
		}
	}
}
@Composable
fun HomePlaylistsCreate(playlistCreate: (String) -> Unit) {
	var newName by remember { mutableStateOf("") }
	Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
		OutlinedTextField(
			modifier = Modifier.weight(1f).padding(end = 5.dp),
			value = newName,
			onValueChange = { newName = it },
			label = { Text(R.string.playlists_new) }
		)
		OutlinedButton(
			onClick = {
				if(newName.isNotBlank()) {
					playlistCreate(newName)
					newName = ""
				}
			}
		) { Text(R.string.create) }
	}
}

@Composable
fun HomePlaylist(
	pl: Playlist,
	playlistRename: (Int, String) -> Unit,
	playlistDelete: (Int) -> Unit,
	playlistDelTrack: (Int, String) -> Unit
) {
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
							playlistRename(pl.id, editName)
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
				onClick = { playlistDelete(pl.id) }
			) {
				Icon(
					imageVector = Icons.Default.Delete,
					contentDescription = stringResource(R.string.delete)
				)
			}
		}
		if(showTracks) {
			Column(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, bottom = 8.dp)) {
				if(pl.trackPaths.isEmpty()) {
					Text(stringResource(R.string.no_playlists))
				} else {
					pl.trackPaths.forEach { path ->
						Row(
							Modifier.fillMaxWidth(),
							verticalAlignment = Alignment.CenterVertically
						) {
							Text(
								modifier = Modifier.weight(1f).padding(vertical = 2.dp),
								text = path,
								maxLines = 1,
								overflow = TextOverflow.StartEllipsis
							)
							IconButton(
								modifier = Modifier.size(48.dp),
								onClick = { playlistDelTrack(pl.id, path) }
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
