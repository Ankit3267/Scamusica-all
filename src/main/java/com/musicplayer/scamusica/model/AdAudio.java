package com.musicplayer.scamusica.model;

import com.google.gson.annotations.SerializedName;

public class AdAudio {

    private Integer id;

    @SerializedName("ad_id")
    private Integer adId;

    @SerializedName("audio_file")
    private String audioFile;

    @SerializedName("audio_source")
    private String audioSource;

    @SerializedName("sort_order")
    private Integer sortOrder;

    public AdAudio() {}

    public AdAudio(Integer id, Integer adId, String audioFile, String audioSource, Integer sortOrder) {
        this.id = id;
        this.adId = adId;
        this.audioFile = audioFile;
        this.audioSource = audioSource;
        this.sortOrder = sortOrder;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getAdId() {
        return adId;
    }

    public void setAdId(Integer adId) {
        this.adId = adId;
    }

    public String getAudioFile() {
        return audioFile;
    }

    public void setAudioFile(String audioFile) {
        this.audioFile = audioFile;
    }

    public String getAudioSource() {
        return audioSource;
    }

    public void setAudioSource(String audioSource) {
        this.audioSource = audioSource;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    @Override
    public String toString() {
        return "AdAudio{" +
                "id=" + id +
                ", audioFile='" + audioFile + '\'' +
                ", sortOrder=" + sortOrder +
                '}';
    }
}
