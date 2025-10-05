/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding

@Composable
fun MenuAlbum(
	id: Int,
	onRandomiseClick: () -> Unit,
	openTracks: (type: Main.TrackListType, id: Int, index: Int) -> Unit,
	trackId: Int
) {
	val album = Library.albums.firstOrNull { it.id == id }
	val columnState = rememberTransformingLazyColumnState()
	val contentPadding = rememberResponsiveColumnPadding()
	val transformationSpec = rememberTransformationSpec()
	LaunchedEffect(Unit) {
		if (trackId > 0) columnState.scrollToItem(trackId + 2)
	}
	ScreenScaffold(scrollState = columnState, contentPadding = contentPadding) { contentPadding ->
		TransformingLazyColumn(state = columnState, contentPadding = contentPadding) {
			item {
				MenuHeaderItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = album?.name ?: stringResource(R.string.oops),
				)
			}
			item {
				Row {
					IconButton(onClick = { openTracks(Main.TrackListType.ALBUM, id, 0) }) {
						Icon(
							imageVector = Icons.Default.PlayArrow,
							contentDescription = "play",
							tint = ColorW8Blue
						)
					}
					IconButton(onClick = onRandomiseClick) {
						Icon(
							imageVector = Icons.Default.Shuffle,
							contentDescription = "randomise",
							tint = ColorW8Blue
						)
					}
				}
			}
			album?.tracks?.forEachIndexed { index, track ->
				item {
					MenuItem(
						transformation = SurfaceTransformation(transformationSpec),
						label = track.title,
						subLabel = if (track.artist.name == album.artist) null else track.artist.name,
						onClick = { openTracks(Main.TrackListType.ALBUM, id, index) }
					)
				}
			}
		}
	}
}
