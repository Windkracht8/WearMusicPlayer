/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.content.Context
import android.content.Context.MODE_PRIVATE
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

data class Playlist(
    var id: Int,
    var name: String,
    val trackPaths: SnapshotStateList<String> = mutableStateListOf()
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
    var maxId: Int = 0
    fun getNewId(): Int {
        maxId++
        sharedPreferences.edit { putInt("maxId", maxId) }
        return maxId
    }

    fun init(context: Context, onError: () -> Unit) {
        sharedPreferences = context.getSharedPreferences("Playlists", MODE_PRIVATE)
        all.clear()
        tryIgnore{ sharedPreferences.all.forEach { (key, value) ->
            try {
                if(key == "maxId"){
                    maxId = value as Int
                    return@forEach
                }
                val obj = JSONObject(value as String)
                val playlist = Playlist(
                    id = obj.getInt("id"),
                    name = obj.getString("name")
                )
                val tracks = obj.getJSONArray("tracks")
                for(index in 0 until tracks.length()) {
                    playlist.trackPaths.add(tracks.getString(index))
                }
                all.add(playlist)
            } catch(e: Exception) {
                logE("Playlists.init : ${e.message}")
                onError()
            }
        } }
    }

    fun create(name: String): Playlist {
        val playlist = Playlist(getNewId(), name)
        all.add(playlist)
        sharedPreferences.edit { putString(playlist.id.toString(), playlist.toJson().toString()) }
        return playlist
    }

    fun rename(id: Int, newName: String) {
        val playlist = all.firstOrNull { it.id == id } ?: return
        playlist.name = newName
        playlist.id = getNewId()
        sharedPreferences.edit {
            remove(id.toString())
            putString(playlist.id.toString(), playlist.toJson().toString())
        }
        if(CommsBT.status.value == CommsBT.Status.CONNECTED){
            if(all.isNotEmpty()) CommsBT.sendRequestPutPlaylist(playlist.id)
            CommsBT.sendRequestDelPlaylist(id)
        }
    }
    fun delete(id: Int) {
        all.removeIf { it.id == id }
        sharedPreferences.edit { remove(id.toString()) }
        if(CommsBT.status.value == CommsBT.Status.CONNECTED) CommsBT.sendRequestDelPlaylist(id)
    }
    fun addTrack(id: Int, path: String) {
        val playlist = all.firstOrNull { it.id == id } ?: return
        if(playlist.trackPaths.add(path)) {
            playlist.id = getNewId()
            sharedPreferences.edit {
                remove(id.toString())
                putString(playlist.id.toString(), playlist.toJson().toString())
            }
            if(CommsBT.status.value == CommsBT.Status.CONNECTED) {
                CommsBT.sendRequestPutPlaylist(playlist.id)
                CommsBT.sendRequestDelPlaylist(id)
            }
        }
    }
	fun removeTrack(id: Int, path: String) {
		val playlist = all.firstOrNull { it.id == id } ?: return
		if(playlist.trackPaths.remove(path)){
            playlist.id = getNewId()
            sharedPreferences.edit {
                remove(id.toString())
                putString(playlist.id.toString(), playlist.toJson().toString())
            }
            if(CommsBT.status.value == CommsBT.Status.CONNECTED){
                if(all.isNotEmpty()) CommsBT.sendRequestPutPlaylist(playlist.id)
                CommsBT.sendRequestDelPlaylist(id)
            }
        }
	}
}
