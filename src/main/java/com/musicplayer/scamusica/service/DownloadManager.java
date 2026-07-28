package com.musicplayer.scamusica.service;


import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.util.ApiClient;
import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.Utility;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class DownloadManager {

    public interface DownloadListener {
        void onDownloadStarted(int songId, File outputFile);

        void onDownloadProgress(int songId, long bytesDownloaded, long contentLength);

        void onDownloadCompleted(int songId, File outputFile);

        void onDownloadSkipped(int songId, File existingFile);

        void onDownloadFailed(int songId, Exception ex);

        void onAllDownloadsFinished();

        void onCancelled();
    }

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DownloadManager-Thread");
        t.setDaemon(true);
        return t;
    });
    private final BlockingQueue<Integer> downloadQueue = new LinkedBlockingQueue<>();
    private volatile boolean cancelled = false;
    private final Set<Integer> activeDownloads = ConcurrentHashMap.newKeySet();
    private static final int MAX_RETRIES = 3;
    private static final long MIN_VALID_FILE_SIZE = 10_000; // 10KB
    private final Map<Integer, Integer> retryCounts = new ConcurrentHashMap<>();
    private final Map<Integer, String> fallbackUrlMap = new ConcurrentHashMap<>();

    private final DownloadListener listener;
    private final String downloadFolderPath;

    public DownloadManager(String downloadFolderPath,
                           DownloadListener listener) {
        this.listener = listener;
        this.downloadFolderPath = downloadFolderPath;
    }

    public void registerFallbackUrl(int songId, String directUrl) {
        if (directUrl != null && !directUrl.trim().isEmpty()) {
            fallbackUrlMap.put(songId, directUrl);
        }
    }

    public void setFallbackUrls(Map<Integer, String> urlMap) {
        if (urlMap != null) {
            fallbackUrlMap.putAll(urlMap);
        }
    }

    public void start() {
        cancelled = false;
        executor.submit(this::runWorker);
    }

    public void stop() {
        cancelled = true;
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void queueDownload(int songId) {
        if (!cancelled) {
            if (activeDownloads.add(songId)) {
                AppLogger.log("[DOWNLOAD] Queued: " + songId);
                downloadQueue.offer(songId);
            }
        }
    }

    private void runWorker() {
        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
        while (!cancelled) {
            try {
                Integer id = downloadQueue.poll(2, TimeUnit.SECONDS);
                if (id == null) continue;
                processDownload(id);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void processDownload(Integer id) {
        AppLogger.log("[DOWNLOAD] Starting: " + id);
        try {
            File baseDir = new File(downloadFolderPath);
            if (!baseDir.exists()) baseDir.mkdirs();

            File outFile = new File(baseDir, "song-" + id + ".dat");

            if (outFile.exists() && outFile.length() > MIN_VALID_FILE_SIZE) {
                AppLogger.log("[DOWNLOAD][SKIP] Already exists: " + id);
                if (listener != null) listener.onDownloadSkipped(id, outFile);
                activeDownloads.remove(id); // 🔥 IMPORTANT
                retryCounts.remove(id);
                return;
            }

            if (outFile.exists() && outFile.length() <= MIN_VALID_FILE_SIZE) {
                AppLogger.log("[DOWNLOAD] Deleting corrupted/truncated file: " + id + " (" + outFile.length() + " bytes)");
                outFile.delete();
            }

            String streamUrl = Utility.BASE_URL.get() + "/api/music/songs/" + id + "/stream";

            if (listener != null) listener.onDownloadStarted(id, outFile);

            Map<String, String> headers = new HashMap<>();
            String token = SessionManager.loadToken();
            if (token != null && !token.trim().isEmpty()) {
                headers.put("Authorization", "Bearer " + token);
            }

            ApiClient.ProgressCallback progressCallback = (bytesRead, contentLength) -> {
                if (listener != null) {
                    listener.onDownloadProgress(id, bytesRead, contentLength);
                }
            };

            boolean success = ApiClient.downloadEncrypted(streamUrl, headers, outFile, progressCallback);

            if (!success || !outFile.exists() || outFile.length() <= MIN_VALID_FILE_SIZE) {
                String fallbackUrl = fallbackUrlMap.get(id);
                if (fallbackUrl != null && !fallbackUrl.trim().isEmpty()) {
                    AppLogger.log("[DOWNLOAD] Stream download failed for id=" + id + ", attempting direct fallback URL: " + fallbackUrl);
                    if (outFile.exists()) outFile.delete();
                    success = ApiClient.downloadEncrypted(fallbackUrl, null, outFile, progressCallback);
                }
            }

            if (success && outFile.exists() && outFile.length() > MIN_VALID_FILE_SIZE) {
                AppLogger.log("[DOWNLOAD][DONE] " + id);
                if (listener != null) listener.onDownloadCompleted(id, outFile);
                retryCounts.remove(id);
            } else {
                if (outFile.exists()) outFile.delete();
                int attempts = retryCounts.getOrDefault(id, 0) + 1;
                retryCounts.put(id, attempts);

                if (attempts < MAX_RETRIES && !cancelled) {
                    long backoffMs = attempts * 5000L;
                    AppLogger.log("[DOWNLOAD][RETRY] id=" + id + " attempt " + attempts + "/" + MAX_RETRIES + ", backoff " + backoffMs + "ms");
                    try { Thread.sleep(backoffMs); } catch (InterruptedException ignored) {}
                    if (!cancelled) {
                        downloadQueue.offer(id);
                    }
                } else {
                    AppLogger.log("[DOWNLOAD][FAIL] id=" + id + " after " + attempts + " attempts");
                    if (listener != null) listener.onDownloadFailed(id, new RuntimeException("Incomplete download, file too small"));
                    retryCounts.remove(id);
                    activeDownloads.remove(id);
                }
            }

            if (!retryCounts.containsKey(id)) {
                activeDownloads.remove(id);
            }

        } catch (Exception ex) {
            int attempts = retryCounts.getOrDefault(id, 0) + 1;
            retryCounts.put(id, attempts);

            if (attempts < MAX_RETRIES && !cancelled) {
                long backoffMs = attempts * 5000L;
                AppLogger.log("[DOWNLOAD][RETRY] id=" + id + " after exception, attempt " + attempts + "/" + MAX_RETRIES);
                try { Thread.sleep(backoffMs); } catch (InterruptedException ignored) {}
                if (!cancelled) {
                    downloadQueue.offer(id);
                }
            } else {
                if (listener != null) listener.onDownloadFailed(id, ex);
                retryCounts.remove(id);
                activeDownloads.remove(id);
            }
        }
    }
}
