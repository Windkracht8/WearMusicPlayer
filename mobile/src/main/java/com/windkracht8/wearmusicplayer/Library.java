package com.windkracht8.wearmusicplayer;

import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

class Library{
    private static String exStorageDir;
    static LibDir root_libDir;

    static void scanFiles(Main main){
        if(!Main.hasReadPermission) return;
        exStorageDir = Environment.getExternalStorageDirectory().toString() + "/";
        scanFiles(main, "Music");
    }
    static void scanFiles(Main main, String dir){
        if(!Main.hasReadPermission) return;
        root_libDir = new LibDir(dir);
        scanFilesDir(root_libDir);
        main.libraryFilesScanned();
    }

    private static void scanFilesDir(LibDir libDir){
        File[] files = (new File(libDir.getFullPath())).listFiles();
        if(files == null) return;
        for(File file : files){
            if(file.getName().startsWith(".")) continue;
            if(file.isDirectory()){
                LibDir libDir_sub = new LibDir(file.getAbsolutePath());
                scanFilesDir(libDir_sub);
                if(libDir_sub.libDirs.isEmpty() && libDir_sub.libTracks.isEmpty()) continue;
                libDir.libDirs.add(libDir_sub);
            }
            if(!isTrack(file.getName())) continue;
            libDir.libTracks.add(new LibTrack(file.getAbsolutePath()));
        }
        libDir.sort();
    }

    static void updateLibWithFilesOnWatch(Main main, JSONArray filesOnWatch){
        ArrayList<String> pathsOnWatch = new ArrayList<>();
        try{
            for(int i=0; i<filesOnWatch.length(); i++){
                JSONObject object = filesOnWatch.getJSONObject(i);
                String path = object.getString("path");
                if(path.startsWith("Music/")) path = path.substring(6);
                pathsOnWatch.add(Uri.decode(path));
            }
            updateDirWithFilesOnWatch(pathsOnWatch, root_libDir);
            checkStatuses(root_libDir);
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
            if(libSubDir.status != LibItem.Status.NOT){
                partial = true;
            }
            if(libSubDir.status != LibItem.Status.FULL){
                full = false;
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
        enum Status{FULL, PARTIAL, NOT, UNKNOWN}
        final String path;
        final String name;
        int depth = 0;
        Status status = Status.UNKNOWN;
        long length = 1;
        LibItem(String path){
            if(root_libDir != null){
                String root_libDir_full = root_libDir.getFullPath() + "/";
                if(path.startsWith(root_libDir_full)) path = path.substring(root_libDir_full.length());
            }
            if(path.endsWith("/")) path = path.substring(0, path.length()-1);
            int index = 1;
            while(index >= 0){
                index = path.indexOf("/", index);
                if(index > 0){
                    depth++;
                    index++;
                }
            }
            name = depth == 0 ? path : path.substring(path.lastIndexOf("/")+1);
            this.path = path;
        }
        String getFullPath(){
            if(this == root_libDir) return exStorageDir + path;
            return root_libDir.getFullPath() + "/" + path;
        }
        void clearStatus(){status = Status.UNKNOWN;}
        void setStatusNot(Main main){
            status = Status.NOT;
            checkStatuses(root_libDir);
            main.libraryNewStatuses();
        }
        @Override
        public int compareTo(LibItem libItem){return name.compareTo(libItem.name);}
    }
    static class LibTrack extends LibItem{
        private LibTrack(String path){super(path);}
    }
    static class LibDir extends LibItem{
        final ArrayList<LibTrack> libTracks = new ArrayList<>();
        final ArrayList<LibDir> libDirs = new ArrayList<>();
        private LibDir(String path){super(path);}
        private void sort(){
            Collections.sort(libTracks);
            Collections.sort(libDirs);
        }
    }
}