/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.provider.MediaStore
import android.provider.MediaStore.MediaColumns
import android.provider.MediaStore.Audio.Media
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.collections.mutableListOf

object Library {
	val exStorageDir: String = Environment.getExternalStorageDirectory().toString()
	val musicDir: String = "$exStorageDir/Music/"
	val tracks = mutableListOf<Track>()
	var shuffleCounter by mutableIntStateOf(0)
	val artists = mutableListOf<Artist>()
	val albums = mutableListOf<Album>()
	val dirs = mutableListOf<Dir>()
	enum class Status { SCAN, READY, UPDATE }
	val status = MutableSharedFlow<Status>()
	val projection: Array<String> = arrayOf(
		Media._ID,
		MediaColumns.DATA,
		Media.TITLE,
		Media.ARTIST,
		Media.ALBUM,
		Media.ALBUM_ARTIST,
		Media.CD_TRACK_NUMBER,
		Media.DISC_NUMBER
	)

	suspend fun scanMediaStore(context: Context) {
		logD{"Library.scanMediaStore"}
		status.emit(Status.SCAN)
		tracks.clear()
		artists.clear()
		albums.clear()
		dirs.clear()
		scanUri(context, Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
		status.emit(Status.READY)
	}
	suspend fun scanFiles(context: Context) {
		logD{"Library.scanFiles"}
		status.emit(Status.SCAN)
		MediaScannerConnection.scanFile(
			context,
			arrayOf(exStorageDir),
			null
		) { _: String, _: Uri? -> runInBackground{ scanMediaStore(context) } }
	}
	fun scanFile(context: Context, path: String) {
		logD{"Library.scanFile: $path"}
		MediaScannerConnection.scanFile(
			context,
			arrayOf("$musicDir$path"),
			null
		) { _: String, uri: Uri? ->
			if (uri == null) {
				logD{"Library.scanFile path does not exists"}
				removeTrack(path)
			} else {
				logD{"Library.scanFile path exists"}
				runInBackground {
					scanUri(context, uri)
					status.emit(Status.UPDATE)
				}
			}
		}
	}
	suspend fun scanUri(context: Context, uri: Uri) {
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
				if (cursor == null) {
					logE("Library.scanUri: Cursor is null")
					error.emit(R.string.fail_scan_media)
					return
				}
				val id = cursor.getColumnIndex(Media._ID)
				val data = cursor.getColumnIndex(MediaColumns.DATA)
				val title = cursor.getColumnIndex(Media.TITLE)
				val artist = cursor.getColumnIndex(Media.ARTIST)
				val album = cursor.getColumnIndex(Media.ALBUM)
				val albumArtist = cursor.getColumnIndex(Media.ALBUM_ARTIST)
				val cdTrackNumber = cursor.getColumnIndex(Media.CD_TRACK_NUMBER)
				val discNumber = cursor.getColumnIndex(Media.DISC_NUMBER)
				while (cursor.moveToNext()) {
					val uri = ContentUris.withAppendedId(
						Media.EXTERNAL_CONTENT_URI,
						cursor.getLong(id)
					)
					if (tracks.any { it.uri == uri }) continue
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
		} catch (e: Exception) {
			logE("Library.scanUri cursor exception: " + e.message)
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
		} catch (e: Exception) {
			logE("Library.scanUri sort exception: " + e.message)
			error.emit(R.string.fail_scan_media)
		}
	}
	fun ensurePath(path: String): Int {
		val file = File("$musicDir$path")
		if (file.exists()) return CommsBT.CODE_FILE_EXISTS
		val parentFile: File = file.parentFile ?: return CommsBT.CODE_FAIL
		if (!parentFile.exists() && !parentFile.mkdirs()) return CommsBT.CODE_FAIL_CREATE_DIRECTORY
		if (!file.createNewFile()) return CommsBT.CODE_FAIL_CREATE_FILE
		return CommsBT.CODE_PENDING
	}
	fun deleteFile(main: Main, path: String): Int {
		val file = File("$musicDir$path")
		if (!file.exists()) {
			logI{"Library.deleteFile: path does not exists"}
			return CommsBT.CODE_OK
		}
		val uri = getUriForPath(path) ?: return CommsBT.CODE_FAIL
		if (main.checkUriPermission(
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
			} catch (e: Exception) {
				logE("Library.deleteFile: startIntentSenderForResult: " + e.message)
				return CommsBT.CODE_FAIL
			}
			return CommsBT.CODE_PENDING
		}
		if (!file.delete()) return CommsBT.CODE_FAIL_DEL_FILE
		scanFile(main, "$musicDir$path")
		return CommsBT.CODE_OK
	}
	fun removeTrack(path: String) {
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
			if (albumName != null && albumName != "Music") {
				album = getAlbumForNewTrack(context, this, albumName, albumArtist)
				artist.addAlbum(album as Album)
			}
			tracks.add(this)

			val dir = this.path.substringBeforeLast("/", "")
			if (dir.isNotEmpty()) {
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
			var compare = (album?.name ?: "").compareTo(other.album?.name ?: "")
			if (compare != 0) return compare
			compare = (discNo ?: "").compareTo(other.discNo ?: "")
			if (compare != 0) return compare

			compare = try {
				trackNo!!.toInt().compareTo(other.trackNo!!.toInt())
			} catch (_: Exception) {
				(trackNo ?: "").compareTo(other.trackNo ?: "")
			}
			if (compare != 0) return compare
			return title.compareTo(other.title)
		}
	}

	class Artist(track: Track, artistName: String?) : Comparable<Artist> {
		val id: Int = artists.size
		val name: String = artistName ?: "<empty>"//TODO from strings.xml or empty string
		val tracks = mutableListOf<Track>()
		var shuffleCounter by mutableIntStateOf(0)
		val albums = mutableListOf<Album>()

		init {
			tracks.add(track)
			artists.add(this)
		}
		fun addAlbum(album: Album) {
			if (albums.contains(album)) return
			albums.add(album)
		}
		override fun compareTo(other: Artist): Int = name.compareTo(other.name)
		fun sort() {
			tracks.sort()
			shuffleCounter = 0
			albums.sort()
		}
	}

	fun getArtistForNewTrack(track: Track, artistName: String?): Artist {
		val artist = artists.firstOrNull { it.name == artistName }
		artist?.tracks?.add(track)
		return artist ?: Artist(track, artistName)
	}

	class Album(
		track: Track,
		albumName: String?,
		albumArtist: String?
	) : Comparable<Album> {
		val id: Int = albums.size
		val name: String = albumName ?: "<empty>"//TODO from strings.xml or empty string
		var artist: String = albumArtist ?: track.artist.name
		val tracks = mutableListOf<Track>()
		var shuffleCounter by mutableIntStateOf(0)

		init {
			tracks.add(track)
			albums.add(this)
		}
		override fun compareTo(other: Album): Int = name.compareTo(other.name)
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
		return albums.firstOrNull { it.name == albumName }?.apply {
			tracks.add(track)
			if (albumArtist != null && artist != albumArtist) {
				artist = context.getString(R.string.various_artists)
			}
		} ?: Album(track, albumName, albumArtist)
	}

	class Dir(val name: String) : Comparable<Dir> {
		val id: Int = dirs.size
		val tracks = mutableListOf<Track>()
		var shuffleCounter by mutableIntStateOf(0)
		override fun compareTo(other: Dir): Int = name.compareTo(other.name)
	}

	fun getUriForPath(path: String): Uri? = tracks.firstOrNull { it.path == path }?.uri
	fun getFreeSpace() = File(exStorageDir).freeSpace
}
