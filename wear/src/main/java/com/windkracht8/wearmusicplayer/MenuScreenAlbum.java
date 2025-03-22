package com.windkracht8.wearmusicplayer;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MenuScreenAlbum extends MenuScreen{
    private final int albumId;
    MenuScreenAlbum(int albumId){this.albumId = albumId;}
    @Override public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        try{
            Library.Album album = Main.library.getAlbum(albumId);
            menu_label.setText(album.name);
            for(int trackIndex = 0; trackIndex < album.tracks.size(); trackIndex++){
                MenuItem menuItem = new MenuItem(
                        inflater
                        ,album.tracks.get(trackIndex).title
                        ,album.artist
                );
                int finalTrackIndex = trackIndex;
                menuItem.setOnClickListener(v->openTrackList(album.tracks, finalTrackIndex));
                addMenuItem(menuItem);
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library album: " + e.getMessage());
        }
        return rootView;
    }
}
