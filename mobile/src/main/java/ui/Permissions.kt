/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer.ui

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_AUDIO
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat.checkSelfPermission
import com.windkracht8.wearmusicplayer.R
import com.windkracht8.wearmusicplayer.ui.PermissionsUtil.btPermission
import com.windkracht8.wearmusicplayer.ui.PermissionsUtil.hasBTPermission
import com.windkracht8.wearmusicplayer.ui.PermissionsUtil.hasReadPermission
import com.windkracht8.wearmusicplayer.ui.PermissionsUtil.readPermission

object PermissionsUtil {
	val btPermission: String
		get() = if(Build.VERSION.SDK_INT >= 31) BLUETOOTH_CONNECT else BLUETOOTH
	val readPermission: String
		get() = if(Build.VERSION.SDK_INT >= 33) READ_MEDIA_AUDIO else READ_EXTERNAL_STORAGE
	fun Context.hasBTPermission(): Boolean = hasPermission(btPermission)
	fun Context.hasReadPermission(): Boolean = hasPermission(readPermission)
	fun Context.hasAllPermissions(): Boolean = hasBTPermission() && hasReadPermission()
	private fun Context.hasPermission(permission: String): Boolean =
		checkSelfPermission(this, permission) == PERMISSION_GRANTED
}

class Permissions : ComponentActivity() {
	private var hasBT by mutableStateOf(false)
	private var hasRead by mutableStateOf(false)
	private val hasAll: Boolean
		get() = hasBT && hasRead
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		hasBT = hasBTPermission()
		hasRead = hasReadPermission()
		if(hasAll) finishAndRemoveTask()

		setContent {
			W8Theme(window, resources) { Surface { PermissionsScreen(
				hasBT,
				hasRead,
				onBTClick = { requestBT.launch(btPermission) },
				onReadClick = { requestRead.launch(readPermission) }
			) } }
		}
	}
	private val requestBT = registerForActivityResult(ActivityResultContracts.RequestPermission()){
		hasBT = it
		if(hasAll) finishAndRemoveTask()
	}
	private val requestRead = registerForActivityResult(ActivityResultContracts.RequestPermission()){
		hasRead = it
		if(hasAll) finishAndRemoveTask()
	}
}

@Composable
fun PermissionsScreen(
	hasBT: Boolean,
	hasRead: Boolean,
	onBTClick: () -> Unit,
	onReadClick: () -> Unit,
) {
	Column(Modifier.fillMaxSize().safeDrawingPadding()) {
		Text(
			modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
			text = stringResource(R.string.permission_title),
			color = colorScheme.primary,
			style = typography.titleLarge,
			textAlign = TextAlign.Center
		)
		PermissionsPermission(
			has = hasBT,
			textAllowed = R.string.permission_nearby_allowed,
			textTitle = R.string.permission_nearby_title,
			textButton = R.string.permission_nearby,
			onClick = onBTClick
		)
		PermissionsPermission(
			has = hasRead,
			textAllowed = R.string.permission_read_allowed,
			textTitle = R.string.permission_read_title,
			textButton = R.string.permission_read,
			onClick = onReadClick
		)
		Text(
			modifier = Modifier.fillMaxWidth(),
			text = stringResource(R.string.permission_explain),
			style = typography.bodySmall,
			textAlign = TextAlign.Center
		)
	}
}

@Composable
fun PermissionsPermission(
	has: Boolean,
	textAllowed: Int,
	textTitle: Int,
	textButton: Int,
	onClick: () -> Unit
) {
	Text(
		modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
		text = stringResource(if(has) textAllowed else textTitle),
		style = typography.bodyLarge,
		textAlign = TextAlign.Center
	)
	if(!has) Button(onClick, Modifier.fillMaxWidth()) { Text(textButton) }
	HorizontalDivider(
		Modifier.fillMaxWidth().padding(vertical = 10.dp),
		thickness = 2.dp,
	)
}

@Preview(uiMode = Configuration.UI_MODE_NIGHT_YES, device = Devices.PIXEL_8)
@Composable
fun PreviewPermissions() {
	W8Theme(null, null) { Surface {
		PermissionsScreen(
			hasBT = true,
			hasRead = false,
			onBTClick = {},
			onReadClick = {}
		)
	} }
}
@Preview(device = Devices.PIXEL_8)
@Composable
fun PreviewPermissionsDay() { PreviewPermissions() }
