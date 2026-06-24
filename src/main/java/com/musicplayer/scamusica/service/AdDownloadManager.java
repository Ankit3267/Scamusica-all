package com.musicplayer.scamusica.service;

import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.model.Ad;
import com.musicplayer.scamusica.util.ApiClient;
import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.Utility;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AdDownloadManager {

    private static final String AD_DIR_NAME = ".scamusica" + File.separator + "ads";

    private static final ExecutorService executor = Executors.newFixedThreadPool(3, r -> {
        Thread t = new Thread(r, "AdDownloadManager-Thread");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    public static File getAdDir() {
        File dir = new File(System.getProperty("user.home") + File.separator + AD_DIR_NAME);
        if (!dir.exists())
            dir.mkdirs();
        return dir;
    }

    public static File getLocalAdFile(com.musicplayer.scamusica.model.AdAudio adAudio) {
        if (adAudio == null || adAudio.getId() == null)
            return null;
        return new File(getAdDir(), "ad-audio-" + adAudio.getId() + ".mp3");
    }

    public static boolean isAdDownloaded(Ad ad) {
        if (ad == null || ad.getAdAudios() == null || ad.getAdAudios().isEmpty())
            return false;
        for (com.musicplayer.scamusica.model.AdAudio adAudio : ad.getAdAudios()) {
            File f = getLocalAdFile(adAudio);
            if (f == null || !f.exists() || f.length() <= 1024) {
                return false;
            }
        }
        return true;
    }

    public static void downloadAd(Ad ad) {
        if (ad == null || ad.getId() == null || ad.getAdAudios() == null)
            return;
        if (isAdDownloaded(ad)) {
            AppLogger.log("[AdDownload] Already exists all audios for ad-" + ad.getId());
            return;
        }

        for (com.musicplayer.scamusica.model.AdAudio adAudio : ad.getAdAudios()) {
            downloadAdAudio(adAudio);
        }
    }

    private static void downloadAdAudio(com.musicplayer.scamusica.model.AdAudio adAudio) {
        if (adAudio == null || adAudio.getId() == null)
            return;
        File f = getLocalAdFile(adAudio);
        if (f != null && f.exists() && f.length() > 1024)
            return;

        String audioFile = adAudio.getAudioFile();
        if (audioFile == null || audioFile.isEmpty())
            return;

        String downloadUrl;
        if (audioFile.startsWith("http://") || audioFile.startsWith("https://")) {
            downloadUrl = audioFile;
        } else {
            String encoded = audioFile
                    .replace(" ", "%20")
                    .replace("(", "%28")
                    .replace(")", "%29");
            if (!encoded.startsWith("/"))
                encoded = "/" + encoded;
            downloadUrl = Utility.BASE_URL.get() + encoded;
        }

        final String finalUrl = downloadUrl;

        executor.submit(() -> {
            try {
                File outFile = getLocalAdFile(adAudio);
                AppLogger.log("[AdDownload] Downloading ad-audio-" + adAudio.getId() + " from: " + finalUrl);

                HttpURLConnection conn = (HttpURLConnection) new URL(finalUrl).openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(30000);

                String token = SessionManager.loadToken();
                if (token != null && !token.isEmpty()) {
                    conn.setRequestProperty("Authorization", "Bearer " + token);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode == 200) {
                    try (InputStream is = conn.getInputStream();
                            FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buffer = new byte[8192];
                        int read;
                        while ((read = is.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                    }
                    AppLogger.log("[AdDownload] Done: ad-audio-" + adAudio.getId() + " size=" + outFile.length());
                } else {
                    AppLogger.log("[AdDownload] Failed HTTP " + responseCode + " for ad-audio-" + adAudio.getId());
                }
                conn.disconnect();

            } catch (Exception e) {
                AppLogger.log("[AdDownload] Error downloading ad-audio-" + adAudio.getId() + ": " + e.getMessage());
            }
        });
    }

    public static void downloadAllAds(List<Ad> ads) {
        if (ads == null || ads.isEmpty())
            return;
        for (Ad ad : ads) {
            downloadAd(ad);
        }
    }
}