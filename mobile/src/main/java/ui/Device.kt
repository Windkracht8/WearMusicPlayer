/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer.ui

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.windkracht8.wearmusicplayer.CommsBT
import com.windkracht8.wearmusicplayer.CommsBT.Status.CONNECTED
import com.windkracht8.wearmusicplayer.CommsBT.Status.CONNECTING
import com.windkracht8.wearmusicplayer.CommsBT.Status.PAIRING
import com.windkracht8.wearmusicplayer.ui.PermissionsUtil.hasBTPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class Device : ComponentActivity() {
	private val viewModel: DeviceViewModel by viewModels()
	public override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		if(CommsBT.isConnected || !hasBTPermission()) finishAndRemoveTask()
		lifecycleScope.launch {
			repeatOnLifecycle(Lifecycle.State.STARTED) {
				viewModel.uiState.collect { if(it.btStatus == CONNECTED) finishAndRemoveTask() }
			}
		}
		setContent {
			val uiState by viewModel.uiState.collectAsState()
			W8Theme(window, resources) { Surface { DeviceScreen(uiState, viewModel) } }
		}
	}
}
data class DeviceUiState(
	val btStatus: CommsBT.Status? = null,
	val pairCode: String = "",
	val deviceName: String = "",
	val messageStatus: Int = -1,
	val error: Int = -1,
	val knownDevices: Set<CommsBT.Device> = emptySet(),
	val bondedDevices: List<CommsBT.Device>? = null
)
interface DeviceActions {
	fun onIconClick()
	fun connectDevice(device: CommsBT.Device)
	fun delKnownDevice(address: String)
	fun getBondedDevices()
}
object DeviceActionsStub : DeviceActions {
	override fun onIconClick() {}
	override fun connectDevice(device: CommsBT.Device) {}
	override fun delKnownDevice(address: String) {}
	override fun getBondedDevices() {}
}
class DeviceViewModel(application: Application) : AndroidViewModel(application), DeviceActions {
	private val _uiState = MutableStateFlow(DeviceUiState())
	val uiState: StateFlow<DeviceUiState> = _uiState.asStateFlow()

	init {
		viewModelScope.launch {
			CommsBT.status.collect { status ->
				_uiState.update {
					it.copy(
						btStatus = status,
						pairCode = CommsBT.pairCode,
						deviceName = CommsBT.deviceName
					)
				}
			}
		}
		viewModelScope.launch {
			CommsBT.messageStatus.collect { messageStatus ->
				_uiState.update { it.copy(messageStatus = messageStatus) }
			}
		}
		viewModelScope.launch {
			CommsBT.error.collect { error ->
				_uiState.update { it.copy(error = error) }
			}
		}
		viewModelScope.launch(Dispatchers.Default) {
			_uiState.update { it.copy(knownDevices = CommsBT.knownDevices) }
		}
	}
	override fun onIconClick() {
		if(_uiState.value.btStatus in listOf(CONNECTING, PAIRING, CONNECTED)) CommsBT.stop()
	}
	override fun connectDevice(device: CommsBT.Device) {
		viewModelScope.launch(Dispatchers.Default) {
			CommsBT.connectDevice(getApplication(), device)
		}
	}
	override fun delKnownDevice(address: String) {
		viewModelScope.launch(Dispatchers.Default) {
			CommsBT.delKnownDevice(address)
			_uiState.update { it.copy(knownDevices = CommsBT.knownDevices) }
		}
	}
	override fun getBondedDevices() {
		_uiState.update { it.copy(bondedDevices = emptyList()) }
		viewModelScope.launch(Dispatchers.Default) {
			_uiState.update {
				it.copy(bondedDevices = CommsBT.getBondedDevices(getApplication()))
			}
		}
	}
}
