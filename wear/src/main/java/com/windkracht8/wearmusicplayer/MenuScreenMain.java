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

public class MenuScreenMain extends MenuScreen{
    @Override public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        menu_label.setText(getString(R.string.library));
        int size = 0;
        try{
            size = Main.library.tracks.size();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library tracks size: " + e.getMessage());
        }
        MenuItem menuItem = new MenuItem(inflater, getString(R.string.all), String.valueOf(size));
        menuItem.setOnClickListener(v->openMenuScreen(new MenuScreenAll()));
        addMenuItem(menuItem);

        try{
            size = Main.library.albums.size();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library albums size: " + e.getMessage());
            size = 0;
        }
        menuItem = new MenuItem(inflater, getString(R.string.albums), String.valueOf(size));
        menuItem.setOnClickListener(v->openMenuScreen(new MenuScreenAlbums()));
        addMenuItem(menuItem);

        try{
            size = Main.library.artists.size();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to get library artists size: " + e.getMessage());
            size = 0;
        }
        menuItem = new MenuItem(inflater, getString(R.string.artists), String.valueOf(size));
        menuItem.setOnClickListener(v->openMenuScreen(new MenuScreenArtists()));
        addMenuItem(menuItem);

        menuItem = new MenuItem(inflater, getString(R.string.rescan), null);
        menuItem.setOnClickListener(v->rescan());
        addMenuItem(menuItem);

        return rootView;
    }
    private void rescan(){
        Main.doRescan = true;
        if(getActivity() == null) return;
        getActivity().finish();
    }
}
