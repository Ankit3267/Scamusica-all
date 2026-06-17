package com.musicplayer.scamusica.service;

import com.google.gson.*;
import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.model.Ad;
import com.musicplayer.scamusica.model.PlaylistTrack;
import com.musicplayer.scamusica.util.ApiClient;
import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.OfflineCache;
import com.musicplayer.scamusica.util.Utility;

import java.util.*;

public class PlaylistApiService {

    private static final String SONGS_URL = Utility.BASE_URL.get() + Utility.API_SONGS_ENDPOINT.get();

    // private JsonObject fetchRootJson() throws Exception {
    // String token = SessionManager.loadToken();
    //
    // if (token == null || token.trim().isEmpty()) {
    // System.err.println("[PlaylistApiService] Token is null or empty");
    // throw new IllegalStateException("Bearer token is missing");
    // }
    //
    // System.out.println("[PlaylistApiService] Using token: " + token);
    //
    // Map<String, String> headers = new HashMap<>();
    // headers.put("Authorization", "Bearer " + token);
    // headers.put("Accept", "application/json");
    //
    // String response = ApiClient.get(SONGS_URL, headers);
    // System.out.println("[PlaylistApiService] Raw response : " + response);
    //
    // if (response == null || response.isEmpty()) {
    // throw new IllegalStateException("Empty response from API");
    // }
    //
    // return JsonParser.parseString(response).getAsJsonObject();
    // }

    private JsonObject fetchRootJson() throws Exception {
        String token = SessionManager.loadToken();

        if (token == null || token.trim().isEmpty()) {
            throw new IllegalStateException("Bearer token is missing");
        }

        if (SessionManager.isTokenExpired(token)) {
            AppLogger.log("[PlaylistApiService] Token expired, clearing session");
            SessionManager.clearToken();
            throw new IllegalStateException("Bearer token is expired");
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        headers.put("Accept", "application/json");

        String response = ApiClient.get(SONGS_URL, headers);

        if (response == null || response.isEmpty()) {
            throw new IllegalStateException("Empty response from API");
        }

        return JsonParser.parseString(response).getAsJsonObject();
    }

    public List<String> fetchPlaylistTitles() throws Exception {
        try {
            JsonObject root = fetchRootJson();
            List<String> titles = new ArrayList<>();

            if (!root.has("data") || root.get("data").isJsonNull() || !root.get("data").isJsonObject()) {
                return titles;
            }

            JsonObject dataObj = root.getAsJsonObject("data");

            if (!dataObj.has("sequences") || !dataObj.get("sequences").isJsonArray()) {
                return titles;
            }

            JsonArray sequences = dataObj.getAsJsonArray("sequences");

            for (JsonElement seqEl : sequences) {
                if (!seqEl.isJsonObject())
                    continue;
                JsonObject seqObj = seqEl.getAsJsonObject();
                if (seqObj.has("title") && !seqObj.get("title").isJsonNull()) {
                    String seqTitle = seqObj.get("title").getAsString();
                    if (seqTitle != null && !seqTitle.trim().isEmpty()) {
                        titles.add(seqTitle);
                    }
                }
            }

            System.out.println("[PlaylistApiService] Playlists from API: " + titles);

            if (!titles.isEmpty()) {
                OfflineCache.savePlaylistTitles(titles);
            }

            return titles;

        } catch (Exception e) {
            AppLogger.log("[PlaylistApiService] fetchPlaylistTitles failed, loading from cache: " + e.getMessage());
            List<String> cached = OfflineCache.loadPlaylistTitles();
            if (!cached.isEmpty()) {
                AppLogger.log("[PlaylistApiService] Using cached titles: " + cached.size());
                return cached;
            }
            throw e;
        }
    }

    public List<PlaylistTrack> fetchTracksForGenre(String genreTitle) throws Exception {
        try {
            List<PlaylistTrack> result = new ArrayList<>();

            JsonObject root = fetchRootJson();

            if (!root.has("data") || root.get("data").isJsonNull() || !root.get("data").isJsonObject()) {
                System.out.println("[PlaylistApiService] 'data' field missing or not object in response");
                return result;
            }

            JsonObject dataObj = root.getAsJsonObject("data");

            if (!dataObj.has("sequences") || !dataObj.get("sequences").isJsonArray()) {
                System.out.println("[PlaylistApiService] 'sequences' missing or not array");
                return result;
            }

            JsonArray sequences = dataObj.getAsJsonArray("sequences");

            String commonPath = null;
            if (root.has("filePath") && !root.get("filePath").isJsonNull()) {
                commonPath = root.get("filePath").getAsString();
            }

            for (JsonElement seqEl : sequences) {
                if (!seqEl.isJsonObject())
                    continue;
                JsonObject seqObj = seqEl.getAsJsonObject();

                if (!seqObj.has("title") || seqObj.get("title").isJsonNull())
                    continue;
                String seqTitle = seqObj.get("title").getAsString();
                if (!genreTitle.equals(seqTitle))
                    continue;

                if (!seqObj.has("styles") || !seqObj.get("styles").isJsonArray())
                    continue;
                JsonArray styles = seqObj.getAsJsonArray("styles");

                List<List<PlaylistTrack>> stylesTracks = new ArrayList<>();
                for (JsonElement styleEl : styles) {
                    if (!styleEl.isJsonObject())
                        continue;
                    JsonObject styleObj = styleEl.getAsJsonObject();

                    String folderTitle = null;
                    if (styleObj.has("title") && !styleObj.get("title").isJsonNull()) {
                        folderTitle = styleObj.get("title").getAsString();
                    }

                    String albumImg = null;

                    if (styleObj.has("album_img")
                            && !styleObj.get("album_img").isJsonNull()) {

                        albumImg = styleObj.get("album_img").getAsString();

                        AppLogger.log("[TRACK] Style album_img = " + albumImg);
                    }

                    if (!styleObj.has("songs") || !styleObj.get("songs").isJsonArray())
                        continue;
                    JsonArray songsArr = styleObj.getAsJsonArray("songs");

                    List<PlaylistTrack> folderTracks = new ArrayList<>();
                    for (JsonElement songEl : songsArr) {
                        if (!songEl.isJsonObject())
                            continue;
                        PlaylistTrack track = parseSongToTrack(songEl.getAsJsonObject(), commonPath, folderTitle,
                                albumImg);
                        if (track != null) {
                            folderTracks.add(track);
                        }
                    }

                    Collections.shuffle(folderTracks);
                    stylesTracks.add(folderTracks);
                }

                boolean added;
                do {
                    added = false;
                    for (List<PlaylistTrack> trackList : stylesTracks) {
                        if (!trackList.isEmpty()) {
                            result.add(trackList.remove(0));
                            added = true;
                        }
                    }
                } while (added);
                break;
            }

            System.out.println("[PlaylistApiService] Final Tracks with folder titles → " + result);

            if (!result.isEmpty()) {
                OfflineCache.saveTracks(genreTitle, result);
            }

            return result;

        } catch (Exception e) {
            AppLogger.log("[PlaylistApiService] fetchTracksForGenre failed, loading from cache: " + e.getMessage());
            List<PlaylistTrack> cached = OfflineCache.loadTracks(genreTitle);
            if (!cached.isEmpty()) {
                AppLogger.log("[PlaylistApiService] Using cached tracks for: " + genreTitle);
                return cached;
            }
            return new ArrayList<>();
        }
    }

    public List<Integer> fetchDownloadSequenceForGenre(String genreTitle) throws Exception {
        try {
            List<Integer> downloadSequence = new ArrayList<>();

            JsonObject root = fetchRootJson();

            if (!root.has("data") || root.get("data").isJsonNull() || !root.get("data").isJsonObject()) {
                return downloadSequence;
            }

            JsonObject dataObj = root.getAsJsonObject("data");

            if (!dataObj.has("sequences") || !dataObj.get("sequences").isJsonArray()) {
                return downloadSequence;
            }

            JsonArray sequences = dataObj.getAsJsonArray("sequences");

            String commonPath = null;
            if (root.has("filePath") && !root.get("filePath").isJsonNull()) {
                commonPath = root.get("filePath").getAsString();
            }

            Set<Integer> seenIds = new HashSet<>();

            for (JsonElement seqEl : sequences) {
                if (!seqEl.isJsonObject())
                    continue;
                JsonObject seqObj = seqEl.getAsJsonObject();

                if (!seqObj.has("title") || seqObj.get("title").isJsonNull())
                    continue;
                String seqTitle = seqObj.get("title").getAsString();
                if (!genreTitle.equals(seqTitle))
                    continue;

                if (!seqObj.has("styles") || !seqObj.get("styles").isJsonArray())
                    continue;
                JsonArray styles = seqObj.getAsJsonArray("styles");

                List<List<Integer>> stylesDownloadTracks = new ArrayList<>();
                for (JsonElement styleEl : styles) {
                    if (!styleEl.isJsonObject())
                        continue;
                    JsonObject styleObj = styleEl.getAsJsonObject();

                    if (!styleObj.has("songs") || !styleObj.get("songs").isJsonArray())
                        continue;
                    JsonArray songsArr = styleObj.getAsJsonArray("songs");

                    String albumImg = null;

                    if (styleObj.has("album_img")
                            && !styleObj.get("album_img").isJsonNull()) {

                        albumImg = styleObj.get("album_img").getAsString();
                    }

                    List<PlaylistTrack> tracks = new ArrayList<>();
                    for (JsonElement songEl : songsArr) {
                        if (!songEl.isJsonObject())
                            continue;
                        PlaylistTrack track = parseSongToTrack(songEl.getAsJsonObject(), commonPath, null, albumImg);
                        if (track != null && track.getId() != null && seenIds.add(track.getId())) {
                            tracks.add(track);
                        }
                    }

                    Collections.shuffle(tracks);
                    List<Integer> styleIds = new ArrayList<>();
                    for (PlaylistTrack t : tracks) {
                        if (t.getId() != null) {
                            styleIds.add(t.getId());
                        }
                    }
                    stylesDownloadTracks.add(styleIds);
                }

                boolean added;
                do {
                    added = false;
                    for (List<Integer> styleIds : stylesDownloadTracks) {
                        if (!styleIds.isEmpty()) {
                            downloadSequence.add(styleIds.remove(0));
                            added = true;
                        }
                    }
                } while (added);
                break;
            }

            System.out.println("[PlaylistApiService] Download sequence for '" + genreTitle + "': " + downloadSequence);

            if (!downloadSequence.isEmpty()) {
                OfflineCache.saveDownloadSequence(genreTitle, downloadSequence);
            }

            return downloadSequence;

        } catch (Exception e) {
            AppLogger.log("[PlaylistApiService] fetchDownloadSequence failed, loading from cache: " + e.getMessage());
            List<Integer> cached = OfflineCache.loadDownloadSequence(genreTitle);
            AppLogger.log("[PlaylistApiService] Using cached sequence: " + cached.size() + " items");
            return cached;
        }
    }

    private PlaylistTrack parseSongToTrack(JsonObject songObj,
                                           String commonPath,
                                           String folderTitle,
                                           String albumImg) {
        if (!songObj.has("file")) {
            System.out.println("[PlaylistApiService] Song missing 'file' field, skipping.");
            return null;
        }

        Integer songId = null;
        if (songObj.has("id") && !songObj.get("id").isJsonNull()) {
            try {
                songId = songObj.get("id").getAsInt();
            } catch (Exception ignored) {
            }
        }

        String title;
        if (songObj.has("file") && !songObj.get("file").isJsonNull()) {
            String fileName = songObj.get("file").getAsString();
            title = fileName.endsWith(".mp3")
                    ? fileName.substring(0, fileName.length() - 4)
                    : fileName;
        } else if (songObj.has("title") && !songObj.get("title").isJsonNull()) {
            title = songObj.get("title").getAsString();
        } else {
            title = "Unknown Title";
        }

        String filePath;
        if (songObj.has("filePath") && !songObj.get("filePath").isJsonNull()) {
            filePath = songObj.get("filePath").getAsString();
        } else {
            String fileName = songObj.get("file").getAsString();
            if (commonPath != null) {
                if (!commonPath.endsWith("/")) {
                    commonPath = commonPath + "/";
                }
                filePath = commonPath + fileName;
            } else {
                filePath = "/public/music/" + fileName;
            }
        }

        String fullUrl;
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            fullUrl = filePath;
        } else {
            fullUrl = Utility.BASE_URL.get() + filePath;
        }

        int durationSeconds = 0;
        if (songObj.has("duration") && !songObj.get("duration").isJsonNull()) {
            try {
                String durationStr = songObj.get("duration").getAsString();
                String[] parts = durationStr.split(":");
                durationSeconds = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
            } catch (Exception ignored) {
            }
        }

        // String albumImgPath = null;
        // if (songObj.has("album_img") && !songObj.get("album_img").isJsonNull()) {
        // albumImgPath = songObj.get("album_img").getAsString();
        // AppLogger.log("[TRACK] album_img raw = " + albumImgPath);
        // }
        // else {
        // AppLogger.log("[TRACK] album_img MISSING for song: " + songObj);
        // }

        String albumImgPath = albumImg;

        String fullAlbumImgUrl = null;
        if (albumImgPath != null && !albumImgPath.trim().isEmpty()) {
            if (albumImgPath.startsWith("http://") || albumImgPath.startsWith("https://")) {
                fullAlbumImgUrl = albumImgPath;
            } else {
                fullAlbumImgUrl = Utility.BASE_URL.get()
                        + "/"
                        + albumImgPath.replaceFirst("^/", "");
            }
        }

        return new PlaylistTrack(songId, title, fullUrl, durationSeconds, folderTitle, fullAlbumImgUrl);
    }

    public List<Ad> fetchAds() throws Exception {
        try {
            JsonObject root = fetchRootJson();
            List<Ad> ads = new ArrayList<>();
            if (!root.has("data") || !root.get("data").isJsonObject()) {
                return ads;
            }

            JsonObject dataObj = root.getAsJsonObject("data");

            if (!dataObj.has("ads") || !dataObj.get("ads").isJsonArray()) {
                return ads;
            }

            JsonArray adsArray = dataObj.getAsJsonArray("ads");

            for (JsonElement adEl : adsArray) {
                if (!adEl.isJsonObject())
                    continue;
                JsonObject adObj = adEl.getAsJsonObject();

                Ad ad = new Ad();
                ad.setId(adObj.has("id") && !adObj.get("id").isJsonNull() ? adObj.get("id").getAsInt() : null);
                ad.setCampaignName(adObj.has("campaign_name") && !adObj.get("campaign_name").isJsonNull() ? adObj.get("campaign_name").getAsString() : "Unknown");
                ad.setScheduleType(adObj.has("schedule_type") && !adObj.get("schedule_type").isJsonNull() ? adObj.get("schedule_type").getAsString() : null);
                ad.setStartDate(adObj.has("start_date") && !adObj.get("start_date").isJsonNull() ? adObj.get("start_date").getAsString() : null);
                ad.setEndDate(adObj.has("end_date") && !adObj.get("end_date").isJsonNull() ? adObj.get("end_date").getAsString() : null);
                ad.setStatus(adObj.has("status") && !adObj.get("status").isJsonNull() ? adObj.get("status").getAsString() : "inactive");

                List<com.musicplayer.scamusica.model.AdAudio> adAudioList = new ArrayList<>();
                if (adObj.has("adAudios") && adObj.get("adAudios").isJsonArray()) {
                    JsonArray adAudiosArray = adObj.getAsJsonArray("adAudios");
                    for (JsonElement audioEl : adAudiosArray) {
                        if (!audioEl.isJsonObject()) continue;
                        JsonObject audioObj = audioEl.getAsJsonObject();
                        com.musicplayer.scamusica.model.AdAudio adAudio = new com.musicplayer.scamusica.model.AdAudio();
                        adAudio.setId(audioObj.has("id") && !audioObj.get("id").isJsonNull() ? audioObj.get("id").getAsInt() : null);
                        adAudio.setAdId(audioObj.has("ad_id") && !audioObj.get("ad_id").isJsonNull() ? audioObj.get("ad_id").getAsInt() : null);
                        adAudio.setAudioFile(audioObj.has("audio_file") && !audioObj.get("audio_file").isJsonNull() ? audioObj.get("audio_file").getAsString() : null);
                        adAudio.setAudioSource(audioObj.has("audio_source") && !audioObj.get("audio_source").isJsonNull() ? audioObj.get("audio_source").getAsString() : null);
                        adAudio.setSortOrder(audioObj.has("sort_order") && !audioObj.get("sort_order").isJsonNull() ? audioObj.get("sort_order").getAsInt() : 0);
                        adAudioList.add(adAudio);
                    }
                }
                
                adAudioList.sort(Comparator.comparingInt(a -> a.getSortOrder() != null ? a.getSortOrder() : 0));
                ad.setAdAudios(adAudioList);

                // Handle playTimes (can be array or number)
                if (adObj.has("play_times")) {
                    JsonElement ptEl = adObj.get("play_times");
                    if (ptEl.isJsonArray()) {
                        // Custom schedule
                        List<String> times = new ArrayList<>();
                        for (JsonElement t : ptEl.getAsJsonArray()) {
                            times.add(t.getAsString());
                        }
                        ad.setPlayTimes(times);
                    } else if (ptEl.isJsonPrimitive()) {
                        // Interval schedule
                        ad.setPlayTimes(ptEl.getAsInt());
                    }
                }

                // Handle activeDays
                if (adObj.has("active_days") && adObj.get("active_days").isJsonArray()) {
                    List<String> days = new ArrayList<>();
                    for (JsonElement day : adObj.getAsJsonArray("active_days")) {
                        days.add(day.getAsString());
                    }
                    ad.setActiveDays(days);
                }

                ads.add(ad);
            }

            System.out.println("[PlaylistApiService] Fetched " + ads.size() + " ads");

            // Cache ads if needed
            // OfflineCache.saveAds(ads);

            return ads;

        } catch (Exception e) {
            AppLogger.log("[PlaylistApiService] fetchAds failed: " + e.getMessage());
            // Try cache if available
            // List<Ad> cached = OfflineCache.loadAds();
            return new ArrayList<>();
        }
    }
}
