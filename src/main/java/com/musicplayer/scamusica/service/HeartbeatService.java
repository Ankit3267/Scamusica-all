package com.musicplayer.scamusica.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicplayer.scamusica.manager.DeviceFingerprint;
import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.Utility;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyStore;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartbeatService {

    private static HeartbeatService instance;
    private ScheduledExecutorService scheduler;
    private HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    private HeartbeatService() {
        initHttpClient();
    }

    public static HeartbeatService getInstance() {
        if (instance == null) {
            instance = new HeartbeatService();
        }
        return instance;
    }

    private void initHttpClient() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            KeyStore ks = KeyStore.getInstance("JKS");
            File cacerts = new File(System.getProperty("java.home") + "/lib/security/cacerts");
            try (FileInputStream fis = new FileInputStream(cacerts)) {
                ks.load(fis, "changeit".toCharArray());
            }
            tmf.init(ks);
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, tmf.getTrustManagers(), null);
            client = HttpClient.newBuilder().sslContext(sslContext).build();
        } catch (Exception e) {
            AppLogger.log("[HeartbeatService] Failed to initialize SSL HttpClient, falling back to default.");
            client = HttpClient.newHttpClient();
        }
    }

    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        AppLogger.log("[HeartbeatService] Starting heartbeat service...");
        scheduler = Executors.newSingleThreadScheduledExecutor();

        // Send immediately, then every 30 minutes
        scheduler.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeat();
            } catch (Exception e) {
                AppLogger.log("[HeartbeatService] Error in heartbeat task: " + e.getMessage());
            }
        }, 0, 30, TimeUnit.SECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            AppLogger.log("[HeartbeatService] Stopping heartbeat service...");
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    private void sendHeartbeat() {
        if (!NetworkMonitor.getInstance().isOnline()) {
            AppLogger.log("[HeartbeatService] Skipping heartbeat - No internet connection");
            return;
        }

        String token = SessionManager.loadToken();
        if (token == null || token.isEmpty()) {
            AppLogger.log("[HeartbeatService] Skipping heartbeat - No valid session token");
            return;
        }

        String deviceId = DeviceFingerprint.getFingerprint();
        String osName = System.getProperty("os.name").toLowerCase();
        String deviceType = osName.contains("win") ? "Windows" : (osName.contains("mac") ? "Mac" : "Raspberry");

        try {
            String requestBody = "{"
                    + "\"deviceId\": \"" + deviceId + "\","
                    + "\"deviceType\": \"" + deviceType + "\""
                    + "}";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(Utility.BASE_URL.get() + Utility.PLAYER_HEARTBEAT.get()))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            AppLogger.log("[HeartbeatService] Heartbeat response code: " + response.statusCode() + " body: "
                    + response.body());
        } catch (Exception e) {
            AppLogger.log("[HeartbeatService] Exception sending heartbeat: " + e.getMessage());
        }
    }
}
