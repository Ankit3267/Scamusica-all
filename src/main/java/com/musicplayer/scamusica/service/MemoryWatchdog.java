package com.musicplayer.scamusica.service;

import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.ImageCache;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class MemoryWatchdog {
    private static MemoryWatchdog instance;
    private ScheduledExecutorService scheduler;
    private java.util.concurrent.CopyOnWriteArrayList<Runnable> cleanupCallbacks = new java.util.concurrent.CopyOnWriteArrayList<>();
    private java.util.concurrent.CopyOnWriteArrayList<Runnable> preRestartCallbacks = new java.util.concurrent.CopyOnWriteArrayList<>();
    
    private volatile boolean restartTriggered = false;

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

        scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "MemoryWatchdog-Thread");
            t.setDaemon(true);
            return t;
        });

        AppLogger.log("[Watchdog] Started");

        // Schedule regular memory checks every 30 minutes
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

                // Always clean temp files
                cleanTempFiles();

                if (usedPercentage > 95.0) {
                    AppLogger.log("[Watchdog] FATAL MEMORY LEVEL (>95%). OOM imminent!");
                    ImageCache.clearMemoryCache();
                    for (Runnable callback : cleanupCallbacks) {
                        try {
                            callback.run();
                        } catch (Exception e) {}
                    }
                    System.gc();
                    System.runFinalization();
                } else if (usedPercentage > 92.0) {
                    AppLogger.log("[Watchdog] CRITICAL MEMORY LEVEL. Forcing GC and clearing caches.");
                    ImageCache.clearMemoryCache();
                    for (Runnable callback : cleanupCallbacks) {
                        try {
                            callback.run();
                        } catch (Exception e) {}
                    }
                    System.gc();
                    System.runFinalization();
                } else if (usedPercentage > 85.0) {
                    AppLogger.log("[Watchdog] HIGH MEMORY LEVEL. Clearing caches.");
                    ImageCache.clearMemoryCache();
                } else if (usedPercentage > 75.0) {
                    System.gc();
                }
            } catch (Exception e) {
                AppLogger.log("[Watchdog] Error: " + e.getMessage());
            }
        }, 15, 30, TimeUnit.MINUTES);
        
        // Schedule daily midnight restart check
        long delayToMidnight = computeDelayToNextMidnight();
        AppLogger.log("[Watchdog] Next midnight restart check in " + (delayToMidnight / 60000) + " minutes.");
        scheduler.scheduleAtFixedRate(
                this::checkMidnightRestart,
                delayToMidnight,
                TimeUnit.DAYS.toMillis(1),
                TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }
    
    public void registerCleanupCallback(Runnable callback) {
        cleanupCallbacks.add(callback);
    }
    
    public void registerPreRestartCallback(Runnable callback) {
        preRestartCallbacks.add(callback);
    }
    
    private void checkMidnightRestart() {
        try {
            AppLogger.log("[Watchdog] 🕛 Midnight restart check triggered.");
            Runtime runtime = Runtime.getRuntime();
            long maxMem = runtime.maxMemory();
            long usedMem = runtime.totalMemory() - runtime.freeMemory();
            
            // If heap is > 90% full at midnight, restart
            double usedPercentage = (double) usedMem / maxMem * 100.0;
            AppLogger.log(String.format("[Watchdog] Midnight check: %.2f%% heap used.", usedPercentage));

            if (usedPercentage > 90.0) {
                AppLogger.log("[Watchdog] ⚠️ RAM exceeds restart threshold at midnight. Triggering restart...");
                triggerSelfRestart();
            } else {
                AppLogger.log("[Watchdog] ✅ Midnight check OK. No restart needed.");
                restartTriggered = false;
            }
        } catch (Exception e) {
            AppLogger.log("[Watchdog] Error in midnight restart check: " + e.getMessage());
        }
    }

    private long computeDelayToNextMidnight() {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return java.time.temporal.ChronoUnit.MILLIS.between(now, nextMidnight);
    }

    private void triggerSelfRestart() {
        if (restartTriggered) return;
        restartTriggered = true;

        AppLogger.log("[Watchdog] 🔄 Triggering auto-restart...");
        for (Runnable callback : preRestartCallbacks) {
            try { callback.run(); } catch (Exception e) {}
        }

        try {
            String[] possiblePaths = {
                    System.getProperty("user.home") + java.io.File.separator + "scamusica" + java.io.File.separator + "restart_scamusica.bat",
                    System.getProperty("user.home") + java.io.File.separator + "scamusica" + java.io.File.separator + "restart_scamusica.sh",
                    System.getProperty("user.dir") + java.io.File.separator + "scripts" + java.io.File.separator + "restart_scamusica.bat",
                    System.getProperty("user.dir") + java.io.File.separator + "scripts" + java.io.File.separator + "restart_scamusica.sh",
                    System.getProperty("user.dir") + java.io.File.separator + "restart_scamusica.bat",
                    System.getProperty("user.dir") + java.io.File.separator + "restart_scamusica.sh",
                    "C:\\scamusica\\restart_scamusica.bat",
                    "/opt/scamusica/bin/restart_scamusica.sh"
            };

            java.io.File scriptFile = null;
            for (String path : possiblePaths) {
                java.io.File f = new java.io.File(path);
                if (f.exists() && f.canExecute()) {
                    scriptFile = f;
                    break;
                }
            }

            if (scriptFile != null) {
                AppLogger.log("[Watchdog] ✅ Launching restart script: " + scriptFile.getAbsolutePath());
                ProcessBuilder pb = new ProcessBuilder(scriptFile.getAbsolutePath());
                pb.start();
            } else {
                AppLogger.log("[Watchdog] ⚠️ Restart script not found. Relying on system service auto-restart if configured.");
            }
        } catch (Exception e) {
            AppLogger.log("[Watchdog] Failed to launch restart script: " + e.getMessage());
        }

        new Thread(() -> {
            try { Thread.sleep(3000); } catch (Exception ignored) {}
            AppLogger.log("[Watchdog] Exiting JVM now for restart.");
            AppLogger.close();
            System.exit(0);
        }, "Watchdog-Restart-Thread").start();
    }
    
    private void cleanTempFiles() {
        try {
            java.io.File tempDir = new java.io.File(System.getProperty("user.home")
                    + java.io.File.separator + ".scamusica"
                    + java.io.File.separator + "temp");

            if (tempDir.exists() && tempDir.isDirectory()) {
                java.io.File[] files = tempDir.listFiles();
                int deletedCount = 0;
                long freedBytes = 0;
                if (files != null) {
                    for (java.io.File f : files) {
                        if (f.getName().startsWith("play_") && f.getName().endsWith(".mp3")) {
                            freedBytes += f.length();
                            if (f.delete())
                                deletedCount++;
                        }
                    }
                }
                if (deletedCount > 0) {
                    AppLogger.log("[Watchdog] Cleaned " + deletedCount
                            + " temp files, freed ~" + (freedBytes / (1024 * 1024)) + " MB");
                }
            }
        } catch (Exception e) {
            AppLogger.log("[Watchdog] Failed to clean temp files: " + e.getMessage());
        }
    }
}
