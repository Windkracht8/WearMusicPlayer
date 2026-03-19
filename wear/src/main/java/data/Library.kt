/*
 * Copyright 2024-2026 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.windkracht8.wearmusicplayer.data

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.windkracht8.wearmusicplayer.CommsBT
import com.windkracht8.wearmusicplayer.Main
import com.windkracht8.wearmusicplayer.R
import com.windkracht8.wearmusicplayer.error
import com.windkracht8.wearmusicplayer.logD
import com.windkracht8.wearmusicplayer.logE
import com.windkracht8.wearmusicplayer.logI
import com.windkracht8.wearmusicplayer.runInBackground
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Library {
	private val exStorageDir: String = Environment.getExternalStorageDirectory().toString()
	val musicDir: String = "$exStorageDir/Music/"
	val tracks = mutableStateListOf<Track>()
	var shuffleCounter by mutableIntStateOf(0)
	val artists = mutableStateListOf<Artist>()
	val albums = mutableStateListOf<Album>()
	val dirs = mutableStateListOf<Dir>()
	enum class Status { SCAN, READY, UPDATE }
	val status = MutableSharedFlow<Status>()
	private val projection: Array<String> = arrayOf(
		MediaStore.Audio.Media._ID,
		MediaStore.MediaColumns.DATA,
		MediaStore.Audio.Media.TITLE,
		MediaStore.Audio.Media.ARTIST,
		MediaStore.Audio.Media.ALBUM,
		MediaStore.Audio.Media.ALBUM_ARTIST,
		MediaStore.Audio.Media.CD_TRACK_NUMBER,
		MediaStore.Audio.Media.DISC_NUMBER
	)

	suspend fun scanMediaStore(context: Context) {
		logD { "Library.scanMediaStore" }
		status.emit(Status.SCAN)
		tracks.clear()
		artists.clear()
		albums.clear()
		dirs.clear()
		scanUri(context, MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
		status.emit(Status.READY)
	}
	suspend fun scanFiles(context: Context) {
		logD { "Library.scanFiles" }
		status.emit(Status.SCAN)
		MediaScannerConnection.scanFile(
			context,
			arrayOf(exStorageDir),
			null
		) { _: String, _: Uri? -> runInBackground { scanMediaStore(context) } }
	}
	fun scanFile(context: Context, path: String) {
		logD { "Library.scanFile: $path" }
		MediaScannerConnection.scanFile(
			context,
			arrayOf("$musicDir$path"),
			null
		) { _: String, uri: Uri? ->
			if(uri == null) {
				logD { "Library.scanFile path does not exists" }
				removeTrack(path)
			} else {
				logD { "Library.scanFile path exists" }
				runInBackground {
					scanUri(context, uri)
					status.emit(Status.UPDATE)
				}
			}
		}
	}
	private suspend fun scanUri(context: Context, uri: Uri) {
		try {
			context.contentResolver.query(
				uri,
				projection,
				null,
				null,
				null,
				null
			).use { cursor ->
				//logD{"Library.scanMediaStore query done"}
				if(cursor == null) {
					logE("Library.scanUri: Cursor is null")
					error.emit(R.string.fail_scan_media)
					return
				}
				val id = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
				val data = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
				val title = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
				val artist = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
				val album = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
				val albumArtist = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
				val cdTrackNumber = cursor.getColumnIndex(MediaStore.Audio.Media.CD_TRACK_NUMBER)
				val discNumber = cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER)
				while(cursor.moveToNext()) {
					val uri = ContentUris.withAppendedId(
						MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
						cursor.getLong(id)
					)
					if(tracks.any { it.uri == uri }) continue
					Track(
						context,
						uri,
						cursor.getString(data),
						cursor.getString(title),
						cursor.getString(artist),
						cursor.getString(album),
						cursor.getString(albumArtist),
						cursor.getString(cdTrackNumber),
						cursor.getString(discNumber)
					)
				}
			}
		} catch(e: Exception) {
			logE("Library.scanUri cursor: ${e.message}")
			error.emit(R.string.fail_scan_media)
		}
		try {
			//logD{"Library.scanUri sort"}
			tracks.sort()
			shuffleCounter = 0
			artists.sort()
			albums.sort()
			artists.forEach { it.sort() }
			albums.forEach { it.sort() }
			dirs.sort()
			dirs.forEach { it.shuffleCounter = 0 }
			Playlists.init(context)
		} catch(e: Exception) {
			logE("Library.scanUri sort: ${e.message}")
			error.emit(R.string.fail_scan_media)
		}
	}
	fun ensurePath(path: String): Int {
		val file = File("$musicDir$path")
		if(file.exists()) return CommsBT.CODE_FILE_EXISTS
		val parentFile: File = file.parentFile ?: return CommsBT.CODE_FAIL
		if(!parentFile.exists() && !parentFile.mkdirs()) return CommsBT.CODE_FAIL_CREATE_DIRECTORY
		if(!file.createNewFile()) return CommsBT.CODE_FAIL_CREATE_FILE
		return CommsBT.CODE_PENDING
	}
	fun deleteFile(main: Main, path: String): Int {
		val file = File("$musicDir$path")
		if(!file.exists()) {
			logI { "Library.deleteFile: path does not exists" }
			return CommsBT.CODE_OK
		}
		val uri = getUriForPath(path) ?: return CommsBT.CODE_FAIL
		if(main.checkUriPermission(
				uri,
				Process.myPid(),
				Process.myUid(),
				Intent.FLAG_GRANT_WRITE_URI_PERMISSION
			) == PackageManager.PERMISSION_DENIED
		) {
			try {
				main.deleteRequestResult.launch(
					IntentSenderRequest.Builder(
						MediaStore.createDeleteRequest(
							main.contentResolver,
							listOf<Uri?>(uri)
						).intentSender
					).build()
				)
			} catch(e: Exception) {
				logE("Library.deleteFile: startIntentSenderForResult: ${e.message}")
				return CommsBT.CODE_FAIL
			}
			return CommsBT.CODE_PENDING
		}
		if(!file.delete()) return CommsBT.CODE_FAIL_DEL_FILE
		scanFile(main, "$musicDir$path")
		return CommsBT.CODE_OK
	}
	private fun removeTrack(path: String) {
		tracks.removeIf { it.path == path }
		artists.forEach { artist ->
			artist.tracks.removeIf { t -> t.path == path }
			artist.albums.forEach { album ->
				album.tracks.removeIf { t -> t.path == path }
			}
			artist.albums.removeIf { it.tracks.isEmpty() }
		}
		artists.removeIf { it.tracks.isEmpty() }
		albums.forEach { album ->
			album.tracks.removeIf { t -> t.path == path }
		}
		albums.removeIf { it.tracks.isEmpty() }
		runInBackground { status.emit(Status.UPDATE) }
	}

	fun getTracksJson(): JSONArray {
		val array = JSONArray()
		tracks.forEach { array.put(it.toJson()) }
		return array
	}

	class Track(
		context: Context,
		val uri: Uri,
		path: String,
		var title: String, artistName: String?, albumName: String?, albumArtist: String?,
		trackNo: String?, discNo: String?
	) : Comparable<Track> {
		val path: String = path.substring(musicDir.length)
		val artist: Artist
		var album: Album? = null
		val trackNo: String?
		val discNo: String?

		init {
			this.title = title
			this.trackNo = trackNo
			this.discNo = discNo
			artist = getArtistForNewTrack(this, artistName)
			if(albumName != null && albumName != "Music") {
				album = getAlbumForNewTrack(context, this, albumName, albumArtist)
				artist.addAlbum(album as Album)
			}
			tracks.add(this)

			val dir = this.path.substringBeforeLast("/", "")
			if(dir.isNotEmpty()) {
				if(dirs.none { it.name == dir }) dirs.add(Dir(dir))
				dirs.first { it.name == dir }.tracks.add(this)
			}
		}
		fun toJson(): JSONObject {
			val ret = JSONObject()
			ret.put("path", path)
			return ret
		}
		override fun compareTo(other: Track): Int {
			var compare = humanCompare(album?.name, other.album?.name)
			if(compare != 0) return compare
			compare = humanCompare(discNo, other.discNo)
			if(compare != 0) return compare
			compare = humanCompare(trackNo, other.trackNo)
			if(compare != 0) return compare
			return humanCompare(title, other.title)
		}
	}

	class Artist(track: Track, artistName: String?) : Comparable<Artist> {
		val id: Int = artists.size
		val name: String = artistName ?: "<empty>"
		val tracks = mutableListOf<Track>()
		var shuffleCounter by mutableIntStateOf(0)
		val albums = mutableListOf<Album>()

		init {
			tracks.add(track)
			artists.add(this)
		}
		fun addAlbum(album: Album) {
			if(albums.contains(album)) return
			albums.add(album)
		}
		override fun compareTo(other: Artist): Int = name.compareTo(other.name, ignoreCase = true)
		fun sort() {
			tracks.sort()
			shuffleCounter = 0
			albums.sort()
		}
	}

	fun getArtistForNewTrack(track: Track, artistName: String?): Artist {
		val artist = artists.find { it.name == artistName }
		artist?.tracks?.add(track)
		return artist ?: Artist(track, artistName)
	}

	class Album(
		track: Track,
		albumName: String?,
		albumArtist: String?
	) : Comparable<Album> {
		val id: Int = albums.size
		val name: String = albumName ?: "<empty>"
		var artist: String = albumArtist ?: track.artist.name
		val tracks = mutableListOf<Track>()
		var shuffleCounter by mutableIntStateOf(0)

		init {
			tracks.add(track)
			albums.add(this)
		}
		override fun compareTo(other: Album): Int = name.compareTo(other.name, ignoreCase = true)
		fun sort() {
			tracks.sort()
			shuffleCounter = 0
		}
	}

	fun getAlbumForNewTrack(
		context: Context,
		track: Track,
		albumName: String?,
		albumArtist: String?
	): Album {
		return albums.find { it.name == albumName }?.apply {
			tracks.add(track)
			if(albumArtist != null && artist != albumArtist) {
				artist = context.getString(R.string.various_artists)
			}
		} ?: Album(track, albumName, albumArtist)
	}

	class Dir(val name: String) : Comparable<Dir> {
		val id: Int = dirs.size
		val tracks = mutableListOf<Track>()
		var shuffleCounter by mutableIntStateOf(0)
		override fun compareTo(other: Dir): Int = name.compareTo(other.name, ignoreCase = true)
	}

	private fun getUriForPath(path: String): Uri? = tracks.find { it.path == path }?.uri
	fun getFreeSpace() = File(exStorageDir).freeSpace

	fun humanCompare(value: String?, other: String?): Int {
		if(value == null && other == null) return 0
		if(value == null) return -1
		if(other == null) return 1
		val valueInt = value.toIntOrNull()
		val otherInt = other.toIntOrNull()
		if(valueInt == null && otherInt != null) return -1
		if(valueInt != null && otherInt == null) return 1
		if(valueInt != null && otherInt != null) return valueInt.compareTo(otherInt)
		val regex = Regex("(\\d+)|(\\D+)")
		val parts1 = regex.findAll(value).map { it.value }.toList()
		val parts2 = regex.findAll(other).map { it.value }.toList()
		for(i in 0 until minOf(parts1.size, parts2.size)) {
			val p1 = parts1[i]
			val p2 = parts2[i]
			val result =
				if(p1[0].isDigit() && p2[0].isDigit()) p1.toInt().compareTo(p2.toInt())
				else p1.compareTo(p2, ignoreCase = true)
			if(result != 0) return result
		}
		return parts1.size - parts2.size
	}
}