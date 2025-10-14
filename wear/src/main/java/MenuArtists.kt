/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding

@Composable
fun MenuArtists(onMenuArtistClick: (id: Int) -> Unit) {
	val columnState = rememberTransformingLazyColumnState()
	val contentPadding = rememberResponsiveColumnPadding()
	val transformationSpec = rememberTransformationSpec()
	ScreenScaffold(scrollState = columnState, contentPadding = contentPadding) { contentPadding ->
		TransformingLazyColumn(state = columnState, contentPadding = contentPadding) {
			item {
				MenuHeaderItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = stringResource(R.string.artists),
				)
			}
			items(Library.artists) {
				MenuItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = it.name,
					subLabel =
						if (it.albums.isEmpty()) trackOrTracks(it.tracks.size)
						else (albumOrAlbums(it.albums.size) + " " +
								trackOrTracks(it.tracks.size)
						),
					onClick = { onMenuArtistClick(it.id) }
				)
			}
		}
	}
}
