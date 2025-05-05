/*
 *  Copyright 2024-2025 Bart Vullings <dev@windkracht8.com>
 *  This file is part of WearMusicPlayer
 *  WearMusicPlayer is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.
 *  WearMusicPlayer is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *  You should have received a copy of the GNU General Public License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.windkracht8.wearmusicplayer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.Set;
import java.util.concurrent.Executors;

@SuppressLint("MissingPermission")//Handled by main
public class DeviceSelect extends Activity implements CommsBT.BTInterface{
    private ImageView device_select_loading;
    private LinearLayout device_select_ll;
    private boolean restartBT;

    @Override public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        restartBT = getIntent().getBooleanExtra("restartBT", false);
        setContentView(R.layout.device_select);
        findViewById(android.R.id.content).setOnApplyWindowInsetsListener(Main.onApplyWindowInsetsListener);

        device_select_loading = findViewById(R.id.device_select_loading);
        ((AnimatedVectorDrawable) device_select_loading.getBackground()).start();
        device_select_ll = findViewById(R.id.device_select_ll);
        try{
            Main.commsBT.addListener(this);
            if(restartBT) Main.commsBT.restartBT();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "DeviceSelect.onCreate Failed to add as a listener: " + e.getMessage());
        }
        Executors.newCachedThreadPool().execute(this::loadDevices);
    }

    private void loadDevices(){
        if(Main.commsBT == null){
            finishAndRemoveTask();
            return;
        }
        Set<BluetoothDevice> bluetoothDevices = Main.commsBT.getDevices();
        if(bluetoothDevices == null || bluetoothDevices.isEmpty()){
            finishAndRemoveTask();
            return;
        }
        runOnUiThread(()->{
            bluetoothDevices.forEach(this::deviceFound);
            device_select_loading.setVisibility(View.GONE);
        });
    }
    private void deviceFound(BluetoothDevice bluetoothDevice){
        TextView device = new TextView(this, null, 0, R.style.w8DeviceStyle);
        device.setText(bluetoothDevice.getName());
        device_select_ll.addView(device);
        device.setOnClickListener(v->{
            if(Main.commsBT != null) Main.commsBT.connectDevice(bluetoothDevice);
        });
    }
    @Override
    public void onBTStartDone(){}
    @Override
    public void onBTConnecting(String deviceName){
        Intent startDeviceConnect = new Intent(this, DeviceConnect.class);
        startDeviceConnect.putExtra("name", deviceName);
        startActivity(startDeviceConnect);
    }
    @Override
    public void onBTConnectFailed(){if(!restartBT) finishAndRemoveTask();}
    @Override
    public void onBTConnected(String x){finishAndRemoveTask();}
    @Override
    public void onBTDisconnected(){}
    @Override
    public void onBTSending(String x){}
    @Override
    public void onBTResponse(JSONObject x){}
    @Override
    public void onBTError(int x){}
}
