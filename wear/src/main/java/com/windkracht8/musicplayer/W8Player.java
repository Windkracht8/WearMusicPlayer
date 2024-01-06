package com.windkracht8.musicplayer;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.media3.common.MediaItem;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.exoplayer.ExoPlayer;

import android.content.Context;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.MediaSession.Callback;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Toast;

public class W8Player{
    private final Handler handler;
    private final ExoPlayer exoPlayer;
    public W8Player(Main main){
        handler = new Handler(Looper.getMainLooper());
        exoPlayer = new ExoPlayer.Builder(main).build();
        exoPlayer.addListener(
            new ExoPlayer.Listener(){
                @Override
                public void onIsPlayingChanged(boolean isPlaying){
                    main.onIsPlayingChanged(isPlaying);
                    handler.postDelayed(() -> update(main), 100);
                }
                @Override
                public void onPlaybackStateChanged(int state){
                    if(state == ExoPlayer.STATE_ENDED){
                        main.onPlayerEnded();
                    }
                }
            }
        );

        MediaSession mediaSession = new MediaSession(main, Main.LOG_TAG);
        mediaSession.setCallback(new Callback() {
            @Override
            public boolean onMediaButtonEvent(@NonNull Intent mediaButtonIntent){
                KeyEvent keyEvent;
                if(Build.VERSION.SDK_INT >= 33){
                    keyEvent = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent.class);
                }else{
                    keyEvent = mediaButtonIntent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
                }
                if(keyEvent == null) return false;
                switch(keyEvent.getKeyCode()){
                    case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                        main.bPreviousPressed();
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_PLAY:
                        exoPlayer.play();
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_PAUSE:
                        exoPlayer.pause();
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                        playPause();
                        return true;
                    case KeyEvent.KEYCODE_MEDIA_NEXT:
                        main.bNextPressed();
                        return true;
                }
                return false;
            }
            @Override
            public void onPlay(){exoPlayer.play();}
            @Override
            public void onPause(){exoPlayer.pause();}
            @Override
            public void onStop(){exoPlayer.stop();}
        });
    }
    public void playTrack(Context context, Uri uri){
        exoPlayer.setPlayWhenReady(true);
        loadTrack(context, uri);
    }
    @OptIn(markerClass = UnstableApi.class)
    public void loadTrack(Context context, Uri uri){
        try{
            exoPlayer.setMediaSource(new ProgressiveMediaSource.Factory(new DefaultDataSource.Factory(context)).createMediaSource(MediaItem.fromUri(uri)));
            exoPlayer.prepare();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "W8Player.prepare: " + e.getMessage());
            Toast.makeText(context, R.string.fail_prepare, Toast.LENGTH_SHORT).show();
        }
    }
    private void update(Main main){
        if(exoPlayer.isPlaying()){
            long currentPosition = exoPlayer.getCurrentPosition();
            main.onProgressChanged(currentPosition);
            handler.postDelayed(() -> update(main), (currentPosition % 1000));
        }
    }
    public void playPause(){
        if(exoPlayer.isPlaying()){
            exoPlayer.pause();
            exoPlayer.setPlayWhenReady(false);
        }else{
            exoPlayer.play();
            exoPlayer.setPlayWhenReady(true);
        }
    }
}
