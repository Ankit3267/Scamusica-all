package com.musicplayer.scamusica.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public class Ad {

    private Integer id;

    @SerializedName("campaign_name")
    private String campaignName;

    @SerializedName("adAudios")
    private List<AdAudio> adAudios;

    @SerializedName("schedule_type")
    private String scheduleType;

    @SerializedName("play_times")
    private Object playTimes;

    @SerializedName("start_date")
    private String startDate;

    @SerializedName("end_date")
    private String endDate;

    @SerializedName("active_days")
    private List<String> activeDays;

    private String status;


    public Ad() {
    }

    public Ad(Integer id, String campaignName, List<AdAudio> adAudios,
              String scheduleType, Object playTimes, String startDate, String endDate,
              List<String> activeDays, String status) {
        this.id = id;
        this.campaignName = campaignName;
        this.adAudios = adAudios;
        this.scheduleType = scheduleType;
        this.playTimes = playTimes;
        this.startDate = startDate;
        this.endDate = endDate;
        this.activeDays = activeDays;
        this.status = status;
    }

    // Getters
    public Integer getId() {
        return id;
    }

    public String getCampaignName() {
        return campaignName;
    }

    public List<AdAudio> getAdAudios() {
        return adAudios;
    }

    public String getScheduleType() {
        return scheduleType;
    }

    public Object getPlayTimes() {
        return playTimes;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public List<String> getActiveDays() {
        return activeDays;
    }

    public String getStatus() {
        return status;
    }


    // Setters
    public void setId(Integer id) {
        this.id = id;
    }

    public void setCampaignName(String campaignName) {
        this.campaignName = campaignName;
    }

    public void setAdAudios(List<AdAudio> adAudios) {
        this.adAudios = adAudios;
    }

    public void setScheduleType(String scheduleType) {
        this.scheduleType = scheduleType;
    }

    public void setPlayTimes(Object playTimes) {
        this.playTimes = playTimes;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public void setActiveDays(List<String> activeDays) {
        this.activeDays = activeDays;
    }

    public void setStatus(String status) {
        this.status = status;
    }


    @Override
    public String toString() {
        return "Ad{" +
                "id=" + id +
                ", campaignName='" + campaignName + '\'' +
                ", scheduleType='" + scheduleType + '\'' +
                ", status='" + status + '\'' +
                '}';
    }
}
