package com.musicplayer.scamusica.model;

public class PlaylistTrack {

    private Integer id;
    private String title;
    private String url;
    private int durationSeconds;
    private String folderTitle;
    private String albumImageUrl;

    public PlaylistTrack(Integer id, String title, String url, int durationSeconds, String folderTitle,
                         String albumImageUrl) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.durationSeconds = durationSeconds;
        this.folderTitle = folderTitle;
        this.albumImageUrl = albumImageUrl;
    }

    public PlaylistTrack(String title, String url, int durationSeconds) {
        this(null, title, url, durationSeconds, null, null);
    }

    public PlaylistTrack(String title, String url, int durationSeconds, String folderTitle) {
        this(null, title, url, durationSeconds, folderTitle, null);
    }

    public Integer getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(int durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public String getFolderTitle() {
        return folderTitle;
    }

    public void setFolderTitle(String folderTitle) {
        this.folderTitle = folderTitle;
    }

    public String getAlbumImageUrl() {
        return albumImageUrl;
    }

    public void setAlbumImageUrl(String albumImageUrl) {
        this.albumImageUrl = albumImageUrl;
    }

    @Override
    public String toString() {
        return "PlaylistTrack{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", url='" + url + '\'' +
                ", durationSeconds=" + durationSeconds +
                ", folderTitle='" + folderTitle + '\'' +
                ", albumImageUrl='" + albumImageUrl + '\'' +
                '}';
    }
}
