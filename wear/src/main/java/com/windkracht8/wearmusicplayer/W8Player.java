package com.windkracht8.wearmusicplayer;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.MediaItem;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.session.MediaSession;
import androidx.media3.session.MediaSessionService;

import java.util.ArrayList;
import java.util.Objects;


public class W8Player extends MediaSessionService{
    private static final String INTENT_ACTION = "com.windkracht8.wearmusicplayer.W8Player";
    private Handler handler;
    private BroadcastReceiver broadcastReceiver;
    ExoPlayer exoPlayer;
    private MediaSession mediaSession;
    private int updateQueued = 0;
    static ArrayList<Library.Track> tracks;

    @SuppressLint("UnspecifiedRegisterReceiverFlag")//registerReceiver is wrapped in SDK_INT, still complains
    @Override
    public void onCreate(){
        super.onCreate();
        if(handler != null) return;
        Log.d(Main.LOG_TAG, "W8Player.onCreate");
        handler = new Handler(Looper.getMainLooper());
        exoPlayer = new ExoPlayer.Builder(this).build();
        Context context = this;

        exoPlayer.addListener(
            new ExoPlayer.Listener(){
                @Override
                public void onIsPlayingChanged(boolean isPlaying){
                    Main.sendIntentIsPlayingChanged(context, isPlaying);
                    handler.postDelayed(() -> update(context), 100);
                    updateQueued++;
                }
                @Override
                public void onPlaybackStateChanged(int state){
                    if(state == ExoPlayer.STATE_ENDED){
                        Main.sendIntent(context, "onPlayerEnded");
                    }
                }
            }
        );

        mediaSession = new MediaSession.Builder(this, exoPlayer).build();

        broadcastReceiver = new BroadcastReceiver(){
            @Override
            public void onReceive(Context context, Intent intent){
                if(!intent.hasExtra("intent_type")){return;}
                switch(Objects.requireNonNull(intent.getStringExtra("intent_type"))){
                    case "onIsPlayingChanged":
                }
            }
        };
        if(Build.VERSION.SDK_INT >= 33){
            registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION), Context.RECEIVER_NOT_EXPORTED);
        }else{
            registerReceiver(broadcastReceiver, new IntentFilter(INTENT_ACTION));
        }

        Main.player = this;
        if(tracks != null){
            loadTracks();
        }
    }
    @Override
    public void onDestroy(){
        super.onDestroy();
        unregisterReceiver(broadcastReceiver);
        mediaSession.getPlayer().release();
        mediaSession.release();
        mediaSession = null;
    }

    //void playTrack(Context context, Uri uri){
        //exoPlayer.setPlayWhenReady(true);
        //loadTrack(context, uri);
    //}
    private void loadTracks(){
        exoPlayer.clearMediaItems();
        for(Library.Track track : tracks){
            Log.d(Main.LOG_TAG, "W8Player.loadTrack: " + track.title);
            try{
                exoPlayer.addMediaItem(MediaItem.fromUri(track.uri));
                exoPlayer.prepare();
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "W8Player.prepare: " + e.getMessage());
                Toast.makeText(this, R.string.fail_load_tracks, Toast.LENGTH_SHORT).show();
            }
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
    void playPause(){
        Log.d(Main.LOG_TAG, "W8Player.playPause");
        if(exoPlayer.isPlaying()){
            Log.d(Main.LOG_TAG, "W8Player.playPause pause");
            exoPlayer.pause();
            exoPlayer.setPlayWhenReady(false);
        }else{
            Log.d(Main.LOG_TAG, "W8Player.playPause play");
            exoPlayer.play();
            exoPlayer.setPlayWhenReady(true);
        }
    }
    @Nullable
    @Override
    public MediaSession onGetSession(@NonNull MediaSession.ControllerInfo controllerInfo){
        return mediaSession;
    }
}
