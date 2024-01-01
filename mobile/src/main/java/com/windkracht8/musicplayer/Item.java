package com.windkracht8.musicplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayList;

public class Item extends ConstraintLayout{
    public boolean isDir;
    public Library.LibDir libDir;
    public Library.LibTrack libTrack;
    public Library.LibItem libItem;

    TextView item_status;
    TextView item_label;

    private final ArrayList<Item> items = new ArrayList<>();
    private boolean isExpanded = false;

    public Item(Context context, AttributeSet attrs){super(context, attrs);}
    public Item(Main main, Library.LibDir libDir){
        super(main);
        this.libDir = libDir;
        this.libItem = libDir;
        isDir = true;
        show(main);
        item_label.setTextAppearance(R.style.w8TextViewStyleBold);

        LinearLayout item_items = findViewById(R.id.item_items);
        for(Library.LibDir libDirSub : libDir.libDirs){
            Item item = new Item(main, libDirSub);
            items.add(item);
            item_items.addView(item);
        }
        for(Library.LibTrack libTrack : libDir.libTracks){
            Item item = new Item(main, libTrack);
            items.add(item);
            item_items.addView(item);
        }
    }
    public Item(Main main, Library.LibTrack libTrack){
        super(main);
        this.libTrack = libTrack;
        this.libItem = libTrack;
        isDir = false;
        show(main);
    }
    private void show(Main main){
        LayoutInflater inflater = (LayoutInflater) main.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if(inflater == null){Toast.makeText(main, "Failed to show item", Toast.LENGTH_SHORT).show();return;}
        inflater.inflate(R.layout.item, this, true);

        item_status = findViewById(R.id.item_status);
        item_label = findViewById(R.id.item_label);
        item_status.setOnClickListener(v -> main.onItemStatusPressed(this));
        item_label.setOnClickListener(v -> onLabelPressed());

        newStatus();

        if(libItem.depth > 0){
            this.setVisibility(View.GONE);
        }

        int margin = getResources().getDimensionPixelSize(R.dimen.dp5) * libItem.depth;
        ((LinearLayout.LayoutParams) item_label.getLayoutParams()).setMarginStart(margin);
        item_label.setText(libItem.name);
    }

    public void clearStatus(){
        libItem.status = Main.Status.UNKNOWN;
        for(Item item : items){
            item.clearStatus();
        }
        newStatus();
    }
    public void newStatus(){
        switch(libItem.status){
            case FULL:
                item_status.setText("V");
                break;
            case PARTIAL:
                item_status.setText("/");
                break;
            case NOT:
                item_status.setText("X");
                break;
            case UNKNOWN:
                item_status.setText("");
                break;
        }
        for(Item item : items){
            item.newStatus();
        }
    }
    public void updateProgress(){
        long perc = (libItem.progress / libItem.length) * 100;
        Log.d(Main.LOG_TAG, "Item.updateProgress: " + perc);
        item_status.setText(String.valueOf(perc));
    }
    public void onLabelPressed(){
        if(!isDir) return;
        isExpanded = !isExpanded;
        for(Item item : items){
            item.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        }
    }

}
