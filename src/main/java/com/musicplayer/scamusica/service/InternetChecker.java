package com.musicplayer.scamusica.service;

import java.net.HttpURLConnection;
import java.net.URL;

public final class InternetChecker {

    private InternetChecker() {}

    public static boolean hasInternet() {
        try {
            HttpURLConnection con =
                    (HttpURLConnection) new URL("https://www.google.com/generate_204").openConnection();
            con.setConnectTimeout(3000);
            con.setReadTimeout(3000);
            con.setRequestMethod("GET");
            con.connect();
            return con.getResponseCode() == 204 || con.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }
}
