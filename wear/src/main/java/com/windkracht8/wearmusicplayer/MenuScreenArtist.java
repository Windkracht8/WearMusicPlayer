package com.windkracht8.wearmusicplayer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MenuScreenArtist extends MenuScreen{
    private final int artistIndex;
    MenuScreenArtist(int artistIndex){this.artistIndex = artistIndex;}
    @Override
    public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        Library.Artist artist = Main.library.artists.get(artistIndex);
        menu_label.setText(artist.name);
        for(int trackIndex = 0; trackIndex < artist.artist_tracks.size(); trackIndex++){
            MenuItem menuItem = new MenuItem(
                    inflater
                    ,artist.artist_tracks.get(trackIndex).title
                    ,artist.artist_tracks.get(trackIndex).artist.name
            );
            int finalTrackIndex = trackIndex;
            menuItem.setOnClickListener((v)-> openTrackList(artist.artist_tracks, finalTrackIndex));
            addMenuItem(menuItem);
        }
        return rootView;
    }
}
