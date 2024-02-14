package com.windkracht8.wearmusicplayer;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.ExoPlayer;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.session.MediaSession;
import android.media.session.MediaSession.Callback;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

import java.util.Objects;

class W8Player{
    private Handler handler;
    private BroadcastReceiver broadcastReceiver;
    private ExoPlayer exoPlayer;
    private MediaSession mediaSession;
    private int updateQueued = 0;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")//registerReceiver is wrapped in SDK_INT, still complains
    void init(Context appContext){
        if(handler != null) return;
        handler = new Handler(Looper.getMainLooper());
        exoPlayer = new ExoPlayer.Builder(appContext).build();

        exoPlayer.addListener(
            new ExoPlayer.Listener(){
                @Override
                public void onIsPlayingChanged(boolean isPlaying){
                    Main.sendIntentIsPlayingChanged(appContext, isPlaying);
                    handler.postDelayed(() -> update(appContext), 100);
                    updateQueued++;
                }
                @Override
                public void onPlaybackStateChanged(int state){
                    if(state == ExoPlayer.STATE_ENDED){
                        Main.sendIntent(appContext, "onPlayerEnded");
                    }
                }
            }
        );

        mediaSession = new MediaSession(appContext, Main.LOG_TAG);
        mediaSession.setCallback(new Callback(){
            @Override
            public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent){
                KeyEvent keyEvent;
                if(Build.VERSION.SDK_INT >= 33){
                    keyEvent = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent.class);
                }else{
                    keyEvent = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                }
                Log.d(Main.LOG_TAG, "W8Player.MediaSession.onMediaButtonEvent keyEvent: " + keyEvent);
                if(keyEvent == null || keyEvent.getAction() != KeyEvent.ACTION_UP) return false;
                switch(keyEvent.getKeyCode()){
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        Main.sendIntent(appContext, "bPreviousPressed");
                       return true;
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        playPause();
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        Main.sendIntent(appContext, "bNextPressed");
                        return true;
                }
                return false;
            }
            @Override
            public void onPlay(){
                Log.d(Main.LOG_TAG, "W8Player.MediaSession.onPlay");
                exoPlayer.play();
            }
            @Override
            public void onPause(){
                Log.d(Main.LOG_TAG, "W8Player.MediaSession.onPause");
                exoPlayer.pause();
            }
            @Override
            public void onStop(){
                Log.d(Main.LOG_TAG, "W8Player.MediaSession.onStop");
                exoPlayer.stop();
            }
        });

        broadcastReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent){
                if(!intent.hasExtra("intent_type")){return;}
                switch(Objects.requireNonNull(intent.getStringExtra("intent_type"))){
                    case "player.playTrack":
                        if(!intent.hasExtra("uri")){return;}
                        playTrack(appContext, intent.getParcelableExtra("uri"));
                        break;
                    case "player.loadTrack":
                        if(!intent.hasExtra("uri")){return;}
                        loadTrack(appContext, intent.getParcelableExtra("uri"));
                        break;
                    case "player.playPause":
                        playPause();
                        break;
                    case "player.exit":
                        exit(appContext);
                        break;
                }
            }
        };
        if(Build.VERSION.SDK_INT >= 33){
            appContext.registerReceiver(broadcastReceiver, new IntentFilter(Main.INTENT_ACTION), Context.RECEIVER_NOT_EXPORTED);
        }else{
            appContext.registerReceiver(broadcastReceiver, new IntentFilter(Main.INTENT_ACTION));
        }
    }
    private void exit(Context appContext){
        appContext.unregisterReceiver(broadcastReceiver);
        handler.removeCallbacksAndMessages(null);
        handler = null;
        mediaSession.release();
        mediaSession = null;
        exoPlayer.stop();
        exoPlayer = null;
    }
    private void playTrack(Context context, Uri uri){
        exoPlayer.setPlayWhenReady(true);
        loadTrack(context, uri);
    }
    @OptIn(markerClass = UnstableApi.class)
    private void loadTrack(Context context, Uri uri){
        try{
            exoPlayer.setMediaSource(new ProgressiveMediaSource.Factory(new DefaultDataSource.Factory(context)).createMediaSource(MediaItem.fromUri(uri)));
            exoPlayer.prepare();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "W8Player.prepare: " + e.getMessage());
            Toast.makeText(context, R.string.fail_prepare, Toast.LENGTH_SHORT).show();
        }
    }
    private void update(Context appContext){
        updateQueued--;
        if(exoPlayer.isPlaying()){
            long currentPosition = exoPlayer.getCurrentPosition();
            Main.sendIntentProgressChanged(appContext, currentPosition);
            if(updateQueued <= 0){
                handler.postDelayed(()-> update(appContext), 1000 - (currentPosition%1000));
                updateQueued++;
            }
        }
    }
    private void playPause(){
        Log.d(Main.LOG_TAG, "W8Player.playPause");
        if(exoPlayer.isPlaying()){
            exoPlayer.pause();
            exoPlayer.setPlayWhenReady(false);
        }else{
            exoPlayer.play();
            exoPlayer.setPlayWhenReady(true);
        }
    }
    boolean isPlaying(){
        if(exoPlayer == null) return false;
        return exoPlayer.isPlaying();
    }
}
