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

        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return trimmed;
        }

        if (!lower.endsWith(".webp")) {
            return trimmed;
        }

        try {
            String tmpDir = System.getProperty("java.io.tmpdir");
            File cacheDir = new File(tmpDir, CACHE_DIR_NAME);
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                System.out.println("[ImageFormatConverter] Failed to create cache dir: " + cacheDir.getAbsolutePath());
            }

            String hash = sha1(trimmed);
            File pngFile = new File(cacheDir, hash + ".png");

            if (pngFile.exists()) {
                System.out.println("[ImageFormatConverter] Using cached PNG: " + pngFile.getAbsolutePath());
                return pngFile.toURI().toString();
            }

            System.out.println("[ImageFormatConverter] Converting WebP to PNG: " + trimmed);

            URL url = new URL(trimmed);
            try (InputStream in = url.openStream()) {
                BufferedImage input = ImageIO.read(in);

                if (input == null) {
                    System.out.println("[ImageFormatConverter] ImageIO.read returned null for: " + trimmed);
                    return trimmed;
                }

                ImageIO.write(input, "png", pngFile);
                System.out.println("[ImageFormatConverter] Converted & cached at: " + pngFile.getAbsolutePath());

                return pngFile.toURI().toString();
            }
        } catch (Exception e) {
            System.out.println("[ImageFormatConverter] Failed to convert WebP image: " + fullUrlOrPath);
            e.printStackTrace();
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