package com.musicplayer.scamusica.service;

import com.musicplayer.scamusica.model.CrossfadeSettings;
import com.musicplayer.scamusica.model.PlaylistTrack;
import com.musicplayer.scamusica.util.AppLogger;
import javafx.application.Platform;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent;

import java.io.File;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CrossfadeEngine manages dual VLC MediaPlayer instances (A and B)
 * to provide seamless audio crossfading between consecutive tracks.
 *
 * When current song reaches (duration - crossfadeDuration):
 * 1. Incoming player starts playing next song at volume 0.
 * 2. Outgoing player gradually fades down (100% -> 0%).
 * 3. Incoming player gradually fades up (0% -> 100%).
 * 4. When fade completes, outgoing player stops and roles swap.
 */
public class CrossfadeEngine {

    public interface CrossfadeListener {
        /**
         * Called on the JavaFX thread when crossfade overlap begins.
         * Useful for updating track UI (title, album, etc.) to the new track.
         */
        void onCrossfadeStarted(PlaylistTrack incomingTrack, File incomingTempFile);

        /**
         * Called on the JavaFX thread when crossfade completes.
         * The incoming player is now activePlayer, and outgoing temp file can be cleaned up.
         */
        void onCrossfadeCompleted(MediaPlayer newActivePlayer, PlaylistTrack currentTrack, File outgoingTempFile);

        /**
         * Called on error during crossfade.
         */
        void onCrossfadeFailed(Exception e);
    }

    private final MediaPlayerFactory factory;
    private final AudioPlayerComponent componentA;
    private final AudioPlayerComponent componentB;

    private volatile MediaPlayer activePlayer;
    private volatile MediaPlayer standbyPlayer;
    private volatile AudioPlayerComponent activeComponent;
    private volatile AudioPlayerComponent standbyComponent;

    // Crossfade configuration
    private volatile boolean crossfadeEnabled = false;
    private volatile int crossfadeDurationSec = CrossfadeSettings.DEFAULT_DURATION;
    private volatile int crossfadeDurationMs = CrossfadeSettings.DEFAULT_DURATION * 1000;
    private volatile int targetVolume = (int) com.musicplayer.scamusica.controller.PlayerController.DEFAULT_SAFE_VOLUME;

    // Crossfade runtime state
    private volatile boolean isCrossfading = false;
    private final ScheduledExecutorService fadeExecutor;
    private ScheduledFuture<?> fadeTask = null;

    // Preloaded next track details
    private volatile PlaylistTrack preparedTrack = null;
    private volatile String preparedMediaPath = null;
    private volatile File preparedTempFile = null;

    // Temp file of current playing track
    private volatile File activeTempFile = null;

    private CrossfadeListener listener;

    public CrossfadeEngine(MediaPlayerFactory factory) {
        this.factory = factory;
        this.componentA = new AudioPlayerComponent(factory);
        this.componentB = new AudioPlayerComponent(factory);

        this.activeComponent = componentA;
        this.standbyComponent = componentB;
        this.activePlayer = componentA.mediaPlayer();
        this.standbyPlayer = componentB.mediaPlayer();

        this.fadeExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CrossfadeEngine-Thread");
            t.setDaemon(true);
            return t;
        });

        AppLogger.log("[CrossfadeEngine] Initialized with dual AudioPlayerComponents");
    }

    public void setListener(CrossfadeListener listener) {
        this.listener = listener;
    }

    // ─── Player Accessors ───

    public MediaPlayer getActivePlayer() {
        return activePlayer;
    }

    public MediaPlayer getStandbyPlayer() {
        return standbyPlayer;
    }

    public AudioPlayerComponent getActiveComponent() {
        return activeComponent;
    }

    public AudioPlayerComponent getStandbyComponent() {
        return standbyComponent;
    }

    public MediaPlayerFactory getFactory() {
        return factory;
    }

    // ─── Settings ───

    public boolean isCrossfadeEnabled() {
        return crossfadeEnabled;
    }

    public void setCrossfadeEnabled(boolean enabled) {
        this.crossfadeEnabled = enabled;
        AppLogger.log("[CrossfadeEngine] Crossfade enabled set to: " + enabled);
    }

    public int getCrossfadeDuration() {
        return crossfadeDurationSec;
    }

    public void setCrossfadeDuration(int seconds) {
        if (seconds <= 0) {
            seconds = CrossfadeSettings.DEFAULT_DURATION;
        }
        this.crossfadeDurationSec = seconds;
        this.crossfadeDurationMs = seconds * 1000;
        AppLogger.log("[CrossfadeEngine] Crossfade duration set to: " + seconds + "s (" + crossfadeDurationMs + "ms)");
    }

    public int getTargetVolume() {
        return targetVolume;
    }

    public void setTargetVolume(int volume) {
        this.targetVolume = Math.max(0, Math.min(100, volume));
        if (!isCrossfading && activePlayer != null) {
            try {
                activePlayer.audio().setVolume(com.musicplayer.scamusica.controller.PlayerController.toSafeVlcVolume(this.targetVolume));
            } catch (Exception ignored) {}
        }
    }

    public boolean isCrossfading() {
        return isCrossfading;
    }

    // ─── Active Track File Tracking ───

    public void setActiveTempFile(File file) {
        this.activeTempFile = file;
    }

    public File getActiveTempFile() {
        return activeTempFile;
    }

    // ─── Next Track Preparation ───

    public synchronized void prepareNextTrack(PlaylistTrack track, String mediaPath, File tempFile) {
        // If an unused prepared temp file exists and is different, clean it up
        if (this.preparedTempFile != null && this.preparedTempFile.exists() && !this.preparedTempFile.equals(tempFile)) {
            try {
                this.preparedTempFile.delete();
            } catch (Exception ignored) {}
        }

        this.preparedTrack = track;
        this.preparedMediaPath = mediaPath;
        this.preparedTempFile = tempFile;

        AppLogger.log("[CrossfadeEngine] Prepared next track for crossfade: "
                + (track != null ? track.getTitle() : "null")
                + " [Path: " + mediaPath + "]");
    }

    public synchronized boolean hasPreparedTrack() {
        return preparedTrack != null && preparedMediaPath != null;
    }

    public PlaylistTrack getPreparedTrack() {
        return preparedTrack;
    }

    public String getPreparedMediaPath() {
        return preparedMediaPath;
    }

    public File getPreparedTempFile() {
        return preparedTempFile;
    }

    public synchronized void clearPreparedTrack(boolean deleteTempFile) {
        if (deleteTempFile && preparedTempFile != null && preparedTempFile.exists()) {
            try {
                preparedTempFile.delete();
            } catch (Exception ignored) {}
        }
        this.preparedTrack = null;
        this.preparedMediaPath = null;
        this.preparedTempFile = null;
    }

    // ─── Crossfade Trigger & Execution ───

    /**
     * Checks whether crossfade should trigger based on current time and total song length.
     * Returns true if crossfade was triggered.
     */
    public boolean checkAndTriggerCrossfade(long currentTimeMs, long totalDurationMs) {
        if (!crossfadeEnabled || isCrossfading) {
            return false;
        }

        if (totalDurationMs <= 0 || totalDurationMs <= (long) crossfadeDurationMs * 2) {
            // Song is too short to crossfade safely
            return false;
        }

        if (!hasPreparedTrack()) {
            // No next track ready to crossfade into
            return false;
        }

        long remainingMs = totalDurationMs - currentTimeMs;
        if (remainingMs <= crossfadeDurationMs && remainingMs > 0) {
            AppLogger.log("[CrossfadeEngine] Triggering crossfade! Remaining: " + remainingMs + "ms, Duration: " + crossfadeDurationMs + "ms");
            startCrossfade();
            return true;
        }

        return false;
    }

    /**
     * Starts the crossfade transition between activePlayer and standbyPlayer.
     */
    public synchronized void startCrossfade() {
        if (isCrossfading) {
            return;
        }

        if (!hasPreparedTrack()) {
            AppLogger.log("[CrossfadeEngine] Cannot start crossfade: No prepared track available");
            return;
        }

        isCrossfading = true;

        final PlaylistTrack incomingTrack = this.preparedTrack;
        final String mediaPath = this.preparedMediaPath;
        final File incomingTempFile = this.preparedTempFile;
        final File outgoingTempFile = this.activeTempFile;

        final MediaPlayer outgoingPlayer = this.activePlayer;
        final MediaPlayer incomingPlayer = this.standbyPlayer;

        // Clear prepared fields so we don't double-trigger
        this.preparedTrack = null;
        this.preparedMediaPath = null;
        this.preparedTempFile = null;

        AppLogger.log("[CrossfadeEngine] Starting crossfade into: " + (incomingTrack != null ? incomingTrack.getTitle() : "Unknown"));

        try {
            // 1. Start incoming player at volume 0
            incomingPlayer.audio().setVolume(0);
            incomingPlayer.media().play(mediaPath);
            incomingPlayer.audio().setVolume(0);

            // Notify listener that overlap started (update UI to incoming track)
            if (listener != null) {
                Platform.runLater(() -> {
                    try {
                        listener.onCrossfadeStarted(incomingTrack, incomingTempFile);
                    } catch (Exception e) {
                        AppLogger.log("[CrossfadeEngine] Error in onCrossfadeStarted listener: " + e.getMessage());
                    }
                });
            }

            // 2. Schedule gradual volume fade (50ms interval)
            final int tickMs = 50;
            final int totalSteps = Math.max(1, crossfadeDurationMs / tickMs);
            final AtomicInteger currentStep = new AtomicInteger(0);

            if (fadeTask != null && !fadeTask.isDone()) {
                fadeTask.cancel(true);
            }

            fadeTask = fadeExecutor.scheduleAtFixedRate(() -> {
                try {
                    int step = currentStep.incrementAndGet();

                    if (step >= totalSteps || !isCrossfading) {
                        // Fade completed
                        completeCrossfade(outgoingPlayer, incomingPlayer, incomingTrack, incomingTempFile, outgoingTempFile);
                        return;
                    }

                    float progress = (float) step / (float) totalSteps;
                    int baseVol = com.musicplayer.scamusica.controller.PlayerController.toSafeVlcVolume(targetVolume);

                    // Equal-power crossfade curve: cos(progress * PI/2) and sin(progress * PI/2)
                    // Prevents acoustic energy summing above 0 dBFS during dual-player overlap
                    double outFactor = Math.cos(progress * (Math.PI / 2.0));
                    double inFactor = Math.sin(progress * (Math.PI / 2.0));

                    int outVol = (int) Math.round(baseVol * outFactor);
                    int inVol = (int) Math.round(baseVol * inFactor);

                    outVol = Math.max(0, Math.min(baseVol, outVol));
                    inVol = Math.max(0, Math.min(baseVol, inVol));

                    outgoingPlayer.audio().setVolume(outVol);
                    incomingPlayer.audio().setVolume(inVol);

                } catch (Exception ex) {
                    AppLogger.log("[CrossfadeEngine] Error in fade step: " + ex.getMessage());
                }
            }, tickMs, tickMs, TimeUnit.MILLISECONDS);

        } catch (Exception e) {
            AppLogger.log("[CrossfadeEngine] Failed to start crossfade: " + e.getMessage());
            isCrossfading = false;
            if (listener != null) {
                Platform.runLater(() -> listener.onCrossfadeFailed(e));
            }
        }
    }

    private synchronized void completeCrossfade(MediaPlayer outgoingPlayer,
                                               MediaPlayer incomingPlayer,
                                               PlaylistTrack incomingTrack,
                                               File incomingTempFile,
                                               File outgoingTempFile) {
        if (fadeTask != null) {
            fadeTask.cancel(false);
            fadeTask = null;
        }

        try {
            // Set final volume state
            outgoingPlayer.audio().setVolume(0);
            incomingPlayer.audio().setVolume(com.musicplayer.scamusica.controller.PlayerController.toSafeVlcVolume(targetVolume));

            // Stop outgoing player
            outgoingPlayer.controls().stop();

            // Swap player references
            MediaPlayer tempPlayer = activePlayer;
            activePlayer = standbyPlayer;
            standbyPlayer = tempPlayer;

            AudioPlayerComponent tempComp = activeComponent;
            activeComponent = standbyComponent;
            standbyComponent = tempComp;

            activeTempFile = incomingTempFile;
            isCrossfading = false;

            AppLogger.log("[CrossfadeEngine] Crossfade complete! Swapped active player.");

            // Notify listener on JavaFX thread
            if (listener != null) {
                Platform.runLater(() -> {
                    try {
                        listener.onCrossfadeCompleted(activePlayer, incomingTrack, outgoingTempFile);
                    } catch (Exception e) {
                        AppLogger.log("[CrossfadeEngine] Error in onCrossfadeCompleted listener: " + e.getMessage());
                    }
                });
            }

        } catch (Exception e) {
            AppLogger.log("[CrossfadeEngine] Error completing crossfade: " + e.getMessage());
            isCrossfading = false;
        }
    }

    /**
     * Cancels any active crossfade immediately (e.g. user pressed skip, pause, ad triggered).
     * Stops the standby player and restores activePlayer volume to targetVolume.
     */
    public synchronized void cancelCrossfade() {
        cancelCrossfade(false);
    }

    /**
     * Cancels any active crossfade with option to clear prepared track.
     */
    public synchronized void cancelCrossfade(boolean clearPrepared) {
        if (fadeTask != null) {
            fadeTask.cancel(true);
            fadeTask = null;
        }

        if (isCrossfading) {
            AppLogger.log("[CrossfadeEngine] Cancelling in-progress crossfade");
            isCrossfading = false;

            try {
                if (standbyPlayer != null) {
                    standbyPlayer.controls().stop();
                    standbyPlayer.audio().setVolume(0);
                }
            } catch (Exception ignored) {}

            try {
                if (activePlayer != null) {
                    activePlayer.audio().setVolume(com.musicplayer.scamusica.controller.PlayerController.toSafeVlcVolume(targetVolume));
                }
            } catch (Exception ignored) {}
        }

        if (clearPrepared) {
            clearPreparedTrack(true);
        }
    }

    /**
     * Stops both players and cancels any running crossfade.
     */
    public synchronized void stop() {
        cancelCrossfade(true);
        try {
            if (activePlayer != null) activePlayer.controls().stop();
            if (standbyPlayer != null) standbyPlayer.controls().stop();
        } catch (Exception ignored) {}
    }

    /**
     * Shuts down executors and releases both VLC components.
     */
    public synchronized void release() {
        stop();
        try {
            fadeExecutor.shutdownNow();
        } catch (Exception ignored) {}

        try {
            if (componentA != null) componentA.release();
        } catch (Exception ignored) {}

        try {
            if (componentB != null) componentB.release();
        } catch (Exception ignored) {}

        AppLogger.log("[CrossfadeEngine] Released resources");
    }
}
