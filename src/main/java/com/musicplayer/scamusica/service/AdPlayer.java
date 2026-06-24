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
        void onAdPlaybackStarted(Ad ad);

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

    public AdPlayer(MediaPlayer vlcPlayer, AdPlaybackListener listener) {
        this.vlcPlayer = vlcPlayer;
        this.listener = listener;
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
        Ad nextAd = adQueue.poll();
        if (nextAd == null) {
            AppLogger.log("[AdPlayer] Queue empty, resuming song");
            isPlayingAd = false;
            songPausedForAds = false;
            resumeSong();
            return;
        }

        executor.submit(() -> {
            try {
                playAdInternal(nextAd);
            } catch (Exception e) {
                AppLogger.log("[AdPlayer] Error: " + e.getMessage());
                listener.onPlaybackError(e);
                playNextAd();
            }
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
                    Platform.runLater(() -> {
                        try {
                            vlcPlayer.audio().setVolume(currentVol);
                        } catch (Exception ignored) {
                        }
                    });
                    Thread.sleep(100);
                }
                Platform.runLater(() -> {
                    try {
                        vlcPlayer.audio().setVolume(0);
                    } catch (Exception ignored) {
                    }
                });
            } catch (Exception e) {
            }

            // Step 2: Stop current song
            Platform.runLater(() -> {
                try {
                    savedSongTime = vlcPlayer.status().time();
                    AppLogger.log("[AdPlayer] Saving song position: " + savedSongTime);
                    vlcPlayer.controls().pause();
                    listener.onSongPaused("Ad starting");
                    vlcPlayer.audio().setVolume(originalVol);
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
                        listener.onAdPlaybackStarted(ad);

                        adListener[0] = new MediaPlayerEventAdapter() {
                            @Override
                            public void finished(MediaPlayer mediaPlayer) {
                                AppLogger.log("[AdPlayer] Ad audio finished");
                                latch.countDown();
                            }

                            @Override
                            public void error(MediaPlayer mediaPlayer) {
                                AppLogger.log("[AdPlayer] Ad audio error");
                                latch.countDown();
                            }
                        };
                        vlcPlayer.events().addMediaPlayerEventListener(adListener[0]);

                        AppLogger.log("[AdPlayer] STARTING ACTUAL VLC PLAY");
                        boolean result = vlcPlayer.media().play(adUrl);
                        AppLogger.log("[AdPlayer] VLC PLAY RESULT = " + result);

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

        // Step 7: Play next ad or resume song
        playNextAd();
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

        return Utility.BASE_URL.get() + encoded;
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