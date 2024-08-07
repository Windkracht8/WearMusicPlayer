package com.windkracht8.wearmusicplayer;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;

public class MenuScreenAll extends MenuScreen{
    private ArrayList<Library.Track> tracks;
    @Override
    public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        menu_label.setText(getString(R.string.all));
        MenuItem menuItem = new MenuItem(inflater, getString(R.string.randomise), null);
        menuItem.setOnClickListener((v)-> randomise());
        addMenuItem(menuItem);

        tracks = new ArrayList<>(Main.library.tracks);
        for(int trackIndex = 0; trackIndex < tracks.size(); trackIndex++){
            Library.Track track = tracks.get(trackIndex);
            MenuItem menuItemTrack = new MenuItem(inflater, track.title, track.artist.name);
            int finalTrackIndex = trackIndex;
            menuItemTrack.setOnClickListener((v)-> openTrackList(tracks, finalTrackIndex));
            addMenuItem(menuItemTrack);
        }
        return rootView;
    }
    private void randomise(){
        Collections.shuffle(tracks);
        for(int i = 1; i < menuItems.size(); i++){
            MenuItem menuItem = menuItems.get(i);
            Library.Track track = tracks.get(i-1);
            menuItem.setPrimary(track.title);
            menuItem.setSecondary(track.artist.name);
        }
    }
}
