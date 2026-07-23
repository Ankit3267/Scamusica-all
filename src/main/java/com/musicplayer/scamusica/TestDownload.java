package com.musicplayer.scamusica;

import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.util.ApiClient;
import com.musicplayer.scamusica.util.Utility;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class TestDownload {
    public static void main(String[] args) {
        try {
            int id = 1799;
            System.out.println("Testing download for song " + id);
            
            String streamUrl = Utility.BASE_URL.get() + "/api/songs/" + id + "/stream";
            Map<String, String> headers = new HashMap<>();
            String token = SessionManager.loadToken();
            if (token != null && !token.trim().isEmpty()) {
                headers.put("Authorization", "Bearer " + token);
            } else {
                System.out.println("NO TOKEN FOUND!");
            }
            
            File outFile = new File("test_song_" + id + ".dat");
            System.out.println("Downloading to: " + outFile.getAbsolutePath());
            
            boolean success = ApiClient.downloadEncrypted(streamUrl, headers, outFile, null);
            System.out.println("Success: " + success);
            if (outFile.exists()) {
                System.out.println("File size: " + outFile.length());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
