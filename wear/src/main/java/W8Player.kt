/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

const val NOTIFICATION_CHANNEL_ID = "WearMusicPlayer_Ongoing"
const val NOTIFICATION_ID = 8

@OptIn(UnstableApi::class)
class W8Player : MediaSessionService() {
	var handler: Handler = Handler(Looper.getMainLooper())
	var mediaSession: MediaSession? = null
	var notificationManager: NotificationManager? = null
	var ongoingActivity: OngoingActivity? = null

	@SuppressLint("MissingPermission")//handled by Permissions
	override fun onCreate() {
		logD{"W8player.onCreate"}
		super.onCreate()

		val audioAttributes = AudioAttributes.Builder()
			.setUsage(C.USAGE_MEDIA)
			.setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
			.build()
		val exoPlayer = ExoPlayer.Builder(this)
			.setAudioAttributes(audioAttributes, true)
			.build()
		mediaSession = MediaSession.Builder(this, exoPlayer).build()

		notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
		setChannelImportance(NotificationUtil.IMPORTANCE_LOW)
		ServiceCompat.startForeground(
			this, NOTIFICATION_ID, getNotificationBuilder().build(),
			ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
		)
	}

	override fun onDestroy() {
		logD{"W8player.onDestroy"}
		stop()
		mediaSession?.run {
			player.release()
			release()
			mediaSession = null
		}
		notificationManager?.cancelAll()
		super.onDestroy()
	}

	override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

	@SuppressLint("MissingPermission")//handled by Permissions
	override fun onUpdateNotification(
		session: MediaSession,
		startInForegroundRequired: Boolean
	) {
		var notificationBuilder: NotificationCompat.Builder? = null
		if (session.player.playbackState in listOf(Player.STATE_ENDED, Player.STATE_IDLE)) {
			setChannelImportance(NotificationUtil.IMPORTANCE_LOW)
			ongoingActivity?.let { stop() }
			return
		} else if (session.player.isPlaying) {
			handler.removeCallbacksAndMessages(null)
			setChannelImportance(NotificationUtil.IMPORTANCE_HIGH)
			notificationBuilder = getNotificationBuilder()
			val status: Status = Status.Builder().addTemplate(getString(R.string.playing_track)).build()
			ongoingActivity?.update(this, status)
				?: startOngoingActivity(notificationBuilder, getIntent(), status)
			notificationManager?.notify(NOTIFICATION_ID, notificationBuilder.build())
		} else if (ongoingActivity is OngoingActivity) { //not playing and ongoingActivity has been created
			setChannelImportance(NotificationUtil.IMPORTANCE_LOW)
			val status: Status = Status.Builder().addTemplate(getString(R.string.paused_track)).build()
			ongoingActivity?.update(this, status)
			notificationBuilder = getNotificationBuilder()
			notificationManager?.notify(NOTIFICATION_ID, notificationBuilder.build())
			handler.postDelayed(::stop, 180000) //stop after 3 minutes
		}
		if(startInForegroundRequired) {
			ServiceCompat.startForeground(
				this, NOTIFICATION_ID,
				notificationBuilder?.build() ?: getNotificationBuilder().build(),
				ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
			)
		}
	}
	fun startOngoingActivity(notificationBuilder: NotificationCompat.Builder, intent: PendingIntent, status: Status) {
		ongoingActivity = OngoingActivity.Builder(this, NOTIFICATION_ID, notificationBuilder)
			.setStaticIcon(R.drawable.icon_vector)
			.setTouchIntent(intent)
			.setStatus(status)
			.build()
		ongoingActivity?.apply(this)
	}
	fun getIntent(): PendingIntent {
		val intent = Intent(this, Main::class.java)
		val activityOptions = ActivityOptions.makeBasic()
		if (android.os.Build.VERSION.SDK_INT >= 36) {
			activityOptions.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE)
		} else if(android.os.Build.VERSION.SDK_INT >= 34) {
			activityOptions.setPendingIntentCreatorBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
		}
		return PendingIntent.getActivity(
			this, 0, intent,
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
			activityOptions.toBundle()
		)
	}

	fun stop() {
		logD{"W8Player.stop"}
		ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
		ongoingActivity = null
	}

	fun setChannelImportance(importance: Int){
		notificationManager?.createNotificationChannel(
			NotificationChannel(
				NOTIFICATION_CHANNEL_ID,
				getString(R.string.playing_track),
				importance
			)
		)
	}
	@OptIn(UnstableApi::class)
	fun getNotificationBuilder(): NotificationCompat.Builder {
		try {
			return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
				.setSmallIcon(R.drawable.icon_vector)
				.setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession!!))
				.setOnlyAlertOnce(true)
				.setContentIntent(getIntent())
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
				.setOngoing(true)
		} catch (e: Exception) {
			logD{"W8Player.getNotificationBuilder: $e"}
		}
		return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
			.setSmallIcon(R.drawable.icon_vector)
			.setOnlyAlertOnce(true)
			.setContentIntent(getIntent())
			.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
			.setOngoing(true)
	}
}
