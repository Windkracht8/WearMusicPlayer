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
import androidx.activity.result.IntentSenderRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object Library {
	val exStorageDir: String = Environment.getExternalStorageDirectory().toString()
	val musicDir: String = "$exStorageDir/Music/"
	val tracks: MutableList<Track> = mutableListOf<Track>()
	val artists: MutableList<Artist> = mutableListOf<Artist>()
	val albums: MutableList<Album> = mutableListOf<Album>()
	enum class Status { SCAN, READY, UPDATE }
	val status = MutableSharedFlow<Status>()
	val projection: Array<String> = arrayOf(
		MediaStore.Audio.Media._ID,
		MediaStore.MediaColumns.DATA,
		MediaStore.Audio.Media.TITLE,
		MediaStore.Audio.Media.ARTIST,
		MediaStore.Audio.Media.ALBUM,
		MediaStore.Audio.Media.ALBUM_ARTIST,
		MediaStore.Audio.Media.CD_TRACK_NUMBER,
		MediaStore.Audio.Media.DISC_NUMBER
	)

	fun scanMediaStore(context: Context) {
		logD("Library.scanMediaStore")
		CoroutineScope(Dispatchers.Default).launch { status.emit(Status.SCAN) }
		tracks.clear()
		artists.clear()
		albums.clear()

		scanUri(context, MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL))
		//logD("Library.scanMediaStore ready")
		CoroutineScope(Dispatchers.Default).launch { status.emit(Status.READY) }
	}
	fun scanFiles(context: Context) {
		logD("Library.scanFiles")
		CoroutineScope(Dispatchers.Default).launch { status.emit(Status.SCAN) }
		MediaScannerConnection.scanFile(
			context,
			arrayOf(exStorageDir),
			null
		) { p: String, u: Uri? -> scanMediaStore(context) }
	}
	fun scanFile(context: Context, path: String) {
		logD("Library.scanFile: $path")
		MediaScannerConnection.scanFile(
			context,
			arrayOf("$musicDir$path"),
			null
		) { p: String, uri: Uri? ->
			if (uri == null) {
				logD("Library.scanFile path does not exists")
				removeTrack(path)
			} else {
				logD("Library.scanFile path exists")
				scanUri(context, uri)
				CoroutineScope(Dispatchers.Default).launch { status.emit(Status.UPDATE) }
			}
		}
	}
	fun scanUri(context: Context, uri: Uri) {
		try {
			context.contentResolver.query(
				uri,
				projection,
				null,
				null,
				null,
				null
			).use { cursor ->
				//logD("Library.scanMediaStore query done")
				if (cursor == null) {
					logE("Library.scanUri: Cursor is null")
					context.toast(R.string.fail_scan_media)
					return
				}
				val id: Int = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
				val data: Int = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
				val title: Int = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
				val artist: Int = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
				val album: Int = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
				val albumArtist: Int = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ARTIST)
				val cdTrackNumber: Int =
					cursor.getColumnIndex(MediaStore.Audio.Media.CD_TRACK_NUMBER)
				val discNumber: Int = cursor.getColumnIndex(MediaStore.Audio.Media.DISC_NUMBER)
				while (cursor.moveToNext()) {
					val uri = ContentUris.withAppendedId(
						MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
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
			context.toast(R.string.fail_scan_media)
		}
		try {
			//logD("Library.scanUri sort")
			tracks.sort()
			artists.sort()
			albums.sort()
			artists.forEach { it.sort() }
			albums.forEach { it.sort() }
		} catch (e: Exception) {
			logE("Library.scanUri sort exception: " + e.message)
			context.toast(R.string.fail_scan_media)
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
			logI("Library.deleteFile: path does not exists")
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
							ArrayList(listOf<Uri?>(uri))
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
		CoroutineScope(Dispatchers.Default).launch { status.emit(Status.UPDATE) }
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
		val name: String = artistName ?: "<empty>"
		val tracks: ArrayList<Track> = ArrayList()
		val albums: ArrayList<Album> = ArrayList()

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
		val name: String = albumName ?: "<empty>"
		var artist: String = albumArtist ?: track.artist.name
		val tracks: ArrayList<Track> = ArrayList()

		init {
			tracks.add(track)
			albums.add(this)
		}
		override fun compareTo(other: Album): Int = name.compareTo(other.name)
		fun sort() = tracks.sort()
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

	fun getUriForPath(path: String): Uri? = tracks.firstOrNull { it.path == path }?.uri
	fun getFreeSpace() = File(exStorageDir).freeSpace
}
