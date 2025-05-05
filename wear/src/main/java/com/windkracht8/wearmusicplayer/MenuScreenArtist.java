/*
 *  Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 *  This file is part of WearMusicPlayer
 *  WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *  WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *  You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
