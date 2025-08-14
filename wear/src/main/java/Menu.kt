/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import com.google.android.horologist.compose.layout.ColumnItemType
import com.google.android.horologist.compose.layout.rememberResponsiveColumnPadding

@Composable
fun Menu(
	onMenuAllClick: () -> Unit,
	onMenuAlbumsClick: () -> Unit,
	onMenuArtistsClick: () -> Unit,
	onRescanClick: () -> Unit
) {
	val columnState = rememberTransformingLazyColumnState()
	val contentPadding = rememberResponsiveColumnPadding(
		first = ColumnItemType.ListHeader,
		last = ColumnItemType.Button,
	)
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
					label = "Library"
				)
			}
			item {
				MenuItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = "All",
					onClick = onMenuAllClick
				)
			}
			item {
				MenuItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = "Albums",
					onClick = onMenuAlbumsClick
				)
			}
			item {
				MenuItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = "Artists",
					onClick = onMenuArtistsClick
				)
			}
			item {
				MenuItem(
					transformation = SurfaceTransformation(transformationSpec),
					label = "Rescan",
					onClick = onRescanClick
				)
			}
		}
	}
}

@Composable
fun MenuHeaderItem(transformation: SurfaceTransformation, label: String) {
	ListHeader(transformation = transformation) { Text(text = label) }
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
				maxLines = if (subLabel == null) 2 else 1,
				softWrap = subLabel == null,
				autoSize = TextAutoSize.StepBased(
					minFontSize = 10.sp,
					maxFontSize = 14.sp,
					stepSize = 1.sp
				)
			)
			if (subLabel != null) {
				BasicText(
					text = subLabel,
					color = { ColorW8Blue },
					maxLines = 1,
					softWrap = false,
					autoSize = TextAutoSize.StepBased(
						minFontSize = 10.sp,
						maxFontSize = 14.sp,
						stepSize = 1.sp
					)
				)
			}
		}
	}
}
