/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 */

package com.windkracht8.wearmusicplayer.ui

import com.windkracht8.wearmusicplayer.CommsBT
import com.windkracht8.wearmusicplayer.data.LibDir
import com.windkracht8.wearmusicplayer.data.LibItem
import com.windkracht8.wearmusicplayer.data.LibItemState
import com.windkracht8.wearmusicplayer.data.Playlist

data class HomeUiState(
	val hasBTPermission: Boolean = false,
	val hasReadPermission: Boolean = false,

	val showLoading: Boolean = true,
	val freeSpace: String = "",
	val phoneRoot: LibDir = LibDir("Music", null),
	val watchRoot: LibDir? = null,
	val itemStates: Map<LibItem, LibItemState> = emptyMap(),
	val playlists: List<Playlist> = emptyList(),
	val itemExistsAskToDelete: LibItem? = null,

	val btStatus: CommsBT.Status? = null,
	val btDeviceName: String = "",
	val btMessageStatus: Int = -1,
	val btError: Int = -1
)
