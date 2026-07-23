package com.musicplayer.scamusica.util;

import com.musicplayer.scamusica.model.PlaylistTrack;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PlaybackHistoryLogger {

    private static final String BASE_DIR =
            System.getProperty("user.home")
                    + File.separator
                    + ".scamusica";

    private static final String LOG_FILE =
            BASE_DIR + File.separator + "playback-history.log";

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd-MM-yyyy hh:mm:ss a");

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB

    static {
        try {
            File dir = new File(BASE_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void checkAndRotate() {
        File file = new File(LOG_FILE);
        if (file.exists() && file.length() > MAX_FILE_SIZE) {
            File backup = new File(LOG_FILE + ".old");
            if (backup.exists()) {
                backup.delete();
            }
            file.renameTo(backup);
        }
    }

    public static synchronized void logSong(PlaylistTrack track) {
        checkAndRotate();
        try (BufferedWriter writer =
                     new BufferedWriter(new FileWriter(LOG_FILE, true))) {

            String time = LocalDateTime.now().format(FORMATTER);

            String log =
                    "\n==================================================\n" +
                            "TIME       : " + time + "\n" +
                            "SONG ID    : " + track.getId() + "\n" +
                            "TITLE      : " + track.getTitle() + "\n" +
                            "PLAYLIST   : " + track.getFolderTitle() + "\n" +
                            "URL        : " + track.getUrl() + "\n" +
                            "==================================================\n";

            writer.write(log);

            AppLogger.log("[HISTORY] Logged -> " + track.getTitle());

            // Sync to server (additive — does not affect local logging)
            try {
                com.musicplayer.scamusica.service.LogSyncService.getInstance()
                        .addSongLog(track.getId(), track.getTitle(),
                                track.getFolderTitle(), track.getUrl());
            } catch (Exception syncEx) {
                AppLogger.log("[HISTORY] Server sync queue failed: " + syncEx.getMessage());
            }

        } catch (Exception e) {
            AppLogger.log("[HISTORY ERROR] " + e.getMessage());
            e.printStackTrace();
        }
    }
}