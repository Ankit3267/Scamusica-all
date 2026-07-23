package com.musicplayer.scamusica.model;

public class VolumeSchedule {
    private int id;
    private String start_time;
    private String end_time;
    private int music_volume;
    private int ad_volume;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getStartTime() {
        return start_time;
    }

    public void setStartTime(String start_time) {
        this.start_time = start_time;
    }

    public String getEndTime() {
        return end_time;
    }

    public void setEndTime(String end_time) {
        this.end_time = end_time;
    }

    public int getMusicVolume() {
        return music_volume;
    }

    public void setMusicVolume(int music_volume) {
        this.music_volume = music_volume;
    }

    public int getAdVolume() {
        return ad_volume;
    }

    public void setAdVolume(int ad_volume) {
        this.ad_volume = ad_volume;
    }
}
