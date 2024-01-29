package com.windkracht8.wearmusicplayer;

import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;

public class Library{
    private static String exStorageDir;
    public LibDir dir_music;

    public void scanFiles(Main main){
        exStorageDir = Environment.getExternalStorageDirectory().toString();
        dir_music = new LibDir(URI.create(exStorageDir + "/Music"));
        scanFilesDir(dir_music);
        main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_LIBRARY_READY));
    }
    private void scanFilesDir(LibDir libDir){
        File[] files = (new File(exStorageDir + "/" + libDir.path)).listFiles();
        if(files == null) return;
        for(File file : files){
            if(file.getName().startsWith(".")) continue;
            if(file.isDirectory()){
                LibDir libDir_sub = new LibDir(file.toURI());
                libDir.libDirs.add(libDir_sub);
                scanFilesDir(libDir_sub);
            }
            if(!isTrack(file.getName())) continue;
            libDir.libTracks.add(new LibTrack(file.toURI()));
        }
        libDir.sort();
    }

    public void updateLibWithFilesOnWatch(Handler handler_message, JSONArray filesOnWatch){
        ArrayList<String> pathsOnWatch = new ArrayList<>();
        try{
            for(int i = 0; i < filesOnWatch.length(); i++){
                JSONObject object = filesOnWatch.getJSONObject(i);
                String path = object.getString("path");
                pathsOnWatch.add(Uri.decode(path));
            }
            updateDirWithFilesOnWatch(pathsOnWatch, dir_music);
            handler_message.sendMessage(handler_message.obtainMessage(Main.MESSAGE_LIBRARY_UPDATE_STATUS));
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Library.updateLibWithFilesOnWatch: " + e.getMessage());
        }
    }
    private void updateDirWithFilesOnWatch(ArrayList<String> pathsOnWatch, LibDir directory){
        for(LibDir subdir : directory.libDirs){
            updateDirWithFilesOnWatch(pathsOnWatch, subdir);
        }
        boolean partial = false;
        boolean full = directory.libTracks.size() > 0;
        for(LibTrack libTrack : directory.libTracks){
            if(pathsOnWatch.contains(libTrack.path)){
                libTrack.status = Main.Status.FULL;
                partial = true;
            }else{
                libTrack.status = Main.Status.NOT;
                full = false;
            }
        }
        for(LibDir libDir : directory.libDirs){
            if(libDir.status == Main.Status.FULL){
                partial = true;
            }else if(libDir.status == Main.Status.PARTIAL){
                partial = true;
                full = false;
            }else{
                full = false;
            }
        }
        if(full){
            directory.status = Main.Status.FULL;
        }else if(partial){
            directory.status = Main.Status.PARTIAL;
        }else{
            directory.status = Main.Status.NOT;
        }
    }

    private boolean isTrack(String name){
        return name.endsWith(".mp3") || name.endsWith(".m4a");
    }

    public static class LibItem implements Comparable<LibItem>{
        public final URI uri;
        public String path;
        public final String name;
        public int depth = -1;
        public Main.Status status = Main.Status.UNKNOWN;
        public long length = 1;
        public long progress = 0;
        public LibItem(URI uri){
            this.uri = uri;
            path = uri.getPath().substring(exStorageDir.length()+1);
            if(path.endsWith("/")) path = path.substring(0, path.length()-1);
            int index = 0;
            while(index >= 0){
                index = path.indexOf("/", index);
                if(index > 0){
                    depth++;
                    index++;
                }
            }
            name = depth == -1 ? path : path.substring(path.lastIndexOf("/")+1);
        }

        @Override
        public int compareTo(LibItem libItem){
            return this.name.compareTo(libItem.name);
        }
    }
    public static class LibTrack extends LibItem{
        public LibTrack(URI uri){
            super(uri);
        }
    }
    public static class LibDir extends LibItem{
        public final ArrayList<LibTrack> libTracks = new ArrayList<>();
        public final ArrayList<LibDir> libDirs = new ArrayList<>();
        public LibDir(URI uri){
            super(uri);
        }
        public void sort(){
            Collections.sort(libTracks);
            Collections.sort(libDirs);
        }
    }

}
