package com.musicplayer.scamusica.model;

public class VolumeSchedule {
    private Integer id;
    private String start_time;
    private String end_time;
    private Integer music_volume;
    private Integer ad_volume;

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
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

    public Integer getMusicVolume() {
        return music_volume;
    }

    public void setMusicVolume(Integer music_volume) {
        this.music_volume = music_volume;
    }

    public Integer getAdVolume() {
        return ad_volume;
    }

    public void setAdVolume(Integer ad_volume) {
        this.ad_volume = ad_volume;
    }
}
