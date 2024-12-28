package com.windkracht8.wearmusicplayer;

import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;

class Library{
    private final Main main;
    static String exStorageDir;
    static File filePendingDelete;
    final ArrayList<Track> tracks = new ArrayList<>();
    final ArrayList<Artist> artists = new ArrayList<>();
    final ArrayList<Album> albums = new ArrayList<>();

    Library(Main main){
        this.main = main;
        exStorageDir = Environment.getExternalStorageDirectory().toString();
    }
    Album getAlbum(int id){
        for(Album album : albums){if(album.id == id){return album;}}
        return null;
    }
    JSONArray getTracks(){
        JSONArray array = new JSONArray();
        for(Track track : tracks){
            array.put(track.toJson(main));
        }
        return array;
    }
    void scanMediaStore(){
        Log.d(Main.LOG_TAG, "Library.scanMediaStore");
        tracks.clear();
        artists.clear();
        albums.clear();

        String[] projection = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.MediaColumns.DATA,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ARTIST,
                MediaStore.Audio.Media.CD_TRACK_NUMBER,
                MediaStore.Audio.Media.DISC_NUMBER
        };

        try(Cursor cursor = main.getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                null,
                null
        )) {
            Log.d(Main.LOG_TAG, "Library.scanMediaStore query done");
            if(cursor == null){
                Log.e(Main.LOG_TAG, "Library.scanMediaStore: Cursor is null");
                main.toast(R.string.fail_scan_media);
                return;
            }
            int ID = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            int DATA = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA);
            int TITLE = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
            int ARTIST = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
            int ALBUM = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM);
            int ALBUM_ARTIST = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST);
            int CD_TRACK_NUMBER = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.CD_TRACK_NUMBER);
            int DISC_NUMBER = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISC_NUMBER);

            while(cursor.moveToNext()){
                Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(ID));
                new Track(main,
                        contentUri,
                        cursor.getString(DATA),
                        cursor.getString(TITLE),
                        cursor.getString(ARTIST),
                        cursor.getString(ALBUM),
                        cursor.getString(ALBUM_ARTIST),
                        cursor.getString(CD_TRACK_NUMBER),
                        cursor.getString(DISC_NUMBER)
                );
            }
        }
        try{
            Log.d(Main.LOG_TAG, "Library.scanMediaStore sort");
            Collections.sort(tracks);
            Collections.sort(artists);
            Collections.sort(albums);
            artists.forEach(Artist::sort);
            albums.forEach(Album::sort);
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Library.scanMediaStore sort exception: " + e.getMessage());
            main.toast(R.string.fail_scan_media);
        }

        Log.d(Main.LOG_TAG, "Library.scanMediaStore ready");
        main.libraryReady();
    }
    void scanFiles(){
        Log.d(Main.LOG_TAG, "Library.scanFiles");
        main.librarySetScanning();
        ArrayList<String> paths = new ArrayList<>();
        paths.add(exStorageDir);
        MediaScannerConnection.scanFile(main,
                paths.toArray(new String[0]),
                null,
                (path1, uri) -> scanMediaStore()
        );
    }
    String ensurePath(String path){
        File file = new File(exStorageDir + "/" + path);
        try{
            if(file.exists()){
                Log.e(Main.LOG_TAG, "Library.ensurePath: file exists");
                main.toast(R.string.fail_file_exists);
                return main.getString(R.string.fail_file_exists);
            }

            File parent = file.getParentFile();
            if(parent == null){
                Log.e(Main.LOG_TAG, "Library.ensurePath: getParentFile == null");
                main.toast(R.string.fail_create_parent);
                return main.getString(R.string.fail_create_parent);
            }
            if(!parent.exists()){
                if(!parent.mkdirs()){
                    Log.e(Main.LOG_TAG, "Library.ensurePath: mkdirs");
                    main.toast(R.string.fail_create_dirs);
                    return main.getString(R.string.fail_create_dirs);
                }
            }
            if(!file.createNewFile()){
                Log.e(Main.LOG_TAG, "Library.ensurePath: createNewFile");
                main.toast(R.string.fail_create_file);
                return main.getString(R.string.fail_create_file);
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Library.ensurePath exception: " + e.getMessage());
            main.toast(R.string.fail_create_file);
            return e.getMessage();
        }
        return null;
    }
    void addFile(String path){
        File file = new File(exStorageDir + "/" + path);
        scanFile(file.toString());
    }
    void scanFile(String path){
        MediaScannerConnection.scanFile(main,
                new String[]{path},
                null,
                (path1, uri) -> scanMediaStore()
        );
        filePendingDelete = null;
    }
    String deleteFile(String path){
        filePendingDelete = new File(exStorageDir + "/" + path);
        if(!filePendingDelete.exists()){
            Log.i(Main.LOG_TAG, "Library.deleteFile: path does not exists");
            return "OK";
        }
        Uri uri = getUriForPath(path);
        if(uri == null) return main.getString(R.string.fail_technical);
        if(main.checkUriPermission(
                uri
                ,android.os.Process.myPid()
                ,android.os.Process.myUid()
                ,Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            ) == PackageManager.PERMISSION_DENIED
        ){
            PendingIntent pendingIntent = MediaStore.createDeleteRequest(
                    main.getContentResolver()
                    ,new ArrayList<>(Collections.singletonList(uri))
            );
            try{
                main.startIntentSenderForResult(
                        pendingIntent.getIntentSender()
                        ,5, null, 0, 0, 0
                );
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "Library.deleteFile: startIntentSenderForResult: " + e.getMessage());
                return main.getString(R.string.fail_technical);
            }
            return "PENDING";
        }
        if(!filePendingDelete.delete()){
            Log.e(Main.LOG_TAG, "Library.deleteFile: delete failed");
            main.toast(R.string.fail_delete_file);
            return main.getString(R.string.fail_delete_file);
        }
        scanFile(filePendingDelete.toString());
        return "OK";
    }
    public class Track implements Comparable<Track>{
        final Uri uri;//content://media/external/audio/media/32
        private final String path;//Music/Austrian Death Machine - Jingle Bells.mp3
        final String title;//Jingle Bells
        final Artist artist;
        Album album;
        private final String track_no;
        private final String disc_no;
        private Track(Context context, Uri uri, String path, String title, String artistName
                ,String albumName, String albumArtist, String track_no, String disc_no){
            this.uri = uri;
            this.path = path.substring(exStorageDir.length()+1);
            this.title = title;
            this.track_no = track_no;
            this.disc_no = disc_no;

            artist = getArtistForNewTrack(this, artistName);
            if(albumName != null && !albumName.equals("Music")){
                album = getAlbumForNewTrack(context, this, albumName, albumArtist);
                artist.addAlbum(album);
            }
            tracks.add(this);
        }
        private JSONObject toJson(Main main){
            JSONObject object = new JSONObject();
            try{
                object.put("path", path);
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "Library.Track.toJson Exception: " + e.getMessage());
                main.toast(R.string.fail_respond);
            }
            return object;
        }
        @Override
        public int compareTo(Track track){
            if(album != null && track.album != null){
                int compare = album.name.compareTo(track.album.name);
                if(compare != 0) return compare;
            }
            if(disc_no != null && track.disc_no != null){
                int compare = disc_no.compareTo(track.disc_no);
                if(compare != 0) return compare;
            }
            if(track_no != null && track.track_no != null){
                int compare;
                try{
                    compare = Integer.valueOf(track_no).compareTo(Integer.valueOf(track.track_no));
                }catch(Exception e){
                    compare = track_no.compareTo(track.track_no);
                }
                if(compare != 0) return compare;
            }
            return title.compareTo(track.title);
        }
    }
    class Artist implements Comparable<Artist>{
        final String name;
        final ArrayList<Track> tracks = new ArrayList<>();
        final ArrayList<Album> albums = new ArrayList<>();
        private Artist(Track track, String artistName){
            name = artistName == null ? "<empty>" : artistName;
            tracks.add(track);
            artists.add(this);
        }
        void addAlbum(Album album){
            if(albums.contains(album)) return;
            albums.add(album);
        }
        @Override
        public int compareTo(Artist artist){
            return name.compareTo(artist.name);
        }
        private void sort(){
            Collections.sort(tracks);
            Collections.sort(albums);
        }
    }
    private Artist getArtistForNewTrack(Track track, String artistName){
        for(Artist artist : artists){
            if(artist.name.equals(artistName)){
                artist.tracks.add(track);
                return artist;
            }
        }
        return new Artist(track, artistName);
    }
    class Album implements Comparable<Album>{
        final int id;
        final String name;
        String artist;
        final ArrayList<Track> tracks = new ArrayList<>();
        private Album(Track track, String albumName, String albumArtist){
            id = albums.size();
            name = albumName == null ? "<empty>" : albumName;
            artist = albumArtist == null ? track.artist.name : albumArtist;
            tracks.add(track);
            albums.add(this);
        }
        @Override
        public int compareTo(Album album){return name.compareTo(album.name);}
        private void sort(){Collections.sort(tracks);}
    }
    private Album getAlbumForNewTrack(Context context, Track track, String albumName, String albumArtist){
        for(Album album : albums){
            if(album.name.equals(albumName)){
                if(albumArtist != null && album.artist != null && !album.artist.equals(albumArtist)){
                    album.artist = context.getString(R.string.various_artists);
                }
                album.tracks.add(track);
                return album;
            }
        }
        return new Album(track, albumName, albumArtist);
    }
    private Uri getUriForPath(String path){
        for(Track track : tracks){if(track.path.equals(path)){return track.uri;}}
        return null;
    }
}