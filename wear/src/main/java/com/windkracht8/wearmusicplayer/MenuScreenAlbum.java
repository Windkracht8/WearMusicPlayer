package com.windkracht8.wearmusicplayer;

import android.os.Bundle;
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
        Library.Album album = Main.library.albums.get(albumIndex);
        menu_label.setText(album.name);
        for(int trackIndex = 0; trackIndex < Main.library.albums.size(); trackIndex++){
            MenuItem menuItem = new MenuItem(
                    inflater
                    ,album.album_tracks.get(trackIndex).title
                    ,album.artist
            );
            int finalTrackIndex = trackIndex;
            menuItem.setOnClickListener((v)-> openTrackList(album.album_tracks, finalTrackIndex));
            addMenuItem(menuItem);
        }
        return rootView;
    }
}
