package org.phlo.AirReceiver;

import java.nio.ByteBuffer;
import java.util.Map;

public class SongInfo {
    private String album;
    private String artist;
    private String comment;
    private String genre;
    private String title;
    private String composer;
    private String fileKind;
    private String sort;
    private ByteBuffer picture;

    public SongInfo(String album, String artist, String comment, String genre, String title, String composer, String fileKind, String sort) {
        this.album = album;
        this.artist = artist;
        this.comment = comment;
        this.genre = genre;
        this.title = title;
        this.composer = composer;
        this.fileKind = fileKind;
        this.sort = sort;
    }

    public static SongInfo createSongInfo(Map<String, String> map) {
        String album = map.get("asal");
        String artist = map.get("asar");
        String comment = map.get("ascm");
        String genre = map.get("asgn");
        String title = map.get("minm");
        String composer = map.get("ascp");
        String fileKind = map.get("asdt");
        String sort = map.get("assn");
        return new SongInfo(album, artist, comment, genre, title, composer, fileKind, sort);
    }

    public String getAlbum() {
        return album;
    }

    public void setAlbum(String album) {
        this.album = album;
    }

    public String getArtist() {
        return artist;
    }

    public void setArtist(String artist) {
        this.artist = artist;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getGenre() {
        return genre;
    }

    public void setGenre(String genre) {
        this.genre = genre;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getComposer() {
        return composer;
    }

    public void setComposer(String composer) {
        this.composer = composer;
    }

    public String getFileKind() {
        return fileKind;
    }

    public void setFileKind(String fileKind) {
        this.fileKind = fileKind;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public ByteBuffer getPicture() {
        return picture;
    }

    public void setPicture(ByteBuffer picture) {
        this.picture = picture;
    }
}
