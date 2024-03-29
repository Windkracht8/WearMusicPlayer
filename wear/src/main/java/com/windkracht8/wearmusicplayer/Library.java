package com.windkracht8.wearmusicplayer;

import android.content.ContentUris;
import android.content.Context;
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
    static String exStorageDir;
    final ArrayList<Track> tracks = new ArrayList<>();
    final ArrayList<Artist> artists = new ArrayList<>();
    final ArrayList<Album> albums = new ArrayList<>();

    static int libraryScanVersion = 0;

    Library(){
        exStorageDir = Environment.getExternalStorageDirectory().toString();
    }
    JSONArray getTracks(Main main){
        JSONArray array = new JSONArray();
        for(Track track : tracks){
            array.put(track.toJson(main));
        }
        return array;
    }
    void scanMediaStore(Main main){
        if(!Main.hasReadPermission) return;
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
                MediaStore.Audio.Media.CD_TRACK_NUMBER
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

            while(cursor.moveToNext()){
                Uri contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(ID));
                new Track(main,
                        contentUri,
                        cursor.getString(DATA),
                        cursor.getString(TITLE),
                        cursor.getString(ARTIST),
                        cursor.getString(ALBUM),
                        cursor.getString(ALBUM_ARTIST),
                        cursor.getString(CD_TRACK_NUMBER)
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
        libraryScanVersion++;
        main.libraryReady();
    }
    void scanFiles(Main main){
        Log.d(Main.LOG_TAG, "Library.scanFiles");
        ArrayList<String> paths = new ArrayList<>();
        paths.add(exStorageDir);
        MediaScannerConnection.scanFile(main,
                paths.toArray(new String[0]),
                null,
                (path1, uri) -> scanMediaStore(main)
        );
    }
    boolean ensurePath(Main main, String path){
        File file = new File(exStorageDir + "/" + path);
        try{
            if(file.exists()){
                Log.i(Main.LOG_TAG, "Library.ensurePath: path exists");
                return false;
            }

            File parent = file.getParentFile();
            if(parent == null){
                Log.e(Main.LOG_TAG, "Library.ensurePath: getParentFile == null");
                main.toast(R.string.fail_create_file);
                return false;
            }
            if(!parent.exists()){
                if(!parent.mkdirs()){
                    Log.e(Main.LOG_TAG, "Library.ensurePath: mkdirs");
                    main.toast(R.string.fail_create_file);
                    return false;
                }
            }
            if(!file.createNewFile()){
                Log.e(Main.LOG_TAG, "Library.ensurePath: createNewFile");
                main.toast(R.string.fail_create_file);
                return false;
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Library.ensurePath exception: " + e.getMessage());
            main.toast(R.string.fail_create_file);
            return false;
        }
        return true;
    }
    void addFile(Main main, String path){
        File file = new File(exStorageDir + "/" + path);
        scanFile(main, file);
    }
    private void scanFile(Main main, File file){
        MediaScannerConnection.scanFile(main,
                new String[]{file.toString()},
                null,
                (path1, uri) -> scanMediaStore(main)
        );
    }
    String deleteFile(Main main, String path){
        File file = new File(exStorageDir + "/" + path);
        Log.d(Main.LOG_TAG, "Library.deleteFile: " + exStorageDir + "/" + path);
        try{
            if(!file.exists()){
                Log.i(Main.LOG_TAG, "Library.deleteFile: path does not exists");
                return "OK";
            }
            if(!file.canWrite()){
                Log.e(Main.LOG_TAG, "Library.deleteFile: cannot write");
                main.toast(R.string.fail_delete_file);
                return "delete not allowed";
            }
            if(!file.delete()){
                Log.e(Main.LOG_TAG, "Library.deleteFile: delete");
                main.toast(R.string.fail_delete_file);
                return "delete failed";
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Library.deleteFile Exception: " + e.getMessage());
            main.toast(R.string.fail_delete_file);
            return e.getMessage();
        }
        scanFile(main, file);
        return "OK";
    }
    class Track implements Comparable<Track>{
        final Uri uri;//content://media/external/audio/media/32
        private final String path;//Music/Austrian Death Machine - Jingle Bells.mp3
        final String title;//Jingle Bells
        final Artist artist;
        private final Album album;
        private final String track_no;
        private Track(Context context, Uri uri, String path, String title, String artistName, String albumName, String albumArtist, String track_no){
            this.uri = uri;
            this.path = path.substring(exStorageDir.length()+1);
            this.title = title;
            this.track_no = track_no;

            artist = getArtistForNewTrack(this, artistName);
            album = getAlbumForNewTrack(context, this, albumName, albumArtist);
            artist.addAlbum(album);
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
            int compare = album.name.compareTo(track.album.name);
            if(compare != 0) return compare;
            if(track_no != null && track.track_no != null){
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
        final ArrayList<Track> artist_tracks = new ArrayList<>();
        final ArrayList<Album> artist_albums = new ArrayList<>();
        private Artist(Track track, String artistName){
            name = artistName == null ? "<empty>" : artistName;
            artist_tracks.add(track);
            artists.add(this);
        }
        void addAlbum(Album album){
            if(artist_albums.contains(album)) return;
            artist_albums.add(album);
        }
        @Override
        public int compareTo(Artist artist){
            return name.compareTo(artist.name);
        }
        private void sort(){
            Collections.sort(artist_tracks);
            Collections.sort(artist_albums);
        }
    }
    private Artist getArtistForNewTrack(Track track, String artistName){
        for(Artist artist : artists){
            if(artist.name.equals(artistName)){
                artist.artist_tracks.add(track);
                return artist;
            }
        }
        return new Artist(track, artistName);
    }
    class Album implements Comparable<Album>{
        final String name;
        String artist;
        final ArrayList<Track> album_tracks = new ArrayList<>();
        private Album(Track track, String albumName, String albumArtist){
            name = albumName == null ? "<empty>" : albumName;
            artist = albumArtist;
            album_tracks.add(track);
            albums.add(this);
        }
        @Override
        public int compareTo(Album album){
            return name.compareTo(album.name);
        }
        private void sort(){
            Collections.sort(album_tracks);
        }
    }
    private Album getAlbumForNewTrack(Context context, Track track, String albumName, String albumArtist){
        for(Album album : albums){
            if(album.name.equals(albumName)){
                if(album.artist != null && !album.artist.equals(albumArtist)){
                    album.artist = context.getString(R.string.various_artists);
                }
                album.album_tracks.add(track);
                return album;
            }
        }
        return new Album(track, albumName, albumArtist);
    }
}