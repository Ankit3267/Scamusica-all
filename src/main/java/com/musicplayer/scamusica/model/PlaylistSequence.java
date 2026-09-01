package com.musicplayer.scamusica.model;

import java.util.Objects;

public class PlaylistSequence {
    private String title;
    private String startTime;
    private String endTime;

    public PlaylistSequence() {
    }

    public PlaylistSequence(String title, String startTime, String endTime) {
        this.title = title;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PlaylistSequence that = (PlaylistSequence) o;
        return Objects.equals(title, that.title) &&
               Objects.equals(startTime, that.startTime) &&
               Objects.equals(endTime, that.endTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, startTime, endTime);
    }

    @Override
    public String toString() {
        return "PlaylistSequence{" +
                "title='" + title + '\'' +
                ", startTime='" + startTime + '\'' +
                ", endTime='" + endTime + '\'' +
                '}';
    }
}
