package com.windkracht8.musicplayer;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

public class Library{
    public static String exStorageDir;
    public final ArrayList<Track> tracks = new ArrayList<>();
    public final ArrayList<Artist> artists = new ArrayList<>();
    public final ArrayList<Album> albums = new ArrayList<>();

    public static int libraryScanVersion = 0;

    private final Player player = new Player();

    //TODO: toast errors
    public Library(){
        exStorageDir = Environment.getExternalStorageDirectory().toString();
    }
    public JSONArray getTracks(){
        JSONArray array = new JSONArray();
        for(Track track : tracks){
            array.put(track.toJson());
        }
        return array;
    }
    public void scanMediaStore(Main main){
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
            if(cursor == null){
                Log.e(Main.LOG_TAG, "Library.rescan: Cursor is null");
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
        Collections.sort(tracks);
        Collections.sort(artists);
        Collections.sort(albums);
        for(Artist artist : artists) artist.sort();
        for(Album album : albums) album.sort();

        libraryScanVersion++;
        if(libraryScanVersion == 1){
            main.handler_message.sendMessage(main.handler_message.obtainMessage(Main.MESSAGE_LIBRARY_READY));
            scanFiles(main);
        }
    }
    public void scanFiles(Main main){
        if(scanFiles(main, "")){
            scanMediaStore(main);
        }
    }
    public boolean scanFiles(Main main, String subdir){
        boolean addedTracks = false;
        String path = exStorageDir+"/Music"+subdir;
        File directory = new File(path);
        File[] files = directory.listFiles();
        if(files == null) return false;
        for(File file : files){
            if(file.isDirectory()){
                if(scanFiles(main, subdir+"/"+file.getName()))
                    addedTracks = true;
            }
            if(!file.getName().endsWith(".mp3")) continue;
            if(!trackExists(file.toURI())){
                player.load(main, Uri.fromFile(file));
                addedTracks = true;
            }
        }
        return addedTracks;
    }
    private boolean trackExists(URI uri){
        String path = uri.getPath().substring(exStorageDir.length()+1);
        for(Track track : tracks){
            if(track.path.equals(path)) return true;
        }
        return false;
    }
    public boolean ensurePath(String path){
        File file = new File(exStorageDir + "/" + path);
        try{
            if(file.exists()){
                Log.e(Main.LOG_TAG, "Library.ensurePath: path exists");
                return false;
            }

            File parent = file.getParentFile();
            if(parent == null){
                Log.e(Main.LOG_TAG, "Library.ensurePath: getParentFile == null");
                return false;
            }
            if(!parent.exists()){
                if(!parent.mkdirs()){
                    Log.e(Main.LOG_TAG, "Library.ensurePath: mkdirs");
                    return false;
                }
            }
            if(!file.createNewFile()){
                Log.e(Main.LOG_TAG, "Library.ensurePath: createNewFile");
                return false;
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Library.ensurePath exception: " + e.getMessage());
            return false;
        }
        return true;
    }
    public void addFile(Main main, String path){
        File file = new File(exStorageDir + "/" + path);
        player.load(main, Uri.fromFile(file));
        scanMediaStore(main);
    }
    public String deleteFile(String path){
        File file = new File(exStorageDir + "/" + path);
        try{
            if(!file.exists()){
                Log.e(Main.LOG_TAG, "Library.deleteFile: path does not exists");
                return "OK";
            }
            if(!file.delete()){
                Log.e(Main.LOG_TAG, "Library.deleteFile: delete");
                return "delete failed";
            }
        }catch(Exception e){
            Log.e(Main.LOG_TAG, "Library.deleteFile exception: " + e.getMessage());
            return e.getMessage();
        }
        //TODO: remove from tracks, albums, artists, and reload MediaStore
        return "OK";
    }
    public class Track implements Comparable<Track>{
        public Uri uri;//content://media/external/audio/media/32
        public String path;//Music/Austrian Death Machine - Jingle Bells.mp3
        public String title;//Jingle Bells
        public Artist artist;
        public Album album;
        public String track_no;
        public Track(Context context, Uri uri, String path, String title, String artistName, String albumName, String albumArtist, String track_no){
            this.uri = uri;
            this.path = path.substring(exStorageDir.length()+1);
            this.title = title;
            this.track_no = track_no;

            artist = getArtistForNewTrack(this, artistName);
            album = getAlbumForNewTrack(context, this, albumName, albumArtist);
            artist.addAlbum(album);
            tracks.add(this);
        }
        public JSONObject toJson(){
            JSONObject object = new JSONObject();
            try{
                object.put("path", path);
            }catch(Exception e){
                Log.e(Main.LOG_TAG, "Library.Track.toJson: " + e.getMessage());
                //TODO: toast
            }
            return object;
        }
        @Override
        public int compareTo(Track track){
            int album = this.album.name.compareTo(track.album.name);
            if(album != 0) return album;
            int track_no = this.track_no.compareTo(track.track_no);
            if(track_no != 0) return track_no;
            return this.title.compareTo(track.title);
        }
    }
    public class Artist implements Comparable<Artist>{
        public String name;
        public final ArrayList<Track> artist_tracks = new ArrayList<>();
        public final ArrayList<Album> artist_albums = new ArrayList<>();
        public Artist(Track track, String artistName){
            name = artistName == null ? "<empty>" : artistName;
            artist_tracks.add(track);
            artists.add(this);
        }
        public void addAlbum(Album album){
            if(artist_albums.contains(album)) return;
            artist_albums.add(album);
        }
        @Override
        public int compareTo(Artist artist){
            return this.name.compareTo(artist.name);
        }
        public void sort(){
            Collections.sort(artist_tracks);
            Collections.sort(artist_albums);
        }
    }
    public Artist getArtistForNewTrack(Track track, String artistName){
        for(Artist artist : artists){
            if(artist.name.equals(artistName)){
                artist.artist_tracks.add(track);
                return artist;
            }
        }
        return new Artist(track, artistName);
    }
    public class Album implements Comparable<Album>{
        public String name;
        public String artist;
        public final ArrayList<Track> album_tracks = new ArrayList<>();
        public Album(Track track, String albumName, String albumArtist){
            name = albumName == null ? "<empty>" : albumName;
            artist = albumArtist;
            album_tracks.add(track);
            albums.add(this);
        }
        @Override
        public int compareTo(Album album){
            return this.name.compareTo(album.name);
        }
        public void sort(){
            Collections.sort(album_tracks);
        }
    }
    public Album getAlbumForNewTrack(Context context, Track track, String albumName, String albumArtist){
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