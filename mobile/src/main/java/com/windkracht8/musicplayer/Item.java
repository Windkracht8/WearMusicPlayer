package com.windkracht8.musicplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import java.util.ArrayList;

public class Item extends ConstraintLayout{
    public boolean isDir;
    public Library.LibItem libItem;

    TextView item_status;
    TextView item_label;

    private final ArrayList<Item> items = new ArrayList<>();
    private boolean isExpanded = false;

    public Item(Context context, AttributeSet attrs){super(context, attrs);}
    public Item(Main main, Library.LibDir libDir){
        super(main);
        this.libItem = libDir;
        isDir = true;
        show(main);
        item_label.setTextAppearance(R.style.w8TextViewStyleBold);
        item_label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);

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
        if(!isDir) item_status.setOnClickListener(v -> main.onItemStatusPressed(this));
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
                if(isDir){
                    item_status.setText("V");
                }else{
                    item_status.setBackgroundResource(R.drawable.icon_delete);
                }
                break;
            case PARTIAL:
                if(isDir){
                    item_status.setText("/");
                }else{
                    item_status.setBackgroundResource(0);
                }
                break;
            case NOT:
                if(isDir){
                    item_status.setText("");
                }else{
                    item_status.setBackgroundResource(R.drawable.icon_upload);
                }
                break;
            case UNKNOWN:
                if(isDir){
                    item_status.setText("");
                }else{
                    item_status.setBackgroundResource(0);
                }
                break;
        }
        for(Item item : items){
            item.newStatus();
        }
    }
    public void updateProgress(){
        long perc = (libItem.progress * 100) / libItem.length;
        item_status.setText(String.valueOf(perc));
        item_status.setBackgroundResource(0);
    }
    public void updateProgressDone(Main main, String path){
        if(libItem.path.equals(path)){
            libItem.status = Main.Status.FULL;
            main.runOnUiThread(this::newStatus);
            return;
        }
        for(Item item : items){
            item.updateProgressDone(main, path);
        }
    }
    public void onLabelPressed(){
        if(!isDir) return;
        isExpanded = !isExpanded;
        for(Item item : items){
            item.setVisibility(isExpanded ? View.VISIBLE : View.GONE);
        }
    }

}
