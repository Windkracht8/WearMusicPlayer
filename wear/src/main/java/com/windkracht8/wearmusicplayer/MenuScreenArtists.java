package com.windkracht8.wearmusicplayer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MenuScreenArtists extends MenuScreen{
    @Override
    public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        menu_label.setText(getString(R.string.artists));
        for(int index = 0; index < Main.library.albums.size(); index++){
            Library.Artist artist = Main.library.artists.get(index);
            String secondary = artist.artist_albums.size() + " albums|" + artist.artist_tracks.size() + " tracks";
            MenuItem menuItem = new MenuItem(inflater, artist.name, secondary);
            int finalIndex = index;
            menuItem.setOnClickListener((v)-> openMenuScreen(new MenuScreenArtist(finalIndex)));
            addMenuItem(menuItem);
        }
        return rootView;
    }
}
