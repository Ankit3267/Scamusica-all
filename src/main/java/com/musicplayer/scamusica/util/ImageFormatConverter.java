package com.musicplayer.scamusica.util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public class ImageFormatConverter {

    private static final String CACHE_DIR_NAME = "scamusica/album-cache";

    public static String ensurePngImage(String fullUrlOrPath) {
        if (fullUrlOrPath == null || fullUrlOrPath.trim().isEmpty()) {
            return null;
        }

        String trimmed = fullUrlOrPath.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);

        // Known JavaFX-compatible formats — return as-is
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")
                || lower.endsWith(".bmp") || lower.endsWith(".gif")) {
            return trimmed;
        }

        // For .webp URLs OR URLs with unknown/missing extensions (e.g., CloudFront
        // paths like /images/abc123), attempt to download and convert via javax.imageio
        // which has the TwelveMonkeys WebP reader registered.
        try {
            String tmpDir = System.getProperty("java.io.tmpdir");
            File cacheDir = new File(tmpDir, CACHE_DIR_NAME);
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                AppLogger.log("[ImageFormatConverter] Failed to create cache dir: " + cacheDir.getAbsolutePath());
            }

            String hash = sha1(trimmed);
            File pngFile = new File(cacheDir, hash + ".png");

            if (pngFile.exists() && pngFile.length() > 0) {
                return pngFile.toURI().toString();
            }

            AppLogger.log("[ImageFormatConverter] Converting image to PNG: " + trimmed);

            URL url = new URL(trimmed);
            try (InputStream in = url.openStream()) {
                BufferedImage input = ImageIO.read(in);

                if (input == null) {
                    AppLogger.log("[ImageFormatConverter] ImageIO.read returned null for: " + trimmed);
                    return trimmed;
                }

                ImageIO.write(input, "png", pngFile);
                AppLogger.log("[ImageFormatConverter] Converted & cached at: " + pngFile.getAbsolutePath());

                return pngFile.toURI().toString();
            }
        } catch (Exception e) {
            AppLogger.log("[ImageFormatConverter] Failed to convert image: " + fullUrlOrPath + " — " + e.getMessage());
            return fullUrlOrPath;
        }
    }

    private static String sha1(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] bytes = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}