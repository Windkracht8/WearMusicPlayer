/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.windkracht8.wearmusicplayer

import android.annotation.SuppressLint
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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.MediaStyleNotificationHelper
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

const val NOTIFICATION_CHANNEL_ID = "WearMusicPlayer_Ongoing"
const val NOTIFICATION_ID = 8

class W8Player : MediaSessionService() {
	var handler: Handler = Handler(Looper.getMainLooper())
	var mediaSession: MediaSession? = null
	var notificationManager: NotificationManager? = null
	var ongoingActivity: OngoingActivity? = null

	override fun onCreate() {
		logD{"W8player.onCreate"}
		super.onCreate()

		val exoPlayer = ExoPlayer.Builder(this).build()
		mediaSession = MediaSession.Builder(this, exoPlayer).build()
		notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

		var exists = false
		for (channel in notificationManager?.notificationChannels ?: emptyList()) {
			val channelId: String = channel.id
			if (channelId == NOTIFICATION_CHANNEL_ID) {
				exists = true
			} else {
				notificationManager?.deleteNotificationChannel(channelId)
			}
		}
		if (!exists) {
			notificationManager?.createNotificationChannel(
				NotificationChannel(
					NOTIFICATION_CHANNEL_ID,
					getString(R.string.playing_track),
					NotificationManager.IMPORTANCE_HIGH
				)
			)
		}
	}

	override fun onDestroy() {
		logD{"W8player.onDestroy"}
		super.onDestroy()
		mediaSession?.player?.release()
		mediaSession?.release()
		notificationManager?.cancelAll()
	}

	override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

	@SuppressLint("MissingPermission")//handled by Permissions
	override fun onUpdateNotification(
		session: MediaSession,
		startInForegroundRequired: Boolean
	) {
		if (session.player.playbackState in listOf(Player.STATE_ENDED, Player.STATE_IDLE)) {
			ongoingActivity?.let { stop() }
		} else if (session.player.isPlaying) {
			handler.removeCallbacksAndMessages(null)
			val intent = PendingIntent.getActivity(
				this, 0,
				Intent(this, Main::class.java),
				PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
			)
			val notificationBuilder = getNotificationBuilder(intent)
			val status: Status = Status.Builder().addTemplate(getString(R.string.playing_track)).build()
			ongoingActivity?.update(this, status) ?: start(
				notificationBuilder,
				intent,
				status
			) //update if exists, else start
			notificationManager?.notify(NOTIFICATION_ID, notificationBuilder.build())
		} else if (ongoingActivity is OngoingActivity) { //not playing and ongoingActivity has been created
			val status: Status = Status.Builder().addTemplate(getString(R.string.paused_track)).build()
			ongoingActivity?.update(this, status)
			handler.postDelayed(::stop, 180000) //cancel after 3 minutes
		}
	}

	fun start(
		notificationBuilder: NotificationCompat.Builder,
		intent: PendingIntent,
		status: Status
	) {
		//logD{"W8Player.start"}
		ongoingActivity = OngoingActivity.Builder(this, NOTIFICATION_ID, notificationBuilder)
			.setStaticIcon(R.drawable.icon_vector)
			.setTouchIntent(intent)
			.setStatus(status)
			.build()
		ongoingActivity?.apply(this)
		ServiceCompat.startForeground(
			this, NOTIFICATION_ID, notificationBuilder.build(),
			ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
		)
	}

	fun stop() {
		logD{"W8Player.stop"}
		notificationManager?.cancel(NOTIFICATION_ID)
		ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
		ongoingActivity = null
	}

	@OptIn(UnstableApi::class)
	fun getNotificationBuilder(intent: PendingIntent?): NotificationCompat.Builder {
		try {
			return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
				.setSmallIcon(R.drawable.icon_vector)
				.setStyle(MediaStyleNotificationHelper.MediaStyle(mediaSession!!))
				.setOnlyAlertOnce(true)
				.setContentIntent(intent)
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
				.setOngoing(true)
		} catch (_: Exception) {
			return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
				.setSmallIcon(R.drawable.icon_vector)
				.setOnlyAlertOnce(true)
				.setContentIntent(intent)
				.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
				.setOngoing(true)
		}
	}
}
