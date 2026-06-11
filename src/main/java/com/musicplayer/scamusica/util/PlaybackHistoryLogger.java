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

    static {
        try {

            File dir = new File(BASE_DIR);

            if (!dir.exists()) {
                dir.mkdirs();
            }

            File file = new File(LOG_FILE);

            if (!file.exists()) {
                file.createNewFile();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static synchronized void logSong(PlaylistTrack track) {

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

        } catch (Exception e) {

            AppLogger.log("[HISTORY ERROR] " + e.getMessage());

            e.printStackTrace();
        }
    }
}