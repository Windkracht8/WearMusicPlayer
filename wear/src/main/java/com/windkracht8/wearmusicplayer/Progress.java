package com.windkracht8.wearmusicplayer;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.google.android.material.progressindicator.CircularProgressIndicator;

public class Progress extends ConstraintLayout{
    private CircularProgressIndicator progress_indicator;
    private TextView progress_connection_info;
    private TextView progress_file;
    public Progress(Context context, AttributeSet attrs){
        super(context, attrs);
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        if(inflater == null){
            Toast.makeText(context, R.string.fail_show_progress, Toast.LENGTH_SHORT).show();
            return;
        }
        inflater.inflate(R.layout.progress, this, true);

        progress_indicator = findViewById(R.id.progress_indicator);
        progress_connection_info = findViewById(R.id.progress_connection_info);
        progress_file = findViewById(R.id.progress_file);
        ConstraintLayout.LayoutParams layoutParams = (ConstraintLayout.LayoutParams) progress_connection_info.getLayoutParams();
        layoutParams.leftMargin = Main.vw20;
        layoutParams.rightMargin = Main.vw20;
        layoutParams = (ConstraintLayout.LayoutParams) progress_file.getLayoutParams();
        layoutParams.leftMargin = Main.vw20;
        layoutParams.rightMargin = Main.vw20;
    }
    void show(String path){
        setProgress(0);
        String file = path.substring(path.lastIndexOf("/")+1, path.length()-4);
        progress_file.setText(file);
        progress_connection_info.setText(R.string.connection_info_BT);
        setVisibility(View.VISIBLE);
    }
    void setProgress(int progress){progress_indicator.setProgress(progress);}
    void setConnectionInfo(int value){progress_connection_info.setText(value);}

}
