package com.windkracht8.wearmusicplayer;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class MenuScreenMain extends MenuScreen{
    @Override
    public @Nullable View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ){
        View rootView = super.onCreateView(inflater, container, savedInstanceState);
        menu_label.setText(getString(R.string.library));
        MenuItem menuItem = new MenuItem(inflater, getString(R.string.all), String.valueOf(Main.library.tracks.size()));
        menuItem.setOnClickListener((v)-> openMenuScreen(new MenuScreenAll()));
        addMenuItem(menuItem);

        menuItem = new MenuItem(inflater, getString(R.string.albums), String.valueOf(Main.library.albums.size()));
        menuItem.setOnClickListener((v)-> openMenuScreen(new MenuScreenAlbums()));
        addMenuItem(menuItem);

        menuItem = new MenuItem(inflater, getString(R.string.artists), String.valueOf(Main.library.artists.size()));
        menuItem.setOnClickListener((v)-> openMenuScreen(new MenuScreenArtists()));
        addMenuItem(menuItem);

        menuItem = new MenuItem(inflater, getString(R.string.rescan), null);
        menuItem.setOnClickListener((v)-> rescan());
        addMenuItem(menuItem);

        return rootView;
    }
    @Override
    public void onAttach(@NonNull Context context){
        super.onAttach(context);
        if(context instanceof MenuScreenInterface)
            menuScreenInterface = (MenuScreenInterface) context;
    }
    private void rescan(){
        Main.rescan();
        assert getActivity() != null;
        getActivity().finish();
    }
}
