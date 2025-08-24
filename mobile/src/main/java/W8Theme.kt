/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val LightColorScheme = lightColorScheme(
	background = Color(0xFFFFFFFF),
	onBackground = Color(0xFF000000),
	surface = Color(0xFFFFFFFF),
	onSurface = Color(0xFF000000),
	primary = Color(0xFF105B65),
	onPrimary = Color(0xFFFFFFFF),
	error = Color(0xFFFF0000),
)
val DarkColorScheme = darkColorScheme(
	background = Color(0xFF000000),
	onBackground = Color(0xFFFFFFFF),
	surface = Color(0xFF000000),
	onSurface = Color(0xFFFFFFFF),
	primary = Color(0xFF2BD2E3),
	onPrimary = Color(0xFF000000),
	error = Color(0xFFFF0000),
)
@Composable
fun W8Theme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = if(isSystemInDarkTheme()) DarkColorScheme else LightColorScheme,
		content = content
	)
}
