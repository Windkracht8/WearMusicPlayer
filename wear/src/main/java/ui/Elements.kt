/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.windkracht8.wearmusicplayer.ui

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.material3.MaterialTheme.colorScheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton

@Composable
fun Text(@StringRes text: Int) = Text(stringResource(text))
@Composable
fun Text(@StringRes text: Int, color: Color) = Text(text = stringResource(text), color = color)
@Composable
@ReadOnlyComposable
fun plural(@PluralsRes id: Int, count: Int): String =
	LocalResources.current.getQuantityString(id, count, count)

@Composable
fun ConfirmDialog(
	text: Int? = null,
	textString: String? = null,
	confirmText: Int,
	dismissText: Int,
	onConfirm: () -> Unit,
	onDismiss: () -> Unit
){
	Column(
		modifier = Modifier.fillMaxSize().background(colorScheme.background),
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.Center
	){
		if(text != null) Text(text)
		if(textString != null) Text(textString, textAlign = TextAlign.Center)
		Row(Modifier.fillMaxWidth()){
			TextButton(
				modifier = Modifier.weight(1f),
				onClick = onDismiss
			){ Text(dismissText, colorScheme.outline) }
			TextButton(
				modifier = Modifier.weight(1f),
				onClick = onConfirm
			){ Text(confirmText, colorScheme.outline) }
		}
	}
}
