package com.musicplayer.scamusica.service;

import com.musicplayer.scamusica.manager.DeviceFingerprint;
import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.util.ApiClient;
import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.Utility;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Batches playback log entries (songs & ads) and periodically syncs them
 * to the Node.js backend via POST /api/logs/sync.
 *
 * Design:
 *  - addLog() is called from the existing PlaybackHistoryLogger / PlayerController.
 *  - A single daemon thread wakes every FLUSH_INTERVAL_SEC or when the queue
 *    reaches BATCH_SIZE, whichever comes first.
 *  - On failure (offline / HTTP error), logs stay in the queue and are retried
 *    on the next cycle. Queue is capped at MAX_QUEUE_SIZE to prevent OOM.
 *  - This service does NOT touch the existing local .log file behaviour.
 */
public class LogSyncService {

    // ── Singleton ──────────────────────────────────────────────────────
    private static LogSyncService instance;

    public static synchronized LogSyncService getInstance() {
        if (instance == null) {
            instance = new LogSyncService();
        }
        return instance;
    }

    // ── Constants ──────────────────────────────────────────────────────
    private static final int BATCH_SIZE         = 10;
    private static final int MAX_QUEUE_SIZE     = 500;
    private static final int FLUSH_INTERVAL_SEC = 300;  // 5 minutes

    // ── Internal state ─────────────────────────────────────────────────
    private final ConcurrentLinkedQueue<Map<String, String>> queue = new ConcurrentLinkedQueue<>();
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    private String cachedDeviceId;

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneOffset.UTC);

    private LogSyncService() {}

    // ── Lifecycle ──────────────────────────────────────────────────────

    public void start() {
        if (running) return;
        running = true;

        // Cache device ID once (expensive to compute every time)
        try {
            cachedDeviceId = DeviceFingerprint.getFingerprint();
        } catch (Exception e) {
            cachedDeviceId = "UNKNOWN";
            AppLogger.log("[LOG_SYNC] Failed to get device fingerprint: " + e.getMessage());
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LogSync-Thread");
            t.setDaemon(true);
            return t;
        });

        // Periodic flush
        scheduler.scheduleAtFixedRate(this::flush,
                FLUSH_INTERVAL_SEC, FLUSH_INTERVAL_SEC, TimeUnit.SECONDS);

        AppLogger.log("[LOG_SYNC] Service started (batch=" + BATCH_SIZE
                + ", interval=" + FLUSH_INTERVAL_SEC + "s)");
    }

    public void stop() {
        if (!running) return;
        running = false;

        // Best-effort final flush before shutdown
        try {
            flush();
        } catch (Exception ignored) {}

        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
        AppLogger.log("[LOG_SYNC] Service stopped. Remaining in queue: " + queue.size());
    }

    // ── Public API ─────────────────────────────────────────────────────

    /**
     * Add a SONG log entry.
     */
    public void addSongLog(Integer songId, String title, String playlist, String url) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("type", "SONG");
        entry.put("songId", songId != null ? songId.toString() : "");
        entry.put("title", title != null ? title : "");
        entry.put("playlist", playlist != null ? playlist : "");
        entry.put("url", url != null ? url : "");
        entry.put("timestamp", ISO_FORMATTER.format(Instant.now()));
        enqueue(entry);
    }

    /**
     * Add an AD log entry.
     */
    public void addAdLog(Integer adId, String campaignName, String adTitle, String adFileName) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("type", "AD");
        entry.put("adId", adId != null ? adId.toString() : "");
        
        String titleToStore = (adTitle != null && !adTitle.trim().isEmpty()) ? adTitle : (adFileName != null ? adFileName : "");
        entry.put("title", titleToStore);
        entry.put("playlist", campaignName != null ? campaignName : "");
        
        entry.put("timestamp", ISO_FORMATTER.format(Instant.now()));
        enqueue(entry);
    }

    /**
     * Add an ERROR log entry.
     */
    public void addErrorLog(String errorMessage, String source) {
        Map<String, String> entry = new LinkedHashMap<>();
        entry.put("type", "ERROR");
        entry.put("message", errorMessage != null ? errorMessage : "Unknown error");
        entry.put("source", source != null ? source : "Unknown");
        entry.put("timestamp", ISO_FORMATTER.format(Instant.now()));
        enqueue(entry);
    }

    private void enqueue(Map<String, String> entry) {
        if (queue.size() >= MAX_QUEUE_SIZE) {
            // Drop oldest to prevent OOM
            queue.poll();
            AppLogger.log("[LOG_SYNC] Queue full, dropped oldest entry");
        }
        queue.add(entry);

        // Trigger immediate flush if batch size reached
        if (queue.size() >= BATCH_SIZE && scheduler != null) {
            scheduler.submit(this::flush);
        }
    }

    private synchronized void flush() {
        if (queue.isEmpty()) return;

        // Check prerequisites
        if (!NetworkMonitor.getInstance().isOnline()) {
            AppLogger.log("[LOG_SYNC] Offline — skipping sync (" + queue.size() + " logs queued)");
            return;
        }

        String token = SessionManager.loadToken();
        if (token == null || token.isEmpty()) {
            AppLogger.log("[LOG_SYNC] No token — skipping sync");
            return;
        }

        // Drain up to BATCH_SIZE entries
        List<Map<String, String>> batch = new ArrayList<>();
        while (!queue.isEmpty() && batch.size() < BATCH_SIZE) {
            Map<String, String> entry = queue.poll();
            if (entry != null) batch.add(entry);
        }

        if (batch.isEmpty()) return;

        // Build JSON payload
        String osName = System.getProperty("os.name", "").toLowerCase();
        String deviceType = osName.contains("win") ? "Windows"
                : osName.contains("mac") ? "Mac" : "Raspberry";

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"playerId\":\"").append(escapeJson(cachedDeviceId)).append("\",");
        json.append("\"deviceType\":\"").append(escapeJson(deviceType)).append("\",");
        json.append("\"logs\":[");

        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) json.append(",");
            Map<String, String> entry = batch.get(i);
            json.append("{");
            int j = 0;
            for (Map.Entry<String, String> kv : entry.entrySet()) {
                if (j > 0) json.append(",");
                json.append("\"").append(escapeJson(kv.getKey())).append("\":");
                json.append("\"").append(escapeJson(kv.getValue())).append("\"");
                j++;
            }
            json.append("}");
        }

        json.append("]}");

        // Send to server
        try {
            String url = Utility.BASE_URL.get() + Utility.LOG_SYNC_ENDPOINT.get();
            Map<String, String> headers = new HashMap<>();
            headers.put("Authorization", "Bearer " + token);
            headers.put("Accept", "application/json");

            String response = ApiClient.post(url, json.toString(), headers);
            AppLogger.log("[LOG_SYNC] Synced " + batch.size() + " logs. Response: " + response);

        } catch (Exception e) {
            AppLogger.log("[LOG_SYNC] Sync failed: " + e.getMessage()
                    + " — re-queuing " + batch.size() + " logs");
            // Re-queue failed batch (add back to front)
            for (int i = batch.size() - 1; i >= 0; i--) {
                queue.add(batch.get(i));
            }
        }
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
