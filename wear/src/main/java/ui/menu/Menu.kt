/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer.ui.menu

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme.colorScheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding
import com.windkracht8.wearmusicplayer.ui.ColorW8
import com.windkracht8.wearmusicplayer.ui.ColorWhite
import com.windkracht8.wearmusicplayer.R

@Composable
fun Menu(
	onMenuAllClick: () -> Unit,
	onMenuAlbumsClick: () -> Unit,
	onMenuArtistsClick: () -> Unit,
	onMenuPlaylistsClick: () -> Unit,
	onMenuDirsClick: () -> Unit,
	onRescanClick: () -> Unit,
	btEnabled: Boolean?,
	onBTClick: () -> Unit
) {
	val columnState = rememberTransformingLazyColumnState()
	val contentPadding = rememberResponsiveColumnPadding()
	val transformationSpec = rememberTransformationSpec()
	ScreenScaffold(
		scrollState = columnState,
		contentPadding = contentPadding
	) { contentPadding ->
		TransformingLazyColumn(
			state = columnState,
			contentPadding = contentPadding
		) {
			item {
				MenuHeaderItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = stringResource(R.string.library)
				)
			}
			item {
				MenuItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = stringResource(R.string.all),
					onClick = onMenuAllClick
				)
			}
			item {
				MenuItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = stringResource(R.string.albums),
					onClick = onMenuAlbumsClick
				)
			}
			item {
				MenuItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = stringResource(R.string.artists),
					onClick = onMenuArtistsClick
				)
			}
			item {
				MenuItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = stringResource(R.string.playlists),
					onClick = onMenuPlaylistsClick
				)
			}
			item {
				MenuItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = stringResource(R.string.dirs),
					onClick = onMenuDirsClick
				)
			}
			item {
				MenuItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = stringResource(R.string.rescan),
					onClick = onRescanClick
				)
			}
			item {
				MenuItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = stringResource(R.string.BT),
					subLabel =
						if(btEnabled == true) stringResource(R.string.enabled)
						else stringResource(R.string.disabled),
					onClick = onBTClick
				)
			}
		}
	}
}

@Composable
fun MenuHeaderItem(transformation: SurfaceTransformation, label: String) =
	ListHeader(transformation = transformation) { Text(label) }

@Composable
fun MenuButtonRow(
	onPlayClick: () -> Unit,
	onShuffleClick: () -> Unit,
	isShuffled: Boolean,
	onLoopClick: () -> Unit,
	loopEnabled: Boolean
){
	Row {
		IconButton(onClick = onPlayClick) {
			Icon(
				imageVector = Icons.Default.PlayArrow,
				contentDescription = "play",
				tint = ColorW8
			)
		}
		IconButton(onClick = onShuffleClick) {
			Icon(
				imageVector = Icons.Default.Shuffle,
				contentDescription = "shuffle",
				tint = if(isShuffled) ColorW8 else colorScheme.onBackground
			)
		}
		IconButton(onClick = onLoopClick) {
			Icon(
				imageVector = Icons.Default.Repeat,
				contentDescription = "loop",
				tint = if(loopEnabled) ColorW8 else colorScheme.onBackground
			)
		}
	}
}

@Composable
fun MenuItem(
	transformation: SurfaceTransformation,
	label: String,
	subLabel: String? = null,
	onClick: () -> Unit
) {
	OutlinedButton(
		modifier = Modifier.fillMaxWidth(),
		transformation = transformation,
		onClick = onClick
	) {
		Column {
			BasicText(
				text = label,
				color = { ColorWhite },
				maxLines = if(subLabel == null) 2 else 1,
				softWrap = subLabel == null,
				autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp)
			)
			if(subLabel != null) {
				BasicText(
					text = subLabel,
					color = { ColorW8 },
					maxLines = 1,
					softWrap = false,
					autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 14.sp)
				)
			}
		}
	}
}
