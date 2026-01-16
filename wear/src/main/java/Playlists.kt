/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.edit
import com.windkracht8.wearmusicplayer.Library.Track
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(
	var id: Int,
	var name: String,
	val trackPaths: MutableList<String> = mutableListOf(),
	var tracks: MutableList<Track> = mutableListOf(),
	var shuffleCounter: MutableIntState = mutableIntStateOf(0)
){
	fun toJson(): JSONObject {
		val obj = JSONObject()
			.put("id", id)
			.put("name", name)
		val tracks = JSONArray()
		trackPaths.forEach { tracks.put(it) }
		obj.put("tracks", tracks)
		return obj
	}
}

object Playlists {
	lateinit var sharedPreferences: SharedPreferences
	val all: SnapshotStateList<Playlist> = mutableStateListOf()
	fun init(context: Context) {
		sharedPreferences = context.getSharedPreferences("Playlists", MODE_PRIVATE)
		all.clear()
		sharedPreferences.all.forEach { (_, value) ->
			try { create(value as String) ?: throw Exception("create failed") }
			catch(e: Exception) {
				logE("Playlists.init : ${e.message}")
				context.toastFromBG(R.string.fail_read_playlist)
			}
		}
	}
	fun create(json: String): Playlist? {
		try { return create(JSONObject(json)) }
		catch(e: Exception) { logE("Playlists.create : ${e.message}") }
		return null
	}
	fun create(obj: JSONObject): Playlist? {
		try {
			val playlist = Playlist(
				id = obj.getInt("id"),
				name = obj.getString("name")
			)
			val tracks = obj.getJSONArray("tracks")
			for(index in 0 until tracks.length()) {
				playlist.trackPaths.add(tracks.getString(index))
				Library.tracks.firstOrNull { it.path == tracks.getString(index) }?.let {
					playlist.tracks.add(it)
				}
			}
			all.add(playlist)
			sharedPreferences.edit { putString(playlist.id.toString(), playlist.toJson().toString()) }
			return playlist
		} catch(e: Exception) { logE("Playlists.create : ${e.message}") }
		return null
	}
	fun delete(id: Int) {
		all.removeIf { it.id == id }
		sharedPreferences.edit { remove(id.toString()) }
	}
}
