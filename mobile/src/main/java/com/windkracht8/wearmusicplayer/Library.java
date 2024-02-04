package com.windkracht8.wearmusicplayer;

import android.net.Uri;
import android.os.Environment;
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
        main.runOnUiThread(main::loadFileList);
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

    public void updateLibWithFilesOnWatch(Main main, JSONArray filesOnWatch){
        ArrayList<String> pathsOnWatch = new ArrayList<>();
        try{
            for(int i = 0; i < filesOnWatch.length(); i++){
                JSONObject object = filesOnWatch.getJSONObject(i);
                String path = object.getString("path");
                pathsOnWatch.add(Uri.decode(path));
            }
            updateDirWithFilesOnWatch(pathsOnWatch, dir_music);
            main.runOnUiThread(main::itemsNewStatus);
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
                libTrack.status = LibItem.Status.FULL;
                partial = true;
            }else{
                libTrack.status = LibItem.Status.NOT;
                full = false;
            }
        }
        for(LibDir libDir : directory.libDirs){
            if(libDir.status == LibItem.Status.FULL){
                partial = true;
            }else if(libDir.status == LibItem.Status.PARTIAL){
                partial = true;
                full = false;
            }else{
                full = false;
            }
        }
        if(full){
            directory.status = LibItem.Status.FULL;
        }else if(partial){
            directory.status = LibItem.Status.PARTIAL;
        }else{
            directory.status = LibItem.Status.NOT;
        }
    }

    private boolean isTrack(String name){
        return name.endsWith(".mp3") || name.endsWith(".m4a");
    }

    public static class LibItem implements Comparable<LibItem>{
        public enum Status {FULL, PARTIAL, NOT, UNKNOWN}
        public final URI uri;
        public String path;
        public final String name;
        public int depth = -1;
        public Status status = Status.UNKNOWN;
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
