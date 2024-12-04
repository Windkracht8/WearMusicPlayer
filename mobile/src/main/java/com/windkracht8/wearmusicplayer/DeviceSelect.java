package com.windkracht8.wearmusicplayer;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.fragment.app.FragmentActivity;

import org.json.JSONObject;

import java.util.Set;

@SuppressLint("MissingPermission")//Handled by main
public class DeviceSelect extends FragmentActivity implements CommsBT.CommsBTInterface{
    private ImageView loading_icon;
    private LinearLayout device_select_ll;
    private LayoutInflater layoutInflater;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_select);
        layoutInflater = getLayoutInflater();
        loading_icon = findViewById(R.id.device_select_loading);
        loading_icon.setBackgroundResource(R.drawable.icon_animate);
        ((AnimatedVectorDrawable) loading_icon.getBackground()).start();
        device_select_ll = findViewById(R.id.device_select_ll);

        try{
            Main.commsBT.addListener(this);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Failed to add as a listener: " + e.getMessage());
        }
        Main.executorService.submit(this::loadDevices);
    }

    private void loadDevices(){
        Set<BluetoothDevice> bluetoothDevices = Main.commsBT.getDevices();
        if(bluetoothDevices == null || bluetoothDevices.isEmpty()){
            finish();
            return;
        }
        for(BluetoothDevice bluetoothDevice : bluetoothDevices){
            deviceFound(bluetoothDevice);
        }
        runOnUiThread(()->loading_icon.setVisibility(View.GONE));
    }
    private void deviceFound(BluetoothDevice bluetoothDevice){
        runOnUiThread(()->{
            Device device = new Device(layoutInflater, bluetoothDevice);
            device_select_ll.addView(device);
            device.setOnClickListener(view -> onDeviceClick((Device) view));
        });
    }
    private void onDeviceClick(Device device){
        Main.commsBT.connectDevice(device.bluetoothDevice);
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
    public void onBTConnectFailed(){finish();}
    @Override
    public void onBTConnected(String x){finish();}
    @Override
    public void onBTDisconnected(){}
    @Override
    public void onBTSending(String x){}
    @Override
    public void onBTResponse(JSONObject x){}
    @Override
    public void onBTError(int x){}
}
