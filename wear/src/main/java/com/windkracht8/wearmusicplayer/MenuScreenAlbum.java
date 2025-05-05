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

public class MenuScreenAlbum extends MenuScreen{
    private final int albumId;
    MenuScreenAlbum(int albumId){this.albumId = albumId;}
    MenuScreenAlbum(int albumId, int scrollToTrack){
        this.albumId = albumId;
        this.scrollToTrack = scrollToTrack;
    }
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
                        inflater,
                        album.tracks.get(trackIndex).title,
                        album.artist
                );
                int finalTrackIndex = trackIndex;
                menuItem.setOnClickListener(v->openTrackList(Library.TrackListType.ALBUM, albumId, album.tracks, finalTrackIndex));
                addMenuItem(menuItem);
            }
            if(scrollToTrack > 0 && scrollToTrack < album.tracks.size()) scrollToItem(scrollToTrack);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library album: " + e.getMessage());
        }
        return rootView;
    }
}
