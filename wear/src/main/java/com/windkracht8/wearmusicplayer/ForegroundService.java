package com.windkracht8.wearmusicplayer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.wear.ongoing.OngoingActivity;
import androidx.wear.ongoing.Status;

public class ForegroundService extends Service{
    private Handler handler;
    private NotificationManager notificationManager;
    private boolean isRunning = false;
    @Nullable
    @Override
    public IBinder onBind(Intent intent){return null;}
    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Log.d(Main.LOG_TAG, "ForegroundService.onStartCommand");
        String channel_id = "WMP_Notification";

        notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(new NotificationChannel(
                channel_id
                ,getString(R.string.open_wmp)
                ,NotificationManager.IMPORTANCE_DEFAULT)
        );
        Intent actionIntent = new Intent(this, Main.class);
        PendingIntent actionPendingIntent = PendingIntent.getActivity(
                this
                ,0
                ,actionIntent
                ,PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(
                this
                ,channel_id
        )
                .setSmallIcon(R.drawable.icon_vector)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(
                        R.drawable.icon_vector, getString(R.string.open_wmp),
                        actionPendingIntent
                )
                .setOngoing(true);
        Status ongoingActivityStatus = new Status.Builder()
                .addTemplate(getString(R.string.playing_track))
                .build();
        OngoingActivity ongoingActivity = new OngoingActivity.Builder(
                getBaseContext()
                ,1
                ,notificationBuilder
        )
                .setStaticIcon(R.drawable.icon_vector)
                .setTouchIntent(actionPendingIntent)
                .setStatus(ongoingActivityStatus)
                .build();
        ongoingActivity.apply(getBaseContext());

        ServiceCompat.startForeground(
                this,
                8,
                notificationBuilder.build(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        );
        isRunning = true;
        return super.onStartCommand(intent, flags, startId);
    }
    void start(Context appContext){
        if(isRunning) return;
        if(handler == null) handler = new Handler(Looper.getMainLooper());
        Log.d(Main.LOG_TAG, "ForegroundService.start");
        appContext.startForegroundService(new Intent(appContext, getClass()));
    }
    void stopDelayed(){
        if(Main.player.isPlaying()) return;
        handler.postDelayed(this::stopDelayed2, 1000);
    }
    private void stopDelayed2(){
        if(Main.player.isPlaying()) return;
        stop();
    }
    void stop(){
        if(isRunning){
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
            notificationManager.cancelAll();
        }
        isRunning = false;
    }
}
