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

class Library{
    private static String exStorageDir;
    static LibDir dir_music;

    static void scanFiles(Main main){
        if(!Main.hasReadPermission) return;
        exStorageDir = Environment.getExternalStorageDirectory().toString();
        dir_music = new LibDir(URI.create(exStorageDir + "/Music"));
        scanFilesDir(dir_music);
        main.libraryFilesScanned();
    }
    private static void scanFilesDir(LibDir libDir){
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

    static void updateLibWithFilesOnWatch(Main main, JSONArray filesOnWatch){
        ArrayList<String> pathsOnWatch = new ArrayList<>();
        try{
            for(int i = 0; i < filesOnWatch.length(); i++){
                JSONObject object = filesOnWatch.getJSONObject(i);
                String path = object.getString("path");
                pathsOnWatch.add(Uri.decode(path));
            }
            updateDirWithFilesOnWatch(pathsOnWatch, dir_music);
            checkStatuses(dir_music);
            main.libraryNewStatuses();
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Library.updateLibWithFilesOnWatch: " + e.getMessage());
        }
    }
    private static void updateDirWithFilesOnWatch(ArrayList<String> pathsOnWatch, LibDir libDir){
        libDir.status = LibItem.Status.UNKNOWN;
        for(LibDir libSubdir : libDir.libDirs){
            updateDirWithFilesOnWatch(pathsOnWatch, libSubdir);
        }
        for(LibTrack libTrack : libDir.libTracks){
            if(pathsOnWatch.contains(libTrack.path)){
                libTrack.status = LibItem.Status.FULL;
            }else{
                libTrack.status = LibItem.Status.NOT;
            }
        }
    }

    private static boolean isTrack(String name){
        return name.endsWith(".mp3") || name.endsWith(".m4a");
    }
    private static void checkStatuses(LibDir libDir){
        boolean partial = false;
        boolean full = true;
        for(LibTrack libTrack : libDir.libTracks){
            if(libTrack.status == LibItem.Status.NOT){
                full = false;
            }else{
                partial = true;
            }
        }
        for(LibDir libSubDir : libDir.libDirs){
            checkStatuses(libSubDir);
            if(libSubDir.status == LibItem.Status.NOT){
                full = false;
            }else{
                partial = true;
            }
        }
        if(full){
            libDir.status = LibItem.Status.FULL;
        }else if(partial){
            libDir.status = LibItem.Status.PARTIAL;
        }else{
            libDir.status = LibItem.Status.NOT;
        }
    }

    static class LibItem implements Comparable<LibItem>{
        enum Status {FULL, PARTIAL, NOT, UNKNOWN}
        final URI uri;
        String path;
        final String name;
        int depth = -1;
        Status status = Status.UNKNOWN;
        long length = 1;
        LibItem(URI uri){
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
        void clearStatus(){
            status = Status.UNKNOWN;
        }
        void setStatus(Main main, Status status){
            this.status = status;
            checkStatuses(dir_music);
            main.libraryNewStatuses();
        }
        @Override
        public int compareTo(LibItem libItem){return name.compareTo(libItem.name);}
    }
    static class LibTrack extends LibItem{
        private LibTrack(URI uri){super(uri);}
    }
    static class LibDir extends LibItem{
        final ArrayList<LibTrack> libTracks = new ArrayList<>();
        final ArrayList<LibDir> libDirs = new ArrayList<>();
        private LibDir(URI uri){super(uri);}
        private void sort(){
            Collections.sort(libTracks);
            Collections.sort(libDirs);
        }
    }

}
