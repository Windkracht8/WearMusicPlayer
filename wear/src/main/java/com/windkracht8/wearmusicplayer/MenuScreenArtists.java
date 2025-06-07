/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.windkracht8.wearmusicplayer;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MenuScreenArtists extends MenuScreen{
    @Override public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        menu_label.setText(getString(R.string.artists));
        try{
            for(int index = 0; index < Main.library.artists.size(); index++){
                Library.Artist artist = Main.library.artists.get(index);
                String second = "";
                if(artist.albums.size() == 1){
                    second = "1 " + getString(R.string.menu_album) + " | ";
                }else if(artist.albums.size() > 1){
                    second = artist.albums.size() + " " + getString(R.string.menu_albums) + " | ";
                }
                if(artist.tracks.size() == 1){
                    second += "1 " + getString(R.string.menu_track);
                }else{
                    second += artist.tracks.size() + " " + getString(R.string.menu_tracks);
                }
                MenuItem menuItem = new MenuItem(inflater, artist.name, second);
                int finalIndex = index;
                menuItem.setOnClickListener(v->openMenuScreen(new MenuScreenArtist(finalIndex)));
                addMenuItem(menuItem);
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library artists: " + e.getMessage());
        }
        return rootView;
    }
}
