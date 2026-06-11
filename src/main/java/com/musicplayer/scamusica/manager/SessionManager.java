package com.musicplayer.scamusica.manager;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.EncryptionUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class SessionManager {
    private static final String CONFIG_DIR = getConfigDir();
    private static final String CONFIG_FILE = CONFIG_DIR + File.separator + "session.properties";

    private static String getConfigDir() {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("mac")) {
            return System.getProperty("user.home") + "/Library/Application Support/Scamusica";
        } else if (os.contains("win")) {
            return System.getenv("LOCALAPPDATA") + "\\Scamusica";
        } else {
            return System.getProperty("user.home") + "/.scamusica";
        }
    }

    // Save token + language to file
    public static void saveToken(String token, Integer userID, String language) {
        try {
            File dir = new File(CONFIG_DIR);
            if (!dir.exists()) dir.mkdirs();

            Properties properties = new Properties();

            // encrypt before saving
            String encryptedToken = EncryptionUtil.encrypt(token);

            String encryptedUserId= EncryptionUtil.encrypt(userID.toString());

            properties.setProperty("token", encryptedToken);
            properties.setProperty("userId", encryptedUserId);
            properties.setProperty("language", language);

            try(FileOutputStream out = new FileOutputStream(CONFIG_FILE)){
                properties.store(out, "Scamusica Session");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Load token from file
//    public static String loadToken() {
//        try {
//            File file = new File(CONFIG_FILE);
//            if (!file.exists()) return null;
//
//            Properties properties = new Properties();
//            properties.load(new FileInputStream(file));
//
//            String encryptedToken = properties.getProperty("token");
//            if (encryptedToken == null) return null;
//
//            return EncryptionUtil.decrypt(encryptedToken);
//
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//
//    }

    public static String loadToken() {
        try {
            File file = new File(CONFIG_FILE);
            if (!file.exists()) {
                AppLogger.log("[SessionManager] session.properties not found at: " + CONFIG_FILE);
                return null;
            }

            Properties properties = new Properties();
            try (FileInputStream fis = new FileInputStream(file)) {
                properties.load(fis);
            }

            String encryptedToken = properties.getProperty("token");
            if (encryptedToken == null) {
                AppLogger.log("[SessionManager] 'token' key missing in properties file");
                return null;
            }

            String token = EncryptionUtil.decrypt(encryptedToken);
            AppLogger.log("[SessionManager] Token loaded, expired=" + isTokenExpired(token));
            return token;

        } catch (Exception e) {
            AppLogger.log("[SessionManager] loadToken FAILED: " + e.getMessage());
            return null;
        }
    }

    public static Integer getUserId() {
        try {
            File file = new File(CONFIG_FILE);
            if (!file.exists()) return null;

            Properties properties = new Properties();
            properties.load(new FileInputStream(file));

            String encryptedToken = properties.getProperty("userId");
            if (encryptedToken == null) return null;

            return Integer.valueOf(EncryptionUtil.decrypt(encryptedToken));

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    public static String getLanguage() {
        try {
            File file = new File(CONFIG_FILE);
            if (!file.exists()) return null;

            Properties properties = new Properties();
            properties.load(new FileInputStream(file));

            String language = properties.getProperty("language");
            if (language == null) return "en";

            return language;

        } catch (Exception e) {
            e.printStackTrace();
            return "en";
        }

    }

    // Clear token
    public static void clearToken() {
        File file = new File(CONFIG_FILE);
        if (file.exists()) file.delete();
    }
    public static void saveLanguage(String language) {
        try {
            // Ensure directory exists
            File dir = new File(CONFIG_DIR);
            if (!dir.exists()) dir.mkdirs();

            Properties properties = new Properties();

            File file = new File(CONFIG_FILE);

            // Load existing properties if file exists
            if (file.exists()) {
                FileInputStream in = new FileInputStream(file);
                properties.load(in);
                in.close();
            }

            // Update only the language key
            properties.setProperty("language", language);

            // Save back to file
            try( FileOutputStream out = new FileOutputStream(CONFIG_FILE)){
                properties.store(out, "Scamusica Session");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // Is token present & valid?
//    public static boolean isUserLoggedIn() {
//        String token = loadToken();
//        return token != null && !token.isEmpty() && !isTokenExpired(token);
//    }

    public static boolean isUserLoggedIn() {
        try {
            String token = loadToken();
            if (token == null || token.isEmpty()) return false;
            return !isTokenExpired(token);
        } catch (Exception e) {
            AppLogger.log("[SessionManager] isUserLoggedIn check failed: " + e.getMessage());
            return false;
        }
    }

    // JWT Expiration check
    public static boolean isTokenExpired(String token) {
        try {
            String[] parts = token.split("\\.");

            // decode JWT payload
            String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]));

            ObjectMapper mapper = new ObjectMapper();
            JsonNode payload = mapper.readTree(payloadJson);

            long exp = payload.get("exp").asLong();
            long now = System.currentTimeMillis() / 1000;

            return now > exp;
        } catch (Exception e) {
            return true;
        }
    }


}
