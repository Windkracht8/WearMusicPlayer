package com.windkracht8.wearmusicplayer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
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
    private MediaSession mediaSession;
    private static final String channel_id = "WMP_Notification";
    private static NotificationManager notificationManager;
    private OngoingActivity ongoingActivity;

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onCreate(){
        super.onCreate();
        ExoPlayer exoPlayer = new ExoPlayer.Builder(this).build();
        MediaSession.Builder mediaSessionBuilder = new MediaSession.Builder(this, exoPlayer);
        mediaSessionBuilder.setCallback(new MediaSession.Callback(){
            @NonNull
            @Override
            public MediaSession.ConnectionResult onConnect(@NonNull MediaSession session, @NonNull MediaSession.ControllerInfo controller){
                Log.d(Main.LOG_TAG, "onConnect: " + controller);
                MediaSession.ConnectionResult ret = MediaSession.Callback.super.onConnect(session, controller);
                Log.d(Main.LOG_TAG, "ConnectionResult: " + ret.isAccepted);
                return ret;
            }
        });
        mediaSession = mediaSessionBuilder.build();

        if(notificationManager == null){
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.createNotificationChannel(new NotificationChannel(
                    channel_id
                    ,getString(R.string.open_wmp)
                    ,NotificationManager.IMPORTANCE_DEFAULT)
            );
        }
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        mediaSession.getPlayer().release();
        mediaSession.release();
        notificationManager.cancelAll();
    }
    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo){
        Log.d(Main.LOG_TAG, "onGetSession: " + controllerInfo);
        return mediaSession;
    }

    @OptIn(markerClass = UnstableApi.class)
    @Override
    public void onUpdateNotification(
            @NonNull MediaSession session,
            boolean startInForegroundRequired
    ){
        //if(Build.VERSION.SDK_INT >= 33){
            //super.onUpdateNotification(session, startInForegroundRequired);
            //return;
        //}

        boolean isPlaying = mediaSession.getPlayer().isPlaying();
        Status ongoingActivityStatus = new Status.Builder()
                .addTemplate(isPlaying ? getString(R.string.playing_track) : getString(R.string.paused_track))
                .build();

        if(!startInForegroundRequired || !isPlaying){
            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                    getBaseContext()
                    ,channel_id
            )
                    .setSmallIcon(R.drawable.icon_vector)
                    .setStyle(new MediaStyleNotificationHelper.MediaStyle(session))
                    .setSilent(true);
            notificationManager.notify(8, notificationBuilder.build());
            if(ongoingActivity != null){
                ongoingActivity.update(getBaseContext(), ongoingActivityStatus);
            }
            return;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(
                getBaseContext()
                ,0
                ,new Intent(this, Main.class)
                ,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                getBaseContext()
                ,channel_id
        )
                .setSmallIcon(R.drawable.icon_vector)
                .setStyle(new MediaStyleNotificationHelper.MediaStyle(mediaSession))
                .setContentIntent(pendingIntent)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setOngoing(true);
        if(ongoingActivity == null){
            ongoingActivity = new OngoingActivity.Builder(
                    getBaseContext()
                    , 8
                    , notificationBuilder
            )
                    .setStaticIcon(R.drawable.icon_vector)
                    .setTouchIntent(pendingIntent)
                    .setStatus(ongoingActivityStatus)
                    .build();
            ongoingActivity.apply(getBaseContext());
            ServiceCompat.startForeground(
                    this
                    ,8
                    ,notificationBuilder.build()
                    ,ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            );
        }else{
            ongoingActivity.update(getBaseContext(), ongoingActivityStatus);
        }
    }
}
