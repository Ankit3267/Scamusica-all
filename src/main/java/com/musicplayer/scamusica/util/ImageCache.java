package com.musicplayer.scamusica.util;

import javafx.scene.image.Image;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

public class ImageCache {

    private static final int MAX_MEMORY_CACHE_SIZE = 10;

    // LRU Cache for Image objects
    private static final Map<String, Image> memoryCache = new LinkedHashMap<String, Image>(MAX_MEMORY_CACHE_SIZE + 1, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MAX_MEMORY_CACHE_SIZE;
        }
    };

    private static final String IMAGES_DIR = System.getProperty("user.home")
            + File.separator + ".scamusica"
            + File.separator + "images";

    private static File getImagesDir() {
        File dir = new File(IMAGES_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private static String getFilenameFromUrl(String url) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(url.getBytes("UTF-8"));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString() + ".jpg";
        } catch (Exception e) {
            // Fallback if MD5 is missing
            return url.replaceAll("[^a-zA-Z0-9.-]", "_") + ".jpg";
        }
    }

    public static Image getImage(String imageUrl) {
        if (imageUrl == null || imageUrl.trim().isEmpty()) {
            return null;
        }

        synchronized (memoryCache) {
            Image cachedImage = memoryCache.get(imageUrl);
            if (cachedImage != null) {
                return cachedImage;
            }
        }

        try {
            File imageFile = new File(getImagesDir(), getFilenameFromUrl(imageUrl));

            // If the file exists on disk, load it with reduced size to save memory
            if (imageFile.exists() && imageFile.length() > 0) {
                AppLogger.log("[ImageCache] Loading image from disk cache: " + imageFile.getAbsolutePath());
                try (InputStream in = new FileInputStream(imageFile)) {
                    // 400x400 limit saves memory footprint
                    Image img = new Image(in, 400, 400, true, true);
                    synchronized (memoryCache) {
                        memoryCache.put(imageUrl, img);
                    }
                    return img;
                }
            }

            // Otherwise, download it, save to disk, and return
            if (imageUrl.startsWith("http")) {
                AppLogger.log("[ImageCache] Downloading image: " + imageUrl);
                HttpURLConnection connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setRequestProperty("User-Agent", "Mozilla/5.0");
                connection.setRequestProperty("Connection", "close");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                try (InputStream in = connection.getInputStream();
                     OutputStream out = new FileOutputStream(imageFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                    }
                } finally {
                    connection.disconnect();
                }

                // Read from the newly saved file with reduced size
                try (InputStream in = new FileInputStream(imageFile)) {
                    Image img = new Image(in, 400, 400, true, true);
                    synchronized (memoryCache) {
                        memoryCache.put(imageUrl, img);
                    }
                    return img;
                }
            } else {
                return new Image(imageUrl, 400, 400, true, true, true);
            }

        } catch (Exception e) {
            AppLogger.log("[ImageCache] Error loading image: " + e.getMessage());
            // Fallback to direct loading
            return new Image(imageUrl, 400, 400, true, true, true);
        }
    }

    public static void clearMemoryCache() {
        synchronized (memoryCache) {
            memoryCache.clear();
        }
    }
}
