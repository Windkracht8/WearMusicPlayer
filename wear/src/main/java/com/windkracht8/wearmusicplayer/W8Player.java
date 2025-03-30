package com.windkracht8.wearmusicplayer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
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
    private static final String NOTIFICATION_CHANNEL_ID = "WMP_Notification";
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
                    NOTIFICATION_CHANNEL_ID
                    ,getString(R.string.playing_track)
                    ,NotificationManager.IMPORTANCE_HIGH)
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

    @OptIn(markerClass = UnstableApi.class) @Override public void onUpdateNotification(
            @NonNull MediaSession session,
            boolean startInForegroundRequired
    ){
        if(session.getPlayer().isPlaying()){
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    getBaseContext()
                    ,0
                    ,new Intent(this, Main.class)
                    ,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            Status ongoingActivityStatus = new Status.Builder()
                    .addTemplate(getString(R.string.playing_track))
                    .build();
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                    getBaseContext()
                    , NOTIFICATION_CHANNEL_ID
            )
                    .setSmallIcon(R.drawable.icon_vector)
                    .setStyle(new MediaStyleNotificationHelper.MediaStyle(mediaSession))
                    .setOnlyAlertOnce(true)
                    .setContentIntent(pendingIntent)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true);
            if(ongoingActivity == null){
                ongoingActivity = new OngoingActivity.Builder(
                        getBaseContext()
                        ,9
                        ,notificationBuilder
                )
                        .setStaticIcon(R.drawable.icon_vector)
                        .setTouchIntent(pendingIntent)
                        .setStatus(ongoingActivityStatus)
                        .build();
                ongoingActivity.apply(getBaseContext());
                ServiceCompat.startForeground(
                        this
                        ,9
                        ,notificationBuilder.build()
                        ,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                );
            }else{
                notificationManager.notify(9, notificationBuilder.build());
                ongoingActivity.update(getBaseContext(), ongoingActivityStatus);
            }
        }else if(ongoingActivity != null){
            Status ongoingActivityStatus = new Status.Builder()
                    .addTemplate(getString(R.string.paused_track))
                    .build();
            ongoingActivity.update(getBaseContext(), ongoingActivityStatus);
        }
    }
}
