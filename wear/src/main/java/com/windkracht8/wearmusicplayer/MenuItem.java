/*
 * Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 * This file is part of WearMusicPlayer
 * WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 * WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.windkracht8.wearmusicplayer;

import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MenuItem extends LinearLayout{
    MenuItem(LayoutInflater inflater, String primary, String secondary){
        super(inflater.getContext());
        inflater.inflate(R.layout.menu_item, this);

        TextView menu_item_primary = findViewById(R.id.menu_item_primary);
        menu_item_primary.setText(primary);

        TextView menu_item_secondary = findViewById(R.id.menu_item_secondary);
        if(secondary == null){
            menu_item_secondary.setVisibility(View.GONE);
        }else{
            menu_item_secondary.setText(secondary);
        }
    }
    void setPrimary(String value){
        TextView menu_item_primary = findViewById(R.id.menu_item_primary);
        menu_item_primary.setText(value);
    }
    void setSecondary(String value){
        TextView menu_item_secondary = findViewById(R.id.menu_item_secondary);
        menu_item_secondary.setText(value);
    }
}

