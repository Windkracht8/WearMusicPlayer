/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.windkracht8.wearmusicplayer.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.windkracht8.wearmusicplayer.CommsBT
import com.windkracht8.wearmusicplayer.CommsBT.Status.DISCONNECTED
import com.windkracht8.wearmusicplayer.CommsBT.Status.ERROR
import com.windkracht8.wearmusicplayer.CommsWifi
import com.windkracht8.wearmusicplayer.R
import com.windkracht8.wearmusicplayer.data.LibDir
import com.windkracht8.wearmusicplayer.data.LibItem
import com.windkracht8.wearmusicplayer.data.Library
import com.windkracht8.wearmusicplayer.data.Playlists
import com.windkracht8.wearmusicplayer.runInBackground
import com.windkracht8.wearmusicplayer.toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(application: Application) : AndroidViewModel(application) {
	private val _uiState = MutableStateFlow(HomeUiState())
	val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

	init {
		viewModelScope.launch {
			CommsBT.status.collect { status ->
				_uiState.update { it.copy(btStatus = status) }
				if(status == DISCONNECTED || status == ERROR) Library.clearStatuses()
			}
		}
		viewModelScope.launch {
			combine(
				CommsBT.freeSpace,
				CommsBT.itemExistsAskToDelete,
				CommsBT.messageStatus,
				CommsBT.error
			) { freeSpace, itemExistsAskToDelete, messageStatus, error ->
				_uiState.value.copy(
					freeSpace = freeSpace,
					itemExistsAskToDelete = itemExistsAskToDelete,
					btDeviceName = CommsBT.deviceName,
					btMessageStatus = messageStatus,
					btError = error
				)
			}.collect { _uiState.value = it }
		}

		viewModelScope.launch {
			Library.status.collect { status ->
				_uiState.update { it.copy(showLoading = status == Library.Status.SCAN) }
				if(status == Library.Status.READY && CommsBT.isConnected) CommsBT.sendRequestSync()
			}
		}
		viewModelScope.launch {
			combine(
				Library.phoneRoot,
				Library.watchRoot,
				Library.itemStates
			) { phoneRoot, watchRoot, itemStates ->
				_uiState.value.copy(
					phoneRoot = phoneRoot,
					watchRoot = watchRoot,
					itemStates = itemStates
				)
			}.collect { _uiState.value = it }
		}

		viewModelScope.launch {
			Playlists.all.collect { playlists ->
				_uiState.update { it.copy(playlists = playlists) }
			}
		}
	}

	fun onResume(hasBT: Boolean, hasRead: Boolean){
		val context = getApplication<Application>()
		_uiState.update { it.copy(hasBTPermission = hasBT, hasReadPermission = hasRead) }
		if(hasRead && Library.status.value == null) Library.scanFiles()
		if(hasBT && CommsBT.status.value == null) runInBackground { CommsBT.start(context) }
		Playlists.init(context) { context.toast(R.string.fail_read_playlist) }
	}
	fun onItemIconClick(libItem: LibItem) {
		if(libItem is LibDir || !CommsBT.isConnected) return
		if(_uiState.value.itemStates[libItem]?.status == LibItem.Status.FULL) {
			viewModelScope.launch(Dispatchers.IO) {
				CommsBT.sendRequestDeleteFile(libItem)
			}
		} else {
			viewModelScope.launch(Dispatchers.IO) {
				CommsWifi.getIpAddress(getApplication())
				CommsBT.sendRequestSendFile(libItem)
			}
		}
	}
	fun confirmDeleteFile(libItem: LibItem) {
		viewModelScope.launch(Dispatchers.IO) { CommsBT.confirmDeleteFile(libItem) }
	}
	fun dismissDeleteDialog() { CommsBT.itemExistsAskToDelete.value = null }

	fun playlistCreate(name: String) {
		viewModelScope.launch(Dispatchers.Default) { Playlists.create(name.trim()) }
	}
	fun playlistRename(id: Int, newName: String) {
		viewModelScope.launch(Dispatchers.Default) { Playlists.rename(id, newName.trim()) }
	}
	fun playlistDelete(id: Int) {
		viewModelScope.launch(Dispatchers.Default) { Playlists.delete(id) }
	}
	fun playlistAddTrack(id: Int, path: String) {
		viewModelScope.launch(Dispatchers.Default) { Playlists.addTrack(id, path) }
	}
	fun playlistDelTrack(id: Int, path: String) {
		viewModelScope.launch(Dispatchers.Default) { Playlists.delTrack(id, path) }
	}
}
