package com.windkracht8.wearmusicplayer;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MenuScreenAlbum extends MenuScreen{
    private final int albumIndex;
    MenuScreenAlbum(int albumIndex){this.albumIndex = albumIndex;}
    @Override
    public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        try{
            Library.Album album = Main.library.albums.get(albumIndex);
            menu_label.setText(album.name);
            for(int trackIndex = 0; trackIndex < album.album_tracks.size(); trackIndex++){
                MenuItem menuItem = new MenuItem(
                        inflater
                        ,album.album_tracks.get(trackIndex).title
                        ,album.artist
                );
                int finalTrackIndex = trackIndex;
                menuItem.setOnClickListener((v)-> openTrackList(album.album_tracks, finalTrackIndex));
                addMenuItem(menuItem);
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library album: " + e.getMessage());
        }
        return rootView;
    }
}
