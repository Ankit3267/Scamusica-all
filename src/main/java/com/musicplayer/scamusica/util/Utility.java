package com.musicplayer.scamusica.util;

public enum Utility {

    // BASE_URL("https://rtas5010.elb.cisinlive.com"),
    // BASE_URL("https://api.scamusica.com"),
    BASE_URL("https://apilive.scamusica.com"),
    MUSIC_PATH("/public/music/"),
    API_SONGS_ENDPOINT("/api/songs/player"),

    // FILEPATH_BASE_URL("https://api.scamusica.com"),
    FILEPATH_BASE_URL("https://dndzskblsl1f7.cloudfront.net"),

    VERIFY_LICENSE_CODE("/api/auth/verify-license-code"),
    PLAYER_HEARTBEAT("/api/player/heartbeat"),
    LOG_SYNC_ENDPOINT("/api/logs/sync"),
    SUPPORT_URL("https://scamusica.com/support");

    private final String value;

    Utility(String value) {
        this.value = value;
    }

    public String get() {
        return value;
    }

}
