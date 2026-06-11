package com.musicplayer.scamusica.util;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;

public class AppLogger {

    private static PrintWriter writer;

    public static void init() {
        try {
            String baseDir = System.getProperty("user.home")
                    + File.separator + ".scamusica"
                    + File.separator + "logs";

            File dir = new File(baseDir);
            if (!dir.exists()) dir.mkdirs();

            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            File logFile = new File(dir, "player_" + timestamp + ".log");

            writer = new PrintWriter(new FileWriter(logFile, true), true);

            log("[LOGGER] Initialized. File: " + logFile.getAbsolutePath());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void log(String message) {
        String time = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String finalMsg = "[" + time + "] " + message;

        System.out.println(finalMsg);

        if (writer != null) {
            writer.println(finalMsg);
        }
    }

    public static void close() {
        if (writer != null) {
            log("[LOGGER] Closing logger");
            writer.close();
        }
    }
}