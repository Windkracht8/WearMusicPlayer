/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 */
package com.windkracht8.wearmusicplayer.data

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.core.content.edit
import com.windkracht8.wearmusicplayer.CommsBT
import com.windkracht8.wearmusicplayer.logE
import com.windkracht8.wearmusicplayer.tryIgnore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(
	val id: Int,
	val version: Int,
	val name: String,
	val trackPaths: List<String> = emptyList()
){
	fun toJson(): JSONObject = JSONObject().apply {
		put("id", id)
		put("name", name)
		put("tracks", JSONArray().apply { trackPaths.forEach { put(it) } })
	}
}

object Playlists {
	private lateinit var sharedPreferences: SharedPreferences
	
	private val _all = MutableStateFlow<List<Playlist>>(emptyList())
	val all: StateFlow<List<Playlist>> = _all.asStateFlow()

	private var maxId: Int = 0
	private fun getNewId(): Int {
		maxId++
		sharedPreferences.edit { putInt("maxId", maxId) }
		return maxId
	}

	fun init(context: Context, onError: () -> Unit) {
		if (::sharedPreferences.isInitialized) return
		sharedPreferences = context.getSharedPreferences("Playlists", MODE_PRIVATE)
		val list = mutableListOf<Playlist>()
		tryIgnore {
			sharedPreferences.all.forEach { (key, value) ->
				try {
					if(key == "maxId") {
						maxId = value as Int
						return@forEach
					}
					val obj = JSONObject(value as String)
					val tracksJson = obj.getJSONArray("tracks")
					val tracks = mutableListOf<String>()
					for(index in 0 until tracksJson.length()) {
						tracks.add(tracksJson.getString(index))
					}
					list.add(Playlist(
						id = obj.getInt("id"),
						version = obj.optInt("version"),
						name = obj.getString("name"),
						trackPaths = tracks
					))
				} catch(e: Exception) {
					logE("Playlists.init: ${e.message}")
					onError()
				}
			}
		}
		_all.value = list
	}

	fun create(name: String): Playlist {
		val playlist = Playlist(getNewId(), 1, name)
		_all.value += playlist
		sharedPreferences.edit { putString(playlist.id.toString(), playlist.toJson().toString()) }
		return playlist
	}

	fun rename(id: Int, newName: String) {
		val oldPlaylist = _all.value.find { it.id == id } ?: return
		val newPlaylist = oldPlaylist.copy(
			id = getNewId(),//still needed, until all users are on new CommsBT version
			version = oldPlaylist.version+1,
			name = newName
		)
		
		_all.value = _all.value.map { if (it.id == id) newPlaylist else it }

		sharedPreferences.edit {
			remove(id.toString())
			putString(newPlaylist.id.toString(), newPlaylist.toJson().toString())
		}
		if(CommsBT.isConnected) CommsBT.sendRequestUpdPlaylist(newPlaylist.id, id)
	}

	fun delete(id: Int) {
		_all.value = _all.value.filter { it.id != id }
		sharedPreferences.edit { remove(id.toString()) }
		if(CommsBT.isConnected) CommsBT.sendRequestDelPlaylist(id)
	}

	fun addTrack(id: Int, path: String) {
		val oldPlaylist = _all.value.find { it.id == id } ?: return
		if(path !in oldPlaylist.trackPaths) {
			val newPlaylist = oldPlaylist.copy(
				version = oldPlaylist.version+1,
				trackPaths = oldPlaylist.trackPaths + path
			)
			_all.value = _all.value.map { if (it.id == id) newPlaylist else it }
			
			sharedPreferences.edit {
				remove(id.toString())
				putString(newPlaylist.id.toString(), newPlaylist.toJson().toString())
			}
			if(CommsBT.isConnected) CommsBT.sendRequestUpdPlaylist(newPlaylist.id, id)
		}
	}

	fun delTrack(id: Int, path: String) {
		val oldPlaylist = _all.value.find { it.id == id } ?: return
		if (path in oldPlaylist.trackPaths) {
			val newPlaylist = oldPlaylist.copy(
				version = oldPlaylist.version+1,
				trackPaths = oldPlaylist.trackPaths - path
			)
			_all.value = _all.value.map { if (it.id == id) newPlaylist else it }
			
			sharedPreferences.edit {
				remove(id.toString())
				putString(newPlaylist.id.toString(), newPlaylist.toJson().toString())
			}
			if(CommsBT.isConnected) CommsBT.sendRequestUpdPlaylist(newPlaylist.id, id)
		}
	}
}
