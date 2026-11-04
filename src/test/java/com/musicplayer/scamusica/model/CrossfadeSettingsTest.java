package com.musicplayer.scamusica.model;

import com.musicplayer.scamusica.util.OfflineCache;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class CrossfadeSettingsTest {

    @AfterEach
    public void cleanup() {
        CrossfadeSettings disabled = new CrossfadeSettings();
        disabled.setCrossfadeEnabled(false);
        disabled.setCrossfadeDuration(CrossfadeSettings.DEFAULT_DURATION);
        OfflineCache.saveCrossfadeSettings(disabled);
    }

    @Test
    public void testDefaultDurationWhenNull() {
        CrossfadeSettings settings = new CrossfadeSettings();
        assertEquals(5, settings.getCrossfadeDuration(), "Default duration should be 5 seconds when null");
        assertFalse(settings.isCrossfadeEnabled(), "Default status should be disabled when null");
    }

    @Test
    public void testDefaultDurationWhenZeroOrNegative() {
        CrossfadeSettings settings = new CrossfadeSettings();
        settings.setCrossfadeDuration(0);
        assertEquals(5, settings.getCrossfadeDuration(), "Duration <= 0 should fall back to default 5 seconds");

        settings.setCrossfadeDuration(-3);
        assertEquals(5, settings.getCrossfadeDuration(), "Negative duration should fall back to default 5 seconds");
    }

    @Test
    public void testCustomDurationAndEnabled() {
        CrossfadeSettings settings = new CrossfadeSettings();
        settings.setCrossfadeDuration(10);
        settings.setCrossfadeEnabled(true);

        assertEquals(10, settings.getCrossfadeDuration());
        assertTrue(settings.isCrossfadeEnabled());
    }

    @Test
    public void testOfflineCacheSaveAndLoad() {
        CrossfadeSettings original = new CrossfadeSettings();
        original.setCrossfadeEnabled(true);
        original.setCrossfadeDuration(8);

        OfflineCache.saveCrossfadeSettings(original);
        CrossfadeSettings loaded = OfflineCache.loadCrossfadeSettings();

        assertNotNull(loaded, "Loaded crossfade settings should not be null");
        assertTrue(loaded.isCrossfadeEnabled());
        assertEquals(8, loaded.getCrossfadeDuration());
    }
}

