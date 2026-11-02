package com.musicplayer.scamusica.model;

public class CrossfadeSettings {
    public static final int DEFAULT_DURATION = 5; // Default 5 seconds if not set

    private Boolean crossfade_enabled;
    private Integer crossfade_duration;

    public boolean isCrossfadeEnabled() {
        return crossfade_enabled != null && crossfade_enabled;
    }

    public void setCrossfadeEnabled(Boolean crossfade_enabled) {
        this.crossfade_enabled = crossfade_enabled;
    }

    public int getCrossfadeDuration() {
        return (crossfade_duration != null && crossfade_duration > 0)
                ? crossfade_duration
                : DEFAULT_DURATION;
    }

    public void setCrossfadeDuration(Integer crossfade_duration) {
        this.crossfade_duration = crossfade_duration;
    }

    @Override
    public String toString() {
        return "CrossfadeSettings{" +
                "crossfade_enabled=" + isCrossfadeEnabled() +
                ", crossfade_duration=" + getCrossfadeDuration() +
                '}';
    }
}
