package com.windkracht8.wearmusicplayer;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MenuScreenArtist extends MenuScreen{
    private final int artistIndex;
    MenuScreenArtist(int artistIndex){this.artistIndex = artistIndex;}
    MenuScreenArtist(int artistIndex, int scrollToTrack){
        this.artistIndex = artistIndex;
        this.scrollToTrack = scrollToTrack;
    }
    @Override public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        try{
            Library.Artist artist = Main.library.artists.get(artistIndex);
            menu_label.setText(artist.name);

            artist.albums.forEach(album->{
                String second = getString(R.string.menu_album) + ": " + album.tracks.size() + " ";
                second += getString(album.tracks.size() == 1 ? R.string.menu_track : R.string.menu_tracks);
                MenuItem menuItem = new MenuItem(inflater, album.name, second);
                menuItem.setOnClickListener(v->openMenuScreen(new MenuScreenAlbum(album.id)));
                addMenuItem(menuItem);
            });
            for(int trackIndex = 0; trackIndex < artist.tracks.size(); trackIndex++){
                Library.Track track = artist.tracks.get(trackIndex);
                MenuItem menuItem = new MenuItem(
                        inflater,
                        track.title,
                        track.album == null ? null : track.album.name
                );
                int finalTrackIndex = trackIndex;
                menuItem.setOnClickListener(v->openTrackList(Library.TrackListType.ARTIST, artistIndex, artist.tracks, finalTrackIndex));
                addMenuItem(menuItem);
            }
            if(scrollToTrack > 0 && scrollToTrack < artist.tracks.size()) scrollToItem(scrollToTrack);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library artist: " + e.getMessage());
        }
        return rootView;
    }
}
