package com.musicplayer.scamusica.util;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class AppLogger {

    private static PrintWriter writer;
    private static File currentLogFile;
    private static long bytesWritten = 0;
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    public static void init() {
        try {
            String baseDir = System.getProperty("user.home")
                    + File.separator + ".scamusica"
                    + File.separator + "logs";

            File dir = new File(baseDir);
            if (!dir.exists()) dir.mkdirs();

            cleanupOldLogs(dir);
            createNewLogFile(dir);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void cleanupOldLogs(File dir) {
        File[] logs = dir.listFiles((d, name) -> name.startsWith("player_") && name.endsWith(".log"));
        if (logs != null && logs.length > 3) {
            java.util.Arrays.sort(logs, java.util.Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < logs.length - 3; i++) {
                logs[i].delete();
            }
        }
    }

    private static void createNewLogFile(File dir) throws IOException {
        if (writer != null) {
            writer.close();
        }
        String timestamp = DATE_FORMAT.format(LocalDateTime.now());
        currentLogFile = new File(dir, "player_" + timestamp + ".log");
        writer = new PrintWriter(new FileWriter(currentLogFile, true), true);
        bytesWritten = currentLogFile.length();
        log("[LOGGER] Initialized. File: " + currentLogFile.getAbsolutePath());
    }

    public static synchronized void log(String message) {
        String time = TIME_FORMAT.format(LocalDateTime.now());
        String finalMsg = "[" + time + "] " + message;

        System.out.println(finalMsg);

        if (writer != null) {
            writer.println(finalMsg);
            bytesWritten += finalMsg.length() + System.lineSeparator().length();

            if (bytesWritten > MAX_FILE_SIZE) {
                try {
                    createNewLogFile(currentLogFile.getParentFile());
                    cleanupOldLogs(currentLogFile.getParentFile());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void close() {
        if (writer != null) {
            log("[LOGGER] Closing logger");
            writer.close();
        }
    }
}