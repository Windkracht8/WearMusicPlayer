/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.Manifest.permission.BLUETOOTH
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.MANAGE_EXTERNAL_STORAGE
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_AUDIO
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.rememberSwipeDismissableNavHostState
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

var hasReadPermission = false
var hasBTPermission = false

class Main : ComponentActivity() {
	lateinit var navController: NavHostController
	lateinit var mediaControllerFuture: ListenableFuture<MediaController>
	var mediaController: MediaController? = null
	var currentTracks: List<Library.Track> by mutableStateOf(emptyList())

	enum class TrackListType { ALL, ARTIST, ALBUM }
	var currentTracksType = TrackListType.ALL
	var currentTracksId = -1
	var currentTrackId by mutableIntStateOf(-1)
	var currentTrackTitle by mutableStateOf("loading")
	var currentTrackArtist by mutableStateOf("loading")
	var hasPrevious by mutableStateOf(false)
	var hasNext by mutableStateOf(false)
	var isPlaying by mutableStateOf(false)

	override fun onCreate(savedInstanceState: Bundle?) {
		installSplashScreen()
		super.onCreate(savedInstanceState)

		setTheme(android.R.style.Theme_DeviceDefault)
		setContent {
			navController = rememberSwipeDismissableNavController()
			MainApp(navController, this)
		}

		mediaControllerFuture = MediaController.Builder(
			this,
			SessionToken(
				this,
				ComponentName(this, W8Player::class.java)
			)
		).buildAsync()
		mediaControllerFuture.addListener({
			try {
				mediaController = mediaControllerFuture.get()
				mediaController?.addListener(playerListener)
				loadTracks(this)
			} catch (e: Exception) {
				logE("Main.MediaController exception: " + e.message)
			}
		}, MoreExecutors.directExecutor())
		lifecycleScope.launch {
			Library.status.collect { libraryStatus ->
				logD("Main: Library status change: $libraryStatus")
				when (libraryStatus) {
					Library.Status.SCAN -> {
						currentTrackTitle = "loading"
						currentTrackArtist = "loading"
					}
					Library.Status.READY -> {
						if (currentTrackId == -1 || currentTrackTitle == "loading") {
							currentTracksType = TrackListType.ALL
							currentTracks = Library.tracks
							if (currentTracks.isEmpty()) {
								currentTrackTitle = "no tracks found"
								currentTrackArtist = "upload with phone app"
							} else {
								loadTracks(this@Main)
							}
						}
						if (CommsBT.commsBTConnected != null) CommsBT.sendSyncResponse()
					}
					Library.Status.UPDATE -> {
						if (currentTrackId > currentTracks.lastIndex) currentTrackId = -1
						if(currentTracks.isEmpty() && currentTracksType != TrackListType.ALL){
							currentTracksType = TrackListType.ALL
							currentTracks = Library.tracks
						}
						if (currentTracks.isEmpty()) {
							currentTrackTitle = "no tracks found"
							currentTrackArtist = "upload with phone app"
						}
						loadTracks(this@Main)
					}
				}
			}
		}
		lifecycleScope.launch { CommsBT.error.collect { toast(it) } }
		lifecycleScope.launch {
			CommsBT.deleteFilePath.collect {
				if (it.isEmpty()) return@collect
				val result: Int = Library.deleteFile(this@Main, it)
				if(result < 0){
					toast(R.string.fail_delete_file)
					logE("Main: Library.deleteFile result: $result")
				} else {
					logD("Main: Library.deleteFile result: $result")
				}
				CommsBT.sendResponse("deleteFile", result)
				if(result != CommsBT.CODE_PENDING) CommsBT.deleteFilePath.value = ""
			}
		}
		lifecycleScope.launch {
			CommsWifi.status.collect { wifiStatus ->
				logD("Main: CommsWifi status change: $wifiStatus")
				when (wifiStatus) {
					CommsWifi.Status.PREPARING ->
						startActivity(Intent(this@Main, Progress::class.java))
					CommsWifi.Status.ERROR -> {
						toast(CommsWifi.error)
						CommsBT.sendResponse("fileBinary", CommsBT.CODE_FAIL)
					}
					CommsWifi.Status.DONE -> {
						CommsBT.sendFileBinaryResponse()
						Library.scanFile(this@Main, CommsWifi.path)
					}
					else -> {}
				}
			}
		}
		checkPermissions()
		if (hasReadPermission) {
			CoroutineScope(Dispatchers.Default).launch {
				Library.scanMediaStore(this@Main)
			}
		}
		if (hasBTPermission) {
			registerReceiver(
				btBroadcastReceiver,
				IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
			)
			CoroutineScope(Dispatchers.Default).launch { CommsBT.start(this@Main) }
		}
	}
	override fun onStop() {
		//logD("MainActivity.onStop")
		super.onStop()
		if (!isPlaying) mediaController?.stop() //stop notification when MainActivity is stopped
	}
	override fun onDestroy() {
		//logD("MainActivity.onDestroy")
		super.onDestroy()
		try { MediaController.releaseFuture(mediaControllerFuture) }
		catch (_: Exception) {}
		try { unregisterReceiver(btBroadcastReceiver) }
		catch (_: Exception) {}
		try { CommsBT.stop() }
		catch (_: Exception) {}
	}

	fun previous() {
		if (currentTrackId > 0) {
			currentTrackId--
			currentTrackTitle = currentTracks[currentTrackId].title
			currentTrackArtist = currentTracks[currentTrackId].artist.name
			hasPrevious = currentTrackId > 0
		}
		mediaController?.seekToPreviousMediaItem()
	}

	fun next() {
		if (currentTrackId < currentTracks.lastIndex) {
			currentTrackId++
			currentTrackTitle = currentTracks[currentTrackId].title
			currentTrackArtist = currentTracks[currentTrackId].artist.name
			hasNext = currentTrackId < currentTracks.lastIndex
		}
		mediaController?.seekToNext()
	}

	fun playPause() {
		isPlaying = !isPlaying
		if (isPlaying) { mediaController?.play() }
		else { mediaController?.pause() }
	}

	fun rescan() =
		CoroutineScope(Dispatchers.Default).launch { Library.scanFiles(this@Main) }

	fun loadTracks(activity: ComponentActivity) {
		if (mediaController == null) return
		val mediaItems = ArrayList<MediaItem>()
		currentTracks.forEach { mediaItems.add(MediaItem.fromUri(it.uri)) }
		activity.runOnUiThread {
			logD("Main.loadTracks loading " + mediaItems.size + " items")
			mediaController?.clearMediaItems()
			if(currentTracks.isNotEmpty()) {
				mediaController?.addMediaItems(mediaItems)
				mediaController?.prepare()
				mediaController?.seekTo(if (currentTrackId == -1) 0 else currentTrackId, 0)
			}
		}
	}

	fun openTracks(type: TrackListType, id: Int, index: Int) {
		currentTracksType = type
		currentTracksId = id
		currentTrackId = index
		currentTracks = when (type) {
			TrackListType.ALL -> Library.tracks
			TrackListType.ARTIST -> Library.artists.firstOrNull { it.id == id }?.tracks
				?: emptyList()

			TrackListType.ALBUM -> Library.albums.firstOrNull { it.id == id }?.tracks ?: emptyList()
		}
		val mediaItems = ArrayList<MediaItem>()
		currentTracks.forEach { mediaItems.add(MediaItem.fromUri(it.uri)) }
		logD("Main.openTracks loading " + mediaItems.size + " items")
		mediaController?.clearMediaItems()
		mediaController?.addMediaItems(mediaItems)
		mediaController?.prepare()
		mediaController?.seekTo(currentTrackId, 0)
		mediaController?.play()
	}

	val playerListener: Player.Listener = object : Player.Listener {
		override fun onIsPlayingChanged(isPlayingMedia: Boolean) {
			//logD("Player.Listener.onIsPlayingChanged $isPlayingMedia")
			isPlaying = isPlayingMedia
		}
		override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
			//logD("Player.Listener.onMediaMetadataChanged " + mediaMetadata.title)
			if (mediaMetadata.title != null) {
				currentTrackTitle = mediaMetadata.title.toString()
				currentTrackArtist = mediaMetadata.artist.toString()
			}
			currentTrackId = mediaController?.currentMediaItemIndex ?: currentTrackId
			hasPrevious = currentTrackId > 0
			hasNext = currentTrackId < currentTracks.lastIndex
		}
		override fun onPlayerError(error: PlaybackException) {
			logE("Player.Listener.onPlayerError: $error")
			logE("Player.Listener.onPlayerError: " + error.message)
		}
	}

	fun checkPermissions() {
		if (Build.VERSION.SDK_INT >= 33) {
			hasReadPermission = hasPermission(READ_MEDIA_AUDIO)
			hasBTPermission = hasPermission(BLUETOOTH_CONNECT)
			if (!hasReadPermission || !hasBTPermission || !hasPermission(POST_NOTIFICATIONS)) {
				requestMultiplePermissions.launch(
					arrayOf(
						POST_NOTIFICATIONS,
						READ_MEDIA_AUDIO,
						BLUETOOTH_CONNECT
					)
				)
			}
		} else if (Build.VERSION.SDK_INT >= 31) {
			hasReadPermission = hasPermission(MANAGE_EXTERNAL_STORAGE)
			hasBTPermission = hasPermission(BLUETOOTH_CONNECT)
			if (!hasReadPermission || !hasBTPermission) {
				requestMultiplePermissions.launch(
					arrayOf(
						MANAGE_EXTERNAL_STORAGE,
						BLUETOOTH_CONNECT
					)
				)
			}
		} else { //30
			hasReadPermission = hasPermission(READ_EXTERNAL_STORAGE)
			hasBTPermission = hasPermission(BLUETOOTH)
			if (!hasReadPermission || !hasBTPermission) {
				requestMultiplePermissions.launch(
					arrayOf(
						READ_EXTERNAL_STORAGE,
						BLUETOOTH
					)
				)
			}
		}
	}

	val requestMultiplePermissions =
		registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
			permissions.entries.forEach {
				if (it.value && it.key == READ_MEDIA_AUDIO) {
					hasReadPermission = true
					CoroutineScope(Dispatchers.Default).launch {
						Library.scanMediaStore(this@Main)
					}
				}
				if (it.value && (it.key == BLUETOOTH_CONNECT || it.key == BLUETOOTH)) {
					hasBTPermission = true
					CoroutineScope(Dispatchers.Default).launch {
						CommsBT.start(this@Main)
					}
				}
			}
		}

	val deleteRequestResult =
		registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
			if (result.resultCode == RESULT_OK) {
				CommsBT.sendResponse("deleteFile", CommsBT.CODE_OK)
				logD("Main: CommsBT.deleteFilePath.value: " + CommsBT.deleteFilePath.value)
				if (CommsBT.deleteFilePath.value.isNotEmpty()) {
					val path = CommsBT.deleteFilePath.value
					CoroutineScope(Dispatchers.Default).launch {
						Library.scanFile(this@Main, path)
					}
					CommsBT.deleteFilePath.value = ""
				}
			} else {
				CommsBT.sendResponse("deleteFile", CommsBT.CODE_DECLINED)
			}
		}
	val btBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
		override fun onReceive(context: Context?, intent: Intent) {
			if (BluetoothAdapter.ACTION_STATE_CHANGED == intent.action) {
				val btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)
				if (btState == BluetoothAdapter.STATE_TURNING_OFF) { CommsBT.stop() }
				else if (btState == BluetoothAdapter.STATE_ON) { CommsBT.start(this@Main) }
			}
		}
	}
}

@Composable
fun MainApp(
	navController: NavHostController,
	main: Main
) {
	val navHostState = rememberSwipeDismissableNavHostState()
	W8Theme {
		AppScaffold {
			SwipeDismissableNavHost(
				navController = navController,
				startDestination = "home",
				state = navHostState,
			) {
				composable("home") {
					Home(
						onLibraryClick = { navController.navigate("menu") },
						onTrackClick = {
							when (main.currentTracksType) {
								Main.TrackListType.ALL ->
									navController.navigate(
										"menu_all/" +
												main.currentTrackId
									)

								Main.TrackListType.ARTIST ->
									navController.navigate(
										"menu_artist/" +
												main.currentTracksId +
												"/" + main.currentTrackId
									)

								Main.TrackListType.ALBUM ->
									navController.navigate(
										"menu_album/" +
												main.currentTracksId +
												"/" + main.currentTrackId
									)
							}
						},
						onPreviousClick = main::previous,
						onPlayPauseClick = main::playPause,
						onNextClick = main::next,
						hasPrevious = main.hasPrevious,
						hasNext = main.hasNext,
						isPlaying = main.isPlaying,
						currentTrackTitle = main.currentTrackTitle,
						currentTrackArtist = main.currentTrackArtist,
						audioManager = navController.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
					)
				}
				composable("menu") {
					Menu(
						onMenuAllClick = { navController.navigate("menu_all/0") },
						onMenuAlbumsClick = { navController.navigate("menu_albums") },
						onMenuArtistsClick = { navController.navigate("menu_artists") },
						onRescanClick = {
							main.rescan()
							navController.popBackStack()
						}
					)
				}
				composable("menu_all/{trackId}") {
					MenuAll(
						onRandomiseClick = {
							Library.tracks.shuffle()
							navController.popBackStack()
							navController.navigate("menu_all/0")
						},
						openTracks = { type, id, index ->
							main.openTracks(type, id, index)
							navController.popBackStack("home", inclusive = false)
						},
						trackId = it.arguments?.getString("trackId")?.toIntOrNull() ?: -1
					)
				}
				composable("menu_albums") {
					MenuAlbums(
						onMenuAlbumClick = { id -> navController.navigate("menu_album/$id/0") }
					)
				}
				composable("menu_album/{id}/{trackId}") {
					MenuAlbum(
						id = it.arguments?.getString("id")?.toIntOrNull() ?: -1,
						openTracks = { type, id, index ->
							main.openTracks(type, id, index)
							navController.popBackStack("home", inclusive = false)
						},
						trackId = it.arguments?.getString("trackId")?.toIntOrNull() ?: -1
					)
				}
				composable("menu_artists") {
					MenuArtists(
						onMenuArtistClick = { id -> navController.navigate("menu_artist/$id/0") }
					)
				}
				composable("menu_artist/{id}/{trackId}") {
					MenuArtist(
						id = it.arguments?.getString("id")?.toIntOrNull() ?: -1,
						openTracks = { type, id, index ->
							main.openTracks(type, id, index)
							navController.popBackStack("home", inclusive = false)
						},
						onMenuAlbumClick = { id -> navController.navigate("menu_album/$id/0") },
						trackId = it.arguments?.getString("trackId")?.toIntOrNull() ?: -1
					)
				}
			}
		}
	}
}
