package com.musicplayer.scamusica.util;

import com.google.gson.*;
import com.musicplayer.scamusica.model.Ad;
import com.musicplayer.scamusica.model.PlaylistTrack;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;

/**
 * OfflineCache — Playlist aur tracks data disk pe save karta hai.
 * Internet nahi hone pe yahan se data load hota hai.
 *
 * Cache location: ~/.scamusica/cache/
 *   - playlist_titles.json  → sabhi playlist names
 *   - tracks_<genreName>.json → ek genre ke tracks
 *   - download_seq_<genreName>.json → ek genre ki download sequence
 */
public class OfflineCache {

    private static final String CACHE_DIR = System.getProperty("user.home")
            + File.separator + ".scamusica"
            + File.separator + "cache";

    private static final String TITLES_FILE = "playlist_titles.json";
    private static final String TRACKS_PREFIX = "tracks_";
    private static final String SEQ_PREFIX = "download_seq_";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ─── Cache directory ensure karo ────────────────────────────────────────
    private static File getCacheDir() {
        File dir = new File(CACHE_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    // ─── Genre name ko safe filename mein convert karo ───────────────────────
    private static String safeFileName(String genreName) {
        return genreName.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }

    // ════════════════════════════════════════════════════════════════════════
    // PLAYLIST TITLES — save & load
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Playlist titles disk pe save karo (API call successful hone ke baad call karo)
     */
    public static void savePlaylistTitles(List<String> titles) {
        try {
            File file = new File(getCacheDir(), TITLES_FILE);
            String json = GSON.toJson(titles);
            Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
            AppLogger.log("[OfflineCache] Playlist titles saved: " + titles.size());
        } catch (Exception e) {
            AppLogger.log("[OfflineCache] Failed to save titles: " + e.getMessage());
        }
    }

    /**
     * Disk se playlist titles load karo (offline fallback)
     * @return titles list, ya empty list agar cache nahi hai
     */
    public static List<String> loadPlaylistTitles() {
        try {
            File file = new File(getCacheDir(), TITLES_FILE);
            if (!file.exists()) {
                AppLogger.log("[OfflineCache] No cached titles found");
                return new ArrayList<>();
            }
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            List<String> titles = GSON.fromJson(json, List.class);
            AppLogger.log("[OfflineCache] Loaded cached titles: " + (titles != null ? titles.size() : 0));
            return titles != null ? titles : new ArrayList<>();
        } catch (Exception e) {
            AppLogger.log("[OfflineCache] Failed to load titles: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // TRACKS — save & load
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Ek genre ke tracks disk pe save karo
     */
    public static void saveTracks(String genreTitle, List<PlaylistTrack> tracks) {
        try {
            File file = new File(getCacheDir(), TRACKS_PREFIX + safeFileName(genreTitle) + ".json");
            String json = GSON.toJson(tracks);
            Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
            AppLogger.log("[OfflineCache] Tracks saved for genre: " + genreTitle + " (" + tracks.size() + ")");
        } catch (Exception e) {
            AppLogger.log("[OfflineCache] Failed to save tracks: " + e.getMessage());
        }
    }

    /**
     * Disk se ek genre ke tracks load karo (offline fallback)
     * @return tracks list, ya empty list agar cache nahi hai
     */
    public static List<PlaylistTrack> loadTracks(String genreTitle) {
        try {
            File file = new File(getCacheDir(), TRACKS_PREFIX + safeFileName(genreTitle) + ".json");
            if (!file.exists()) {
                AppLogger.log("[OfflineCache] No cached tracks for: " + genreTitle);
                return new ArrayList<>();
            }
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            PlaylistTrack[] arr = GSON.fromJson(json, PlaylistTrack[].class);
            List<PlaylistTrack> tracks = arr != null ? Arrays.asList(arr) : new ArrayList<>();
            AppLogger.log("[OfflineCache] Loaded cached tracks for: " + genreTitle + " (" + tracks.size() + ")");
            return new ArrayList<>(tracks);
        } catch (Exception e) {
            AppLogger.log("[OfflineCache] Failed to load tracks: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // DOWNLOAD SEQUENCE — save & load
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Ek genre ki download sequence disk pe save karo
     */
    public static void saveDownloadSequence(String genreTitle, List<Integer> sequence) {
        try {
            File file = new File(getCacheDir(), SEQ_PREFIX + safeFileName(genreTitle) + ".json");
            String json = GSON.toJson(sequence);
            Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
            AppLogger.log("[OfflineCache] Download sequence saved for: " + genreTitle);
        } catch (Exception e) {
            AppLogger.log("[OfflineCache] Failed to save sequence: " + e.getMessage());
        }
    }

    /**
     * Disk se ek genre ki download sequence load karo (offline fallback)
     * @return sequence list, ya empty list agar cache nahi hai
     */
    public static List<Integer> loadDownloadSequence(String genreTitle) {
        try {
            File file = new File(getCacheDir(), SEQ_PREFIX + safeFileName(genreTitle) + ".json");
            if (!file.exists()) {
                AppLogger.log("[OfflineCache] No cached sequence for: " + genreTitle);
                return new ArrayList<>();
            }
            String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            // Gson numbers ko Double parse karta hai, isliye Integer mein convert karo
            List list = GSON.fromJson(json, List.class);
            List<Integer> sequence = new ArrayList<>();
            if (list != null) {
                for (Object o : list) {
                    if (o instanceof Number) {
                        sequence.add(((Number) o).intValue());
                    }
                }
            }
            AppLogger.log("[OfflineCache] Loaded cached sequence for: " + genreTitle + " (" + sequence.size() + ")");
            return sequence;
        } catch (Exception e) {
            AppLogger.log("[OfflineCache] Failed to load sequence: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // INTERNET CHECK
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Internet available hai ya nahi check karo
     * Simple aur fast — sirf Google DNS ping karta hai
     */
    public static boolean isInternetAvailable() {
        try {
            java.net.InetAddress address = java.net.InetAddress.getByName("8.8.8.8");
            return address.isReachable(2000); // 2 second timeout
        } catch (Exception e) {
            AppLogger.log("[OfflineCache] Internet check failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Cache exist karta hai ya nahi — matlab pehle kabhi online tha ya nahi
     */
    public static boolean hasCachedData() {
        File file = new File(getCacheDir(), TITLES_FILE);
        return file.exists() && file.length() > 0;
    }

    // ✅ Ad schedule save karo
    public static void saveAdSchedule(List<Ad> ads) {
        try {
            File file = new File(getCacheDir(), "ad_schedule.json");
            Gson gson = new Gson();
            String json = gson.toJson(ads);
            try (FileWriter fw = new FileWriter(file)) {
                fw.write(json);
            }
            AppLogger.log("[OfflineCache] Ad schedule saved: " + ads.size() + " ads");
        } catch (Exception e) {
            AppLogger.log("[OfflineCache] Failed to save ad schedule: " + e.getMessage());
        }
    }

    // ✅ Ad schedule load karo
    public static List<Ad> loadAdSchedule() {
        try {
            File file = new File(getCacheDir(), "ad_schedule.json");
            if (!file.exists()) return new ArrayList<>();

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }

            Gson gson = new Gson();
            Type type = new TypeToken<List<Ad>>(){}.getType();
            List<Ad> ads = gson.fromJson(sb.toString(), type);
            AppLogger.log("[OfflineCache] Ad schedule loaded: " + (ads != null ? ads.size() : 0) + " ads");
            return ads != null ? ads : new ArrayList<>();
        } catch (Exception e) {
            AppLogger.log("[OfflineCache] Failed to load ad schedule: " + e.getMessage());
            return new ArrayList<>();
        }
    }
}
