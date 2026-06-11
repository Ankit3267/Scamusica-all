package com.musicplayer.scamusica.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.musicplayer.scamusica.model.PlaylistTrack;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;

public class SongMetadataStore {

    private static final Gson gson =
            new GsonBuilder().setPrettyPrinting().create();

    private static File metaFile(File mp3) {
        return new File(
                mp3.getParentFile(),
                mp3.getName().replace(".mp3", ".json")
        );
    }

    public static void save(File mp3, PlaylistTrack track) {
        if (track == null) return;

        try (FileWriter fw = new FileWriter(metaFile(mp3))) {
            gson.toJson(track, fw);
        } catch (Exception e) {
            System.out.println("[MetadataStore] Save failed: " + e.getMessage());
        }
    }

    public static PlaylistTrack load(File mp3) {
        try {
            File meta = metaFile(mp3);
            if (!meta.exists()) return null;

            try (FileReader fr = new FileReader(meta)) {
                return gson.fromJson(fr, PlaylistTrack.class);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
