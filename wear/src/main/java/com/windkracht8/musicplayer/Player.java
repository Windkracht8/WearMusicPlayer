package com.windkracht8.musicplayer;

import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class Player{
    Handler handler;
    MediaPlayer mediaPlayer;
    public void play(Main main, Uri uri){
        load(main, uri);
        playPause(main.handler_message);
    }
    public void load(Main main, Uri uri){
        if(mediaPlayer != null) mediaPlayer.release();
        mediaPlayer = MediaPlayer.create(main, uri);
        mediaPlayer.setOnCompletionListener(mp -> main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_PLAYER_COMPLETION)));
        handler = new Handler(Looper.getMainLooper());
    }
    private void update(Handler handler_message){
        try{
        if(mediaPlayer.isPlaying()){
            handler_message.sendMessage(handler_message.obtainMessage(Main.MESSAGE_PLAYER_POSITION, mediaPlayer.getCurrentPosition()));
            handler.postDelayed(() -> update(handler_message), 1000);
        }else{
            handler_message.sendMessage(handler_message.obtainMessage(Main.MESSAGE_PLAYER_STOP));
        }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Player.update Exception: " + e.getMessage());
        }
    }
    public void playPause(Handler handler_message){
        if(mediaPlayer.isPlaying()){
            mediaPlayer.pause();
        }else{
            mediaPlayer.start();
            handler.postDelayed(() -> update(handler_message), 1000);
        }
    }
}
