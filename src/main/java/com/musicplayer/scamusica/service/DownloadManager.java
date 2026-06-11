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

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final BlockingQueue<Integer> downloadQueue = new LinkedBlockingQueue<>();
    private volatile boolean cancelled = false;
    private final Set<Integer> activeDownloads = ConcurrentHashMap.newKeySet();

    private final DownloadListener listener;
    private final String downloadFolderPath;

    public DownloadManager(String downloadFolderPath,
                           DownloadListener listener) {
        this.listener = listener;
        this.downloadFolderPath = downloadFolderPath;
    }

    public void start() {
        cancelled = false;
        executor.submit(this::runWorker);
    }

    public void stop() {
        cancelled = true;
        executor.shutdownNow();
    }

    public void queueDownload(int songId) {
        if (!cancelled) {
            if (activeDownloads.add(songId)) {
                AppLogger.log("[DOWNLOAD] Queued: " + songId);
                downloadQueue.offer(songId);
            }
        }
    }

//    private void runWorker() {
//        while (!cancelled) {
//            try {
//                Integer id = downloadQueue.poll(2, TimeUnit.SECONDS);
//                if (id == null) continue;
//
//                processDownload(id);
//
//            } catch (Exception e) {
//                e.printStackTrace();
//            }
//        }
//    }

    // ✅ REPLACE WITH: For Windows
    private void runWorker() {
        // ✅ Download thread ki priority kam karo taaki playback affect na ho
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

            if (outFile.exists() && outFile.length() > 0) {
                AppLogger.log("[DOWNLOAD][SKIP] Already exists: " + id);
                if (listener != null) listener.onDownloadSkipped(id, outFile);
                activeDownloads.remove(id); // 🔥 IMPORTANT
                return;
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

            if (success) {
                AppLogger.log("[DOWNLOAD][DONE] " + id);
                if (listener != null) listener.onDownloadCompleted(id, outFile);
            } else {
                // Incomplete file delete karo
                if (outFile.exists()) outFile.delete();
                AppLogger.log("[DOWNLOAD][FAIL] Incomplete file deleted for id=" + id);
                if (listener != null) listener.onDownloadFailed(id, new RuntimeException("Incomplete download, file too small"));
            }

            activeDownloads.remove(id);

        } catch (Exception ex) {
            if (listener != null) listener.onDownloadFailed(id, ex);
        }
    }
}
