package com.musicplayer.scamusica.service;

import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.ImageCache;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MemoryWatchdog {
    private static MemoryWatchdog instance;
    private ScheduledExecutorService scheduler;
    private java.util.concurrent.CopyOnWriteArrayList<Runnable> cleanupCallbacks = new java.util.concurrent.CopyOnWriteArrayList<>();

    private MemoryWatchdog() {}

    public static synchronized MemoryWatchdog getInstance() {
        if (instance == null) {
            instance = new MemoryWatchdog();
        }
        return instance;
    }

    public void start() {
        if (scheduler != null && !scheduler.isShutdown()) {
            return;
        }

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "MemoryWatchdog-Thread");
            t.setDaemon(true);
            return t;
        });

        AppLogger.log("[Watchdog] Started");

        scheduler.scheduleAtFixedRate(() -> {
            try {
                Runtime runtime = Runtime.getRuntime();
                long totalMem = runtime.totalMemory();
                long freeMem = runtime.freeMemory();
                long usedMem = totalMem - freeMem;
                long maxMem = runtime.maxMemory();

                double usedPercentage = (double) usedMem / maxMem * 100.0;
                AppLogger.log(String.format("[Watchdog] Heap usage: %.2f%% (%d MB / %d MB)", 
                    usedPercentage, usedMem / 1024 / 1024, maxMem / 1024 / 1024));

                if (usedPercentage > 95.0) {
                    AppLogger.log("[Watchdog] FATAL MEMORY LEVEL (>95%). OOM imminent!");
                    ImageCache.clearMemoryCache();
                    for (Runnable callback : cleanupCallbacks) {
                        try {
                            callback.run();
                        } catch (Exception e) {}
                    }
                    System.gc();
                } else if (usedPercentage > 92.0) {
                    AppLogger.log("[Watchdog] CRITICAL MEMORY LEVEL. Forcing GC and clearing caches.");
                    ImageCache.clearMemoryCache();
                    for (Runnable callback : cleanupCallbacks) {
                        try {
                            callback.run();
                        } catch (Exception e) {}
                    }
                    System.gc();
                } else if (usedPercentage > 85.0) {
                    AppLogger.log("[Watchdog] HIGH MEMORY LEVEL. Clearing caches.");
                    ImageCache.clearMemoryCache();
                } else if (usedPercentage > 75.0) {
                    System.gc();
                }
            } catch (Exception e) {
                AppLogger.log("[Watchdog] Error: " + e.getMessage());
            }
        }, 10, 10, TimeUnit.MINUTES);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
    
    public void registerCleanupCallback(Runnable callback) {
        cleanupCallbacks.add(callback);
    }
}
