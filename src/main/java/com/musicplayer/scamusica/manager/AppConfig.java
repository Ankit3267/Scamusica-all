package com.musicplayer.scamusica.manager;

import java.io.InputStream;
import java.util.Properties;

public class AppConfig {

    private static final Properties properties = new Properties();

    static {
        try (InputStream input = AppConfig.class.getClassLoader()
                .getResourceAsStream("app.properties")) {
            if (input != null) {
                properties.load(input);
            } else {
                System.out.println("app.properties not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String get(String key) {
        return properties.getProperty(key);
    }

    /*public static void set(String key, String value) {
        properties.setProperty(key, value);
    }*/
}
