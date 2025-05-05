/*
 *  Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 *  This file is part of WearMusicPlayer
 *  WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *  WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *  You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.windkracht8.wearmusicplayer;

import static androidx.media3.common.Player.STATE_ENDED;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;
import androidx.media3.session.MediaStyleNotificationHelper;
import androidx.wear.ongoing.OngoingActivity;
import androidx.wear.ongoing.Status;

public class W8Player extends MediaSessionService{
    private static final String NOTIFICATION_CHANNEL_ID = "WearMusicPlayer_Ongoing";
    private static final int NOTIFICATION_ID = 8;
    private Handler handler;
    private MediaSession mediaSession;
    private NotificationManager notificationManager;
    private OngoingActivity ongoingActivity;

    @OptIn(markerClass = UnstableApi.class) @Override public void onCreate(){
        Log.d(Main.LOG_TAG, "W8player.onCreate");
        super.onCreate();
        ExoPlayer exoPlayer = new ExoPlayer.Builder(this).build();
        mediaSession = new MediaSession.Builder(this, exoPlayer).build();

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean exists = false;
        for(NotificationChannel channel : notificationManager.getNotificationChannels()){
            String channelId = channel.getId();
            if(channelId.equals(NOTIFICATION_CHANNEL_ID)){
                exists = true;
            }else{
                notificationManager.deleteNotificationChannel(channelId);
            }
        }
        if(!exists){
            notificationManager.createNotificationChannel(new NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    getString(R.string.playing_track),
                    NotificationManager.IMPORTANCE_HIGH)
            );
        }
    }
    @Override public void onDestroy(){
        Log.d(Main.LOG_TAG, "W8player.onDestroy");
        super.onDestroy();
        mediaSession.getPlayer().release();
        mediaSession.release();
        notificationManager.cancelAll();
    }
    @Nullable @Override public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo){
        return mediaSession;
    }

    @Override public void onUpdateNotification(
            @NonNull MediaSession session,
            boolean startInForegroundRequired
    ){
        if(session.getPlayer().getPlaybackState() == STATE_ENDED){
            if(ongoingActivity != null) stop();
            return;
        }
        if(session.getPlayer().isPlaying()){
            if(handler != null) handler.removeCallbacksAndMessages(null);
            PendingIntent intent = getIntent();
            Status status = new Status.Builder().addTemplate(getString(R.string.playing_track)).build();
            NotificationCompat.Builder notiBuilder = getNotificationBuilder(intent);
            if(ongoingActivity == null){
                start(notiBuilder, intent, status);
            }else{//playing, ongoingActivity has been created
                ongoingActivity.update(this, status);
            }
            notificationManager.notify(NOTIFICATION_ID, notiBuilder.build());
        }else if(ongoingActivity != null){//not playing and ongoingActivity has been created
            Status status = new Status.Builder().addTemplate(getString(R.string.paused_track)).build();
            ongoingActivity.update(this, status);
            if(handler == null) handler = new Handler(Looper.getMainLooper());
            handler.postDelayed(this::stop, 180000);//cancel after 3 minutes
        }
    }
    private void start(NotificationCompat.Builder notiBuilder, PendingIntent intent, Status status){
        Log.d(Main.LOG_TAG, "W8Player.start");
        ongoingActivity = new OngoingActivity.Builder(this, NOTIFICATION_ID, notiBuilder)
                .setStaticIcon(R.drawable.icon_vector)
                .setTouchIntent(intent)
                .setStatus(status)
                .build();
        ongoingActivity.apply(this);
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notiBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        );
    }
    private void stop(){
        Log.d(Main.LOG_TAG, "W8Player.stop");
        notificationManager.cancel(NOTIFICATION_ID);
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        ongoingActivity = null;
    }
    private PendingIntent getIntent(){
        return PendingIntent.getActivity(this, 0,
                new Intent(this, Main.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
    @OptIn(markerClass = UnstableApi.class)
    private NotificationCompat.Builder getNotificationBuilder(PendingIntent intent){
        return new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_vector)
                .setStyle(new MediaStyleNotificationHelper.MediaStyle(mediaSession))
                .setOnlyAlertOnce(true)
                .setContentIntent(intent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true);
    }
}
