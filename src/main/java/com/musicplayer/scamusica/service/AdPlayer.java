package com.musicplayer.scamusica.service;

import com.musicplayer.scamusica.model.Ad;
import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.Utility;
import javafx.application.Platform;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;

public class AdPlayer {

    public interface AdPlaybackListener {
        void onAdPlaybackStarted(Ad ad, com.musicplayer.scamusica.model.AdAudio adAudio);

        void onAdPlaybackFinished(Ad ad);

        void onSongPaused(String reason);

        void onSongResumed();

        void onPlaybackError(Exception ex);
    }

    private final MediaPlayer vlcPlayer;
    private final AdPlaybackListener listener;
    // private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "AdPlayer-Thread");
        t.setDaemon(true);
        return t;
    });
    private final Queue<Ad> adQueue = new ConcurrentLinkedQueue<>();
    private volatile boolean isPlayingAd = false;
    private volatile boolean songPausedForAds = false;

    private volatile String savedSongPath = null;
    private volatile long savedSongTime = 0L;

    private java.util.function.Supplier<Integer> adVolumeProvider;

    public AdPlayer(MediaPlayer vlcPlayer, AdPlaybackListener listener) {
        this.vlcPlayer = vlcPlayer;
        this.listener = listener;
    }

    public void setAdVolumeProvider(java.util.function.Supplier<Integer> adVolumeProvider) {
        this.adVolumeProvider = adVolumeProvider;
    }

    public void queueAds(List<Ad> ads) {
        if (ads == null || ads.isEmpty())
            return;

        AppLogger.log("[AdPlayer] Queueing " + ads.size() + " ads");

        List<Ad> playableAds = new ArrayList<>();

        for (Ad ad : ads) {
            if (AdDownloadManager.isAdDownloaded(ad)) {
                playableAds.add(ad);
                AppLogger.log("[AdPlayer] Ad queued (local): " + ad.getCampaignName());
            } else if (NetworkMonitor.getInstance().isOnline()) {
                playableAds.add(ad);
                AppLogger.log("[AdPlayer] Ad queued (stream): " + ad.getCampaignName());
            } else {
                AppLogger.log("[AdPlayer] Skipping ad (offline + not downloaded): " + ad.getCampaignName());
            }
        }

        if (playableAds.isEmpty())
            return;

        AppLogger.log("[AdPlayer] Queueing " + playableAds.size() + " playable ads");
        List<Ad> shuffled = new ArrayList<>(playableAds);
        Collections.shuffle(shuffled);
        adQueue.addAll(shuffled);

        if (!isPlayingAd) {
            isPlayingAd = true;
            songPausedForAds = false;
            playNextAd();
        }
    }

    private void playNextAd() {
        executor.submit(() -> {
            while (isPlayingAd && !adQueue.isEmpty()) {
                Ad nextAd = adQueue.poll();
                if (nextAd == null) break;
                try {
                    playAdInternal(nextAd);
                } catch (Exception e) {
                    AppLogger.log("[AdPlayer] Error: " + e.getMessage());
                    listener.onPlaybackError(e);
                }
            }
            AppLogger.log("[AdPlayer] Queue empty, resuming song");
            isPlayingAd = false;
            songPausedForAds = false;
            resumeSong();
        });
    }

    public long getSavedSongTime() {
        return savedSongTime;
    }

    private void playAdInternal(Ad ad) throws Exception {
        AppLogger.log("[AdPlayer] Preparing ad: " + ad.getCampaignName());

        if (!songPausedForAds) {
            // Step 1: Save current song state
            final long[] timeRef = { 0L };
            final int[] volRef = { 100 };
            CountDownLatch stateLatch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    timeRef[0] = vlcPlayer.status().time();
                } catch (Exception ignored) {
                }
                try {
                    volRef[0] = vlcPlayer.audio().volume();
                } catch (Exception ignored) {
                }
                stateLatch.countDown();
            });
            try {
                stateLatch.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                AppLogger.log("[AdPlayer] State fetch interrupted");
            }
            savedSongTime = timeRef[0];
            int originalVol = volRef[0];
            try {
                int steps = 20;
                for (int i = 0; i < steps; i++) {
                    if (!isPlayingAd)
                        break;
                    int currentVol = (int) (originalVol * (1.0 - (double) i / steps));
                    try {
                        vlcPlayer.audio().setVolume(currentVol);
                    } catch (Exception ignored) {
                    }
                    Thread.sleep(100);
                }
                try {
                    vlcPlayer.audio().setVolume(0);
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
            }

            // Step 2: Stop current song
            Platform.runLater(() -> {
                try {
                    savedSongTime = vlcPlayer.status().time();
                    AppLogger.log("[AdPlayer] Saving song position: " + savedSongTime);
                    vlcPlayer.controls().pause();
                    listener.onSongPaused("Ad starting");
                    
                    int targetAdVol = (adVolumeProvider != null) ? adVolumeProvider.get() : originalVol;
                    AppLogger.log("[AdPlayer] Setting ad volume to: " + targetAdVol);
                    vlcPlayer.audio().setVolume(targetAdVol);
                } catch (Exception ignored) {
                }
            });
            Thread.sleep(600);
            songPausedForAds = true;
        }

        // Step 3: Loop over ad audios
        if (ad.getAdAudios() != null && !ad.getAdAudios().isEmpty()) {
            for (com.musicplayer.scamusica.model.AdAudio adAudio : ad.getAdAudios()) {
                String adUrl = buildAdUrl(adAudio);
                if (adUrl == null) {
                    AppLogger.log("[AdPlayer] Invalid ad audio URL, skipping");
                    continue;
                }

                AppLogger.log("[AdPlayer] Playing ad from URL: " + adUrl);

                CountDownLatch latch = new CountDownLatch(1);
                final MediaPlayerEventAdapter[] adListener = new MediaPlayerEventAdapter[1];

                Platform.runLater(() -> {
                    try {
                        listener.onAdPlaybackStarted(ad, adAudio);

                        adListener[0] = new MediaPlayerEventAdapter() {
                            @Override
                            public void finished(MediaPlayer mediaPlayer) {
                                AppLogger.log("[AdPlayer] Ad audio finished");
                                latch.countDown();
                            }

                            @Override
                            public void error(MediaPlayer mediaPlayer) {
                                AppLogger.log("[AdPlayer] Ad audio error");
                                try {
                                    LogSyncService.getInstance().addErrorLog(
                                            "VLC Ad Playback Error", "AdPlayer (Campaign: " + ad.getCampaignName() + ")");
                                } catch (Exception ex) {
                                    AppLogger.log("[AdPlayer] Error logging failed: " + ex.getMessage());
                                }
                                latch.countDown();
                            }

                            private volatile boolean started = false;

                            @Override
                            public void playing(MediaPlayer mediaPlayer) {
                                started = true;
                            }

                            @Override
                            public void stopped(MediaPlayer mediaPlayer) {
                                if (started) {
                                    AppLogger.log("[AdPlayer] Ad audio stopped/interrupted");
                                    latch.countDown();
                                }
                            }
                        };
                        vlcPlayer.events().addMediaPlayerEventListener(adListener[0]);

                        AppLogger.log("[AdPlayer] STARTING ACTUAL VLC PLAY");
                        boolean result = vlcPlayer.media().play(adUrl);
                        AppLogger.log("[AdPlayer] VLC PLAY RESULT = " + result);
                        
                        if (adVolumeProvider != null) {
                            int targetAdVol = adVolumeProvider.get();
                            vlcPlayer.audio().setVolume(targetAdVol);
                            AppLogger.log("[AdPlayer] Re-applying ad volume: " + targetAdVol);
                        }

                    } catch (Exception e) {
                        AppLogger.log("[AdPlayer] Failed to start ad audio: " + e.getMessage());
                        latch.countDown();
                    }
                });

                boolean finished = latch.await(10, TimeUnit.MINUTES);
                AppLogger.log("[AdPlayer] Ad latch released, finished=" + finished);

                CountDownLatch cleanupLatch = new CountDownLatch(1);
                Platform.runLater(() -> {
                    if (adListener[0] != null) {
                        try {
                            vlcPlayer.events().removeMediaPlayerEventListener(adListener[0]);
                            AppLogger.log("[AdPlayer] Ad event listener removed safely");
                        } catch (Exception ignored) {
                        }
                    }
                    cleanupLatch.countDown();
                });

                try {
                    cleanupLatch.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }

                Thread.sleep(300); // Minor delay between consecutive audios
            }
        } else {
            AppLogger.log("[AdPlayer] No audios found for ad: " + ad.getCampaignName());
        }

        // Step 6: Ad done, notify
        Platform.runLater(() -> listener.onAdPlaybackFinished(ad));
        // Note: Loop handles next ad
    }

    private void resumeSong() {
        Platform.runLater(() -> {
            try {
                listener.onSongResumed();
            } catch (Exception e) {
                AppLogger.log("[AdPlayer] Resume error: " + e.getMessage());
            }
        });
    }

    private String buildAdUrl(com.musicplayer.scamusica.model.AdAudio adAudio) {
        if (adAudio == null)
            return null;

        File localFile = AdDownloadManager.getLocalAdFile(adAudio);
        if (localFile != null && localFile.exists() && localFile.length() > 1024) {
            AppLogger.log("[AdPlayer] Playing ad from local file: " + localFile.getAbsolutePath());
            return localFile.getAbsolutePath();
        }

        if (!NetworkMonitor.getInstance().isOnline()) {
            AppLogger.log("[AdPlayer] Ad not downloaded and offline, skipping: ad-audio-" + adAudio.getId());
            return null;
        }

        String audioFile = adAudio.getAudioFile();
        if (audioFile == null || audioFile.isEmpty())
            return null;

        if (audioFile.startsWith("http://") || audioFile.startsWith("https://")) {
            return audioFile;
        }

        String encoded = audioFile
                .replace(" ", "%20")
                .replace("(", "%28")
                .replace(")", "%29")
                .replace("[", "%5B")
                .replace("]", "%5D");
        if (!encoded.startsWith("/")) {
            encoded = "/" + encoded;
        }

        return Utility.FILEPATH_BASE_URL.get() + encoded;
    }

    public boolean isPlayingAd() {
        return isPlayingAd;
    }

    public void clearQueue() {
        adQueue.clear();
        AppLogger.log("[AdPlayer] Queue cleared");
    }

    public void stop() {
        clearQueue();
        isPlayingAd = false;
        executor.shutdownNow();
        AppLogger.log("[AdPlayer] Stopped");
    }
}