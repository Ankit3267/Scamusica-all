package com.musicplayer.scamusica.controller;

import com.musicplayer.scamusica.manager.LanguageManager;
import com.musicplayer.scamusica.model.Ad;
import com.musicplayer.scamusica.model.PlaylistTrack;
import com.musicplayer.scamusica.model.VolumeSchedule;
import com.musicplayer.scamusica.model.VolumeSettings;
import com.musicplayer.scamusica.service.*;
import com.musicplayer.scamusica.ui.*;

import com.musicplayer.scamusica.util.AppLogger;
import com.musicplayer.scamusica.util.CryptoUtil;
import com.musicplayer.scamusica.util.PlaybackHistoryLogger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;

import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.component.AudioPlayerComponent;

import javax.crypto.CipherInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;
import javafx.scene.Node;

import com.musicplayer.scamusica.util.OfflineCache;

public class PlayerController extends Application {

    private Label globalAlbumHeading;
    private Label globalTitleLabel;

    private Slider globalProgressSlider;
    private HBox globalControlsWrapper;
    private HBox globalBottomBar;
    private Label globalLeftTime;
    private Label globalRightTime;
    private Label globalDownloadLabel;

    private MediaPlayer vlcPlayer;
    private AudioPlayerComponent vlcPlayerComponent;
    private boolean vlcHandlersAttached = false;
    private boolean userPaused = false;

    private MediaPlayerEventAdapter currentVlcListener = null;
    private File currentTempFile = null;
    private Thread queueWorkerThread = null;

    private final List<String> playlistMaster = new ArrayList<>();
    private final javafx.collections.ObservableList<String> playlistViewItems = FXCollections.observableArrayList();
    private final String[] playlistCurrent = new String[1];

    private final PlayerSidebar sidebarUtil = new PlayerSidebar();
    private final PlayerHeader headerUtil = new PlayerHeader();
    private final PlayerDropdown dropdownUtil = new PlayerDropdown();
    private final PlayerControls controlsUtil = new PlayerControls();
    private final PlayerAlbum albumUtil = new PlayerAlbum();

    private VBox playlistDropdownCard;
    private HBox playlistPill;

    private String currentPlaylistName;

    private static final String PREF_VOLUME = "player_volume";
    private final Preferences prefs = Preferences.userNodeForPackage(PlayerController.class);

    private final List<PlaylistTrack> playQueue = Collections.synchronizedList(new ArrayList<>());
    private int currentTrackIndex = 0;

    private volatile boolean isFirstTrackStarted = false;
    private int consecutiveErrorCount = 0;

    private ImageView albumImageView;
    private String currentAlbumImgUrl = null;

    private final List<String> tempPlaylist = Arrays.asList(
            "Secuencias-Estilos-playlist",
            "Sequence 1",
            "Sequence 2",
            "Sequence 3",
            "Playlist Custom 1",
            "Playlist Custom 2",
            "Playlist Custom 3");

    private DownloadManager downloadManager;

    private final AtomicInteger totalDownloadedCounter = new AtomicInteger(0);

    private volatile int currentGenreTotalFiles = 0;
    private final AtomicInteger currentGenreDownloadedCount = new AtomicInteger(0);

    private final PlaylistApiService apiService = new PlaylistApiService();
    private volatile double currentFileProgressFraction = 0.0;

    private ScheduledExecutorService schedular;
    private BlockingQueue<Runnable> operationQueue = new LinkedBlockingQueue<>();
    private java.util.concurrent.ExecutorService asyncExecutor = java.util.concurrent.Executors.newFixedThreadPool(4,
            r -> {
                Thread t = new Thread(r, "AsyncExecutor-Thread");
                t.setDaemon(true);
                return t;
            });
    private volatile boolean running = true;
    private List<Integer> lastServerIds = new ArrayList<>();

    private AdScheduler adScheduler;
    private AdPlayer adPlayer;
    private List<Ad> allAds = new ArrayList<>();

    private AudioCallbackHandler audioCallbackHandler;
    private LedVuMeter ledVuMeter;
    
    private VolumeSettings currentVolumeSettings;
    private Integer currentScheduleId = -999;

    @Override
    public void start(Stage primaryStage) {

        // === TEMP CLEANUP ===
        try {
            File tempDir = new File(System.getProperty("user.home")
                    + File.separator + ".scamusica"
                    + File.separator + "temp");
            if (tempDir.exists() && tempDir.isDirectory()) {
                File[] files = tempDir.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.getName().startsWith("play_") && f.getName().endsWith(".mp3")) {
                            f.delete();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }

        // === NETWORK MONITOR START ===
        NetworkMonitor.getInstance().start();
        AppLogger.log("[APP] Player started");

        // Schedule non-critical services to start later to reduce startup contention
        ScheduledExecutorService startupDelayer = Executors.newSingleThreadScheduledExecutor();
        startupDelayer.schedule(() -> MemoryWatchdog.getInstance().start(), 60, TimeUnit.SECONDS);
        startupDelayer.schedule(() -> HeartbeatService.getInstance().start(), 30, TimeUnit.SECONDS);
        startupDelayer.schedule(() -> LogSyncService.getInstance().start(), 30, TimeUnit.SECONDS);

        String appDir = System.getProperty("user.dir");
        String vlcPath = appDir + File.separator + "vlc";
        System.setProperty("jna.library.path", vlcPath);

        // Pre-fetch API data concurrently
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                apiService.prefetchAll();
            } catch (Exception ignored) {
            }
        });

        vlcPlayerComponent = new AudioPlayerComponent();
        vlcPlayer = vlcPlayerComponent.mediaPlayer();

        // Delay AdSystem init slightly to allow UI to render first
        startupDelayer.schedule(this::initializeAdSystem, 5, TimeUnit.SECONDS);

        Button headphonesButton = sidebarUtil.createIconButton("fas-headphones");
        List<Button> sidebarButtons = Arrays.asList(headphonesButton);
        sidebarUtil.addSidebarLogic(sidebarButtons, headphonesButton);
        VBox sidebarTop = sidebarUtil.createSidebarTop(headphonesButton);
        FontIcon settingsIcon = sidebarUtil.createSettingsIcon(primaryStage, () -> {
            running = false;
            if (vlcPlayer != null) {
                try {
                    vlcPlayer.controls().stop();
                } catch (Exception ignored) {
                }
            }
            if (queueWorkerThread != null) {
                queueWorkerThread.interrupt();
            }
            if (schedular != null) {
                schedular.shutdownNow();
            }
            if (adScheduler != null) {
                adScheduler.stop();
            }
            if (adPlayer != null) {
                adPlayer.stop();
            }
            if (downloadManager != null) {
                try {
                    downloadManager.stop();
                } catch (Exception ignored) {
                }
            }
            if (asyncExecutor != null) {
                asyncExecutor.shutdownNow();
            }
            MemoryWatchdog.getInstance().stop();
            HeartbeatService.getInstance().stop();
            LogSyncService.getInstance().stop();
        });
        VBox sidebar = sidebarUtil.createSidebar(sidebarTop, settingsIcon);

        HBox leftMeta = headerUtil.createLeftMeta();
        ImageView logoView = headerUtil.createLogoView(getClass());
        HBox rightMeta = headerUtil.createRightMeta();
        ComboBox<LangItem> languageBox = LanguageManager.createLanguageSelector();
        languageBox.getStyleClass().add("language-selector");
        rightMeta.getChildren().add(languageBox);
        BorderPane header = headerUtil.createHeader(leftMeta, logoView, rightMeta);

        globalAlbumHeading = albumUtil.createAlbumHeading();
        Label albumHeading = globalAlbumHeading;

        Label currentStyleLabel = new Label();
        currentStyleLabel.textProperty().bind(
                LanguageManager.createStringBinding("label.currentStyle"));

        currentStyleLabel.getStyleClass().add("section-heading-styles");

        ImageView img = albumUtil.createAlbumImage(getClass());
        albumImageView = img;
        albumUtil.applyClip(img);
        HBox songsBox = albumUtil.createSongsBox();

        VBox leftAlbumVBox = albumUtil.createLeftAlbumVBox(albumHeading, img, songsBox);
        leftAlbumVBox.getChildren().add(0, currentStyleLabel);

        MemoryWatchdog.getInstance().registerCleanupCallback(() -> {
            // Hint to JVM that large image buffers can be collected
            Platform.runLater(() -> {
                if (albumImageView != null && albumImageView.getImage() != null) {
                    albumImageView.getImage().cancel();
                }
            });
        });

        recomputeGlobalCountAndUpdateUI();

        List<String> tempList;
        try {
            PlaylistApiService playlistApiService = apiService;
            
            try {
                currentVolumeSettings = playlistApiService.fetchVolumeSettings();
            } catch (Exception e) {
                AppLogger.log("[PlayerController] Failed to fetch volume settings on startup: " + e.getMessage());
            }

            List<String> apiPlaylists = playlistApiService.fetchPlaylistTitles();
            if (apiPlaylists != null && !apiPlaylists.isEmpty()) {
                tempList = new ArrayList<>(apiPlaylists);
            } else {
                tempList = tempPlaylist;
                AppLogger.log("[PlayerController] API returned empty list, using fallback playlists.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            AppLogger.log("[PlayerController] API failed, checking offline cache...");
            List<String> cached = OfflineCache.loadPlaylistTitles();
            if (!cached.isEmpty()) {
                tempList = cached;
                AppLogger.log("[PlayerController] Using cached playlists: " + cached.size());
            } else {
                tempList = tempPlaylist; // hardcoded fallback
                AppLogger.log("[PlayerController] No cache, using hardcoded fallback.");
            }
        }

        playlistMaster.addAll(tempList);
        playlistCurrent[0] = playlistMaster.get(0);
        playlistViewItems.setAll(
                playlistMaster.stream()
                        .filter(s -> !s.equals(playlistCurrent[0]))
                        .collect(Collectors.toList()));

        playlistPill = dropdownUtil.createPlaylistPill(playlistCurrent[0]);
        playlistDropdownCard = dropdownUtil.createDropdownCard(playlistViewItems, playlistCurrent, playlistMaster,
                playlistPill);

        HBox playlistHeaderBox = dropdownUtil.createPlaylistHeaderBox(playlistPill);

        // New Code
        Label sequencesLabel = new Label();
        sequencesLabel.textProperty().bind(
                LanguageManager.createStringBinding("label.sequencesTitle"));
        sequencesLabel.getStyleClass().add("section-heading-sequences");

        // New Code
        try {
            audioCallbackHandler = new AudioCallbackHandler();
        } catch (Exception e) {
            e.printStackTrace();
            audioCallbackHandler = null;
        }

        VBox rightColumn = new VBox(4);
        rightColumn.getChildren().addAll(sequencesLabel, playlistHeaderBox);

        HBox rightWrapper = new HBox(rightColumn);
        rightWrapper.setAlignment(Pos.TOP_RIGHT);

        globalTitleLabel = headerUtil.createPlayerTitle();
        Label titleCentered = globalTitleLabel;
        VBox centerContainer = headerUtil.createCenterContainer(titleCentered);
        HBox topRow = albumUtil.createTopRow(leftAlbumVBox, centerContainer, rightWrapper);

        Slider progressSlider = controlsUtil.createProgressSlider();
        globalProgressSlider = progressSlider;

        Label leftTime = controlsUtil.createTimeLabel(false);
        globalLeftTime = leftTime;
        Label rightTime = controlsUtil.createTimeLabel(true);
        globalRightTime = rightTime;
        HBox timesRow = controlsUtil.createTimesRow(leftTime, rightTime);
        HBox progressRow = controlsUtil.createProgressRow(progressSlider);
        // Original line — touch mat karo
        // LedVuMeter initialize karo PEHLE sliderContainer ke
        ledVuMeter = new LedVuMeter();
        if (audioCallbackHandler != null) {
            ledVuMeter.setAudioCallbackHandler(audioCallbackHandler);
        }

        // Original line — touch mat karo
        VBox sliderContainer = controlsUtil.createSliderContainer(titleCentered, timesRow, progressRow);
        // Ab sliderContainer ka pehla child (title row) StackPane mein wrap karo
        try {
            // sliderContainer ka index 0 = title label node
            Node titleNode = sliderContainer.getChildren().get(0);
            sliderContainer.getChildren().remove(0);

            StackPane titleWithVu = new StackPane();
            titleWithVu.getChildren().addAll(titleNode, ledVuMeter);
            StackPane.setAlignment(titleNode, Pos.CENTER);
            StackPane.setAlignment(ledVuMeter, Pos.CENTER_RIGHT);
            ledVuMeter.setTranslateY(2);
            ledVuMeter.setTranslateX(-86); // right side se thoda andar

            sliderContainer.getChildren().add(0, titleWithVu);
        } catch (Exception e) {
            e.printStackTrace();
        }
        HBox controlsWrapper = controlsUtil.createControls(progressSlider, playlistPill);
        globalControlsWrapper = controlsWrapper;

        HBox bottomBar = controlsUtil.createBottomBar();
        globalBottomBar = bottomBar;

        Label downloadLabel = controlsUtil.getDownloadLabel(bottomBar);
        globalDownloadLabel = downloadLabel;

        if (downloadLabel != null) {
            bottomBar.getChildren().remove(downloadLabel);
            downloadLabel.setPadding(new javafx.geometry.Insets(6, 0, 0, 6));
            downloadLabel.getStyleClass().remove("download-label");
            downloadLabel.getStyleClass().add("download-label-left");
            leftAlbumVBox.getChildren().add(downloadLabel);
        }

        StackPane combinedBottom = new StackPane();
        bottomBar.setPickOnBounds(false);
        controlsWrapper.setPickOnBounds(false);
        combinedBottom.getChildren().addAll(bottomBar, controlsWrapper);
        StackPane.setAlignment(bottomBar, Pos.CENTER);

        VBox contentVBox = new VBox();
        contentVBox.setSpacing(0);
        contentVBox.getChildren().addAll(header, topRow, sliderContainer, combinedBottom);
        VBox.setVgrow(topRow, Priority.ALWAYS);

        BorderPane mainPane = new BorderPane();
        mainPane.getStyleClass().add("root");
        mainPane.setLeft(sidebar);
        mainPane.setCenter(contentVBox);

        Pane rootOverlay = new Pane();
        mainPane.setPrefSize(1200, 720);
        mainPane.prefWidthProperty().bind(rootOverlay.widthProperty());
        mainPane.prefHeightProperty().bind(rootOverlay.heightProperty());

        rootOverlay.getChildren().addAll(mainPane, playlistDropdownCard);

        Scene scene = new Scene(rootOverlay, 1200, 720);
        scene.getStylesheets().add(getClass().getResource("/styles/style.css").toExternalForm());
        playlistDropdownCard.getStylesheets().add(scene.getStylesheets().get(0));

        headerUtil.loadFonts(getClass());
        scene.getRoot().setStyle("-fx-font-family: 'Poppins', 'Noto Sans', 'Noto Sans JP', 'Noto Sans SC', 'Noto Sans" +
                " Arabic', 'Noto Sans Devanagari';");
        albumUtil.bindImageSize(img, scene, progressRow, titleCentered);

        dropdownUtil.setupDropdownHandlers(
                playlistPill,
                playlistDropdownCard,
                playlistMaster,
                playlistViewItems,
                playlistCurrent,
                scene,
                rootOverlay,
                img,
                this::hideDropdown,
                selectedPlaylistName -> {
                    currentPlaylistName = selectedPlaylistName;
                    try {
                        loadPlaylistAndStart(
                                selectedPlaylistName,
                                albumHeading,
                                titleCentered,
                                progressSlider,
                                leftTime,
                                rightTime,
                                controlsWrapper,
                                bottomBar,
                                downloadLabel,
                                true);
                    } catch (URISyntaxException e) {
                        throw new RuntimeException(e);
                    }
                });

        primaryStage.titleProperty().bind(
                LanguageManager.createStringBinding("app.title"));
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1150);
        primaryStage.setMinHeight(650);

        primaryStage.setOnCloseRequest(event -> {
            AppLogger.log("[APP] Closing application");
            AppLogger.close();

            running = false;

            if (queueWorkerThread != null) {
                queueWorkerThread.interrupt();
            }

            if (schedular != null) {
                schedular.shutdownNow();
            }

            if (vlcPlayer != null) {
                try {
                    vlcPlayer.controls().stop();
                } catch (Exception ignored) {
                }
            }

            if (downloadManager != null) {
                try {
                    downloadManager.stop();
                } catch (Exception ignored) {
                }
            }

            if (adScheduler != null) {
                adScheduler.stop();
            }
            if (adPlayer != null) {
                adPlayer.stop();
            }

            NetworkMonitor.getInstance().stop();
            MemoryWatchdog.getInstance().stop();
            HeartbeatService.getInstance().stop();
            LogSyncService.getInstance().stop();

            // ✅ VU Meter cleanup
            if (ledVuMeter != null) {
                ledVuMeter.stop();
            }
            if (audioCallbackHandler != null) {
                audioCallbackHandler.close();
            }

            Platform.exit();

            System.exit(0);

        });

        primaryStage.show();

        Platform.runLater(() -> {
            controlsUtil.setupSliderFill(progressSlider);

            Slider volumeSlider = controlsUtil.getVolumeSlider(bottomBar);
            controlsUtil.setupVolumeSliderFill(controlsUtil.getVolumeSlider(bottomBar));

            double savedVolume = prefs.getDouble(PREF_VOLUME, 85.0);
            volumeSlider.setValue(savedVolume);
            vlcPlayer.audio().setVolume((int) savedVolume);

            volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                prefs.putDouble(PREF_VOLUME, newVal.doubleValue());
                vlcPlayer.audio().setVolume(newVal.intValue());
            });

            progressSlider.setOnMouseReleased(e -> {
                float pos = (float) (progressSlider.getValue() / 100.0);
                vlcPlayer.controls().setPosition(pos);
            });

            currentPlaylistName = playlistCurrent[0];
            try {
                loadPlaylistAndStart(
                        playlistCurrent[0],
                        albumHeading,
                        titleCentered,
                        progressSlider,
                        leftTime,
                        rightTime,
                        controlsWrapper,
                        bottomBar,
                        downloadLabel,
                        true);

                setupBigPlayBehaviour(
                        albumHeading,
                        titleCentered,
                        controlsWrapper,
                        progressSlider,
                        leftTime,
                        rightTime,
                        bottomBar,
                        downloadLabel);

            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

            Button forwardBtn = null;
            HBox forwardBox = null;
            try {
                forwardBox = (HBox) controlsWrapper.getChildren().get(2);
                forwardBtn = (Button) forwardBox.getChildren().get(0);
            } catch (Exception ex) {
                forwardBtn = (Button) controlsWrapper.lookup("#forwardButton");
            }

            if (forwardBtn != null) {
                final Button finalForwardBtn = forwardBtn;
                javafx.event.EventHandler<javafx.scene.input.MouseEvent> fwdHandler = e -> {
                    e.consume();
                    finalForwardBtn.getStyleClass().add("control-active");
                    Platform.runLater(() -> finalForwardBtn.getStyleClass().remove("control-active"));

                    try {
                        playNextTrack(
                                albumHeading,
                                titleCentered,
                                progressSlider,
                                leftTime,
                                rightTime,
                                controlsWrapper,
                                bottomBar,
                                downloadLabel);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                };

                finalForwardBtn.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, fwdHandler);
                if (forwardBox != null) {
                    forwardBox.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, fwdHandler);
                }
            }
        });

        // 🔥 START QUEUE WORKER
        queueWorkerThread = new Thread(() -> {
            while (running) {
                try {
                    Runnable task = operationQueue.take();
                    task.run();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        queueWorkerThread.setDaemon(true);
        queueWorkerThread.start();

        // 🔥 START SCHEDULER
        schedular = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SyncScheduler");
            t.setDaemon(true);
            return t;
        });

        schedular.scheduleAtFixedRate(() -> {
            operationQueue.add(() -> {
                if (!NetworkMonitor.getInstance().isOnline()) {
                    AppLogger.log("[SYNC] Offline — skipping server sync");
                    return;
                }
                try {
                    syncWithServer();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
//        }, 15, 30, java.util.concurrent.TimeUnit.SECONDS); // Changed from 300s to 30s for testing
    }, 30, 300, java.util.concurrent.TimeUnit.SECONDS); // 300s = 5 minutes
        schedular.scheduleAtFixedRate(() -> {
            try {
                checkAndApplyVolumeSchedule();
            } catch (Exception e) {
                AppLogger.log("[Volume] Schedule check failed: " + e.getMessage());
            }
        }, 0, 5, java.util.concurrent.TimeUnit.SECONDS);

        schedular.scheduleAtFixedRate(() -> {
            try {
                File tempDir = new File(System.getProperty("user.home")
                        + File.separator + ".scamusica"
                        + File.separator + "temp");
                if (tempDir.exists() && tempDir.isDirectory()) {
                    File[] files = tempDir.listFiles();
                    if (files != null) {
                        long now = System.currentTimeMillis();
                        for (File f : files) {
                            if (f.getName().startsWith("play_") && f.getName().endsWith(".mp3")) {
                                if (now - f.lastModified() > 15 * 60 * 1000) {
                                    if (f.delete()) {
                                        AppLogger.log("[TEMP] Periodic sweep deleted: " + f.getName());
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                AppLogger.log("[TEMP] Sweep error: " + e.getMessage());
            }
        }, 2, 2, java.util.concurrent.TimeUnit.HOURS);
    }

    private void syncWithServer() {
        AppLogger.log("[SYNC] Checking server updates for playlist: " + currentPlaylistName);

        if (!NetworkMonitor.getInstance().isOnline()) {
            AppLogger.log("[SYNC] Offline — aborting sync");
            return;
        }

        try {
            VolumeSettings newSettings = apiService.fetchVolumeSettings();
            if (newSettings != null) {
                currentVolumeSettings = newSettings;
                checkAndApplyVolumeSchedule();
            }
        } catch (Exception e) {
            AppLogger.log("[SYNC] Failed to sync volume settings: " + e.getMessage());
        }

        try {
            syncAdsFromServer();
        } catch (Exception e) {
            AppLogger.log("[SYNC] Ad sync failed: " + e.getMessage());
        }

        // ✅ Playlist titles sync with Auto-Switch
        try {
            List<String> fetchedTitles = apiService.fetchPlaylistTitles();
            List<String> serverTitles;

            if (fetchedTitles == null || fetchedTitles.isEmpty()) {
                serverTitles = new ArrayList<>(java.util.Arrays.asList("Default"));
                AppLogger.log("[SYNC] API returned empty titles, falling back to Default sequence.");
            } else {
                serverTitles = fetchedTitles;
            }

            if (serverTitles != null && !serverTitles.isEmpty()) {
                List<String> newSequences = serverTitles.stream()
                        .filter(title -> !playlistMaster.contains(title))
                        .collect(Collectors.toList());

                boolean sequenceSwitched = false;
                String nextSequence = null;

                if (!newSequences.isEmpty()) {
                    nextSequence = newSequences.get(0);
                    AppLogger.log("[SYNC] New sequence(s) added detected! Switching immediately to: " + nextSequence);
                    sequenceSwitched = true;
                } else if (currentPlaylistName != null && !serverTitles.contains(currentPlaylistName)) {
                    if (!serverTitles.isEmpty()) {
                        nextSequence = serverTitles.get(0);
                        AppLogger.log("[SYNC] Current sequence removed detected! Switching to fallback: " + nextSequence);
                        sequenceSwitched = true;
                    }
                }

                if (!serverTitles.equals(playlistMaster)) {
                    playlistMaster.clear();
                    playlistMaster.addAll(serverTitles);
                    AppLogger.log("[SYNC] Playlist titles updated: " + serverTitles.size());
                }

                // ✅ Cleanup orphaned sequence folders (runs on background thread)
                final List<String> titlesForCleanup = new ArrayList<>(serverTitles);
                asyncExecutor.submit(() -> {
                    try {
                        cleanupOrphanedSequences(titlesForCleanup);
                    } catch (Exception e) {
                        AppLogger.log("[SYNC] Orphaned sequence cleanup failed: " + e.getMessage());
                    }
                });

                if (sequenceSwitched && nextSequence != null) {
                    currentPlaylistName = nextSequence;
                    playlistCurrent[0] = nextSequence;
                    final String finalNextSeq = nextSequence;

                    Platform.runLater(() -> {
                        try {
                            if (playlistPill != null) {
                                Label textLabel = (Label) playlistPill.getChildren().get(0);
                                textLabel.setText(finalNextSeq);
                            }

                            playQueue.clear();

                            loadPlaylistAndStart(
                                    finalNextSeq,
                                    globalAlbumHeading,
                                    globalTitleLabel,
                                    globalProgressSlider,
                                    globalLeftTime,
                                    globalRightTime,
                                    globalControlsWrapper,
                                    globalBottomBar,
                                    globalDownloadLabel,
                                    true
                            );

                            playlistViewItems.setAll(
                                    playlistMaster.stream()
                                            .filter(s -> !s.equals(playlistCurrent[0]))
                                            .collect(Collectors.toList()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    
                    // Sequence switched, so we skip syncing tracks for the old sequence!
                    return;
                }
                
                Platform.runLater(() -> {
                    try {
                        playlistViewItems.setAll(
                                playlistMaster.stream()
                                        .filter(s -> !s.equals(playlistCurrent[0]))
                                        .collect(Collectors.toList()));
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (Exception e) {
            AppLogger.log("[SYNC] Playlist title sync failed: " + e.getMessage());
        }

        // ============================================
        // TRACK SYNC FOR CURRENT SEQUENCE
        // ============================================
        try {
            String currentPlaylist = currentPlaylistName;
            if (currentPlaylist == null)
                return;

            List<PlaylistTrack> serverTracks = apiService.fetchTracksForGenre(currentPlaylist);
            AppLogger.log("[SYNC] Server tracks count: " + (serverTracks != null ? serverTracks.size() : 0));

            if (serverTracks == null)
                return;

            List<Integer> serverIds = serverTracks.stream()
                    .map(PlaylistTrack::getId)
                    .collect(Collectors.toList());

            if (serverIds.equals(lastServerIds)) {
                AppLogger.log("[SYNC] No changes detected");
                return; // no change
            }

            lastServerIds = new ArrayList<>(serverIds);

            List<Integer> localIds;
            synchronized (playQueue) {
                localIds = playQueue.stream()
                        .map(PlaylistTrack::getId)
                        .collect(Collectors.toList());
            }

            java.util.Set<Integer> toAdd = new java.util.HashSet<>(serverIds);
            toAdd.removeAll(localIds);

            java.util.Set<Integer> toDelete = new java.util.HashSet<>(localIds);
            toDelete.removeAll(serverIds);

            AppLogger.log("[SYNC] To Add: " + toAdd);
            AppLogger.log("[SYNC] To Delete: " + toDelete);

            // ✅ ADD
            for (PlaylistTrack t : serverTracks) {
                if (toAdd.contains(t.getId())) {
                    boolean exists;
                    synchronized (playQueue) {
                        exists = playQueue.stream()
                                .anyMatch(x -> x.getId() == t.getId());
                    }

                    if (!exists) {
                        playQueue.add(t);
                    }

                    if (downloadManager != null) {
                        downloadManager.queueDownload(t.getId());
                    }
                }
            }

            // ✅ DELETE
            for (Integer id : toDelete) {

                PlaylistTrack current = null;

                if (currentTrackIndex < playQueue.size()) {
                    current = playQueue.get(currentTrackIndex);
                }

                playQueue.removeIf(track -> track.getId() == id);

                deleteSongFile(id);

                if (current != null && current.getId() == id) {
                    Platform.runLater(() -> {
                        try {
                            playNextTrack(null, null, null, null, null, null, null, null);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }
            }

            // Update Total files count for UI
            try {
                List<Integer> serverDownloadSeq = apiService.fetchDownloadSequenceForGenre(currentPlaylist);
                if (serverDownloadSeq != null) {
                    currentGenreTotalFiles = serverDownloadSeq.size();
                } else {
                    currentGenreTotalFiles = serverTracks.size();
                }
                
                if (globalDownloadLabel != null) {
                    Platform.runLater(() -> {
                        updateGenreDownloadLabel(globalDownloadLabel);
                    });
                }
            } catch (Exception e) {
                AppLogger.log("[SYNC] Failed to update download sequence count: " + e.getMessage());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initializeAdSystem() {
        AppLogger.log("[PlayerController] Initializing Ad System");

        // 1. Create AdPlayer with listeners
        adPlayer = new AdPlayer(vlcPlayer, new AdPlayer.AdPlaybackListener() {
            @Override
            public void onAdPlaybackStarted(Ad ad, com.musicplayer.scamusica.model.AdAudio adAudio) {

                AppLogger.log("[AdPlayer] Ad started: "
                        + ad.getCampaignName());

                // Log ad play event for server sync
                try {
                    LogSyncService.getInstance()
                            .addAdLog(ad.getId(), ad.getCampaignName(), adAudio != null ? adAudio.getAdTitle() : null, adAudio != null ? adAudio.getAudioFile() : null);
                } catch (Exception syncEx) {
                    AppLogger.log("[AdPlayer] Ad log sync failed: " + syncEx.getMessage());
                }

                Platform.runLater(() -> {
                    try {
                        globalTitleLabel.textProperty().unbind();
                        globalTitleLabel.setText("ADVERTISEMENT");
                        globalAlbumHeading.textProperty().unbind();
                        globalAlbumHeading.setText(ad.getCampaignName());

                        if (globalProgressSlider != null) {
                            globalProgressSlider.setDisable(true);
                            globalProgressSlider.setMouseTransparent(true);
                        }
                        if (globalControlsWrapper != null) {
                            globalControlsWrapper.setDisable(true);
                        }

                        setGenreSwitchEnabled(false);

                        // // Ad start hone par saved volume restore karo
                        // double savedVol = prefs.getDouble(PREF_VOLUME, 85.0);
                        // if (vlcPlayer != null) {
                        // vlcPlayer.audio().setVolume((int) savedVol);
                        // }

                        // if (globalBottomBar != null) {
                        //     Slider volumeSlider = controlsUtil.getVolumeSlider(globalBottomBar);
                        //     if (volumeSlider != null) {
                        //         volumeSlider.setDisable(false);
                        //         volumeSlider.setMouseTransparent(false);
                        //     }
                        // }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onAdPlaybackFinished(Ad ad) {

                AppLogger.log("[AdPlayer] Ad finished: "
                        + ad.getCampaignName());

                Platform.runLater(() -> {
                    try {

                        if (!playQueue.isEmpty() && currentTrackIndex < playQueue.size()) {
                            PlaylistTrack track = playQueue.get(currentTrackIndex);
                            globalTitleLabel.textProperty().unbind();
                            globalTitleLabel.setText(track.getTitle());
                            globalAlbumHeading.textProperty().unbind();
                            globalAlbumHeading.setText(currentPlaylistName);
                        }

                        if (globalProgressSlider != null) {
                            globalProgressSlider.setDisable(false);
                            globalProgressSlider.setMouseTransparent(false);
                        }
                        if (globalControlsWrapper != null) {
                            globalControlsWrapper.setDisable(false);
                        }

                        setGenreSwitchEnabled(true);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onSongPaused(String reason) {
                AppLogger.log("[AdPlayer] Song paused: " + reason);
                Platform.runLater(() -> {
                    try {
                        if (globalBottomBar != null && controlsUtil != null) {
                            Slider volumeSlider = controlsUtil.getVolumeSlider(globalBottomBar);
                            if (volumeSlider != null) {
                                double savedPrefVol = prefs.getDouble(PREF_VOLUME, 85.0);
                                volumeSlider.setValue(getCurrentAdVolume());
                                prefs.putDouble(PREF_VOLUME, savedPrefVol);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onSongResumed() {
                AppLogger.log("[AdPlayer] Song resuming after ad");

                Platform.runLater(() -> {
                    try {
                        if (!playQueue.isEmpty() && currentTrackIndex < playQueue.size()) {
                            PlaylistTrack track = playQueue.get(currentTrackIndex);

                            String baseDownloadDir = System.getProperty("user.home")
                                    + File.separator + ".scamusica"
                                    + File.separator + "downloads";

                            String genreFolder = (currentPlaylistName != null)
                                    ? currentPlaylistName.replaceAll("\\s+", "_")
                                    : track.getFolderTitle().replaceAll("\\s+", "_");

                            File encryptedFile = new File(baseDownloadDir
                                    + File.separator + genreFolder,
                                    "song-" + track.getId() + ".dat");

                            if (encryptedFile.exists()) {
                                AppLogger
                                        .log("[AdPlayer] Resuming from local file: " + encryptedFile.getAbsolutePath());
                                asyncExecutor.submit(() -> {
                                    try {
                                        cleanupTempFile();
                                        File tempFile = decryptToTemp(encryptedFile);
                                        synchronized (PlayerController.this) {
                                            currentTempFile = tempFile;
                                        }
                                        Platform.runLater(() -> {
                                            int originalVol = (int) prefs.getDouble(PREF_VOLUME, 85.0);
                                            long savedTime = adPlayer.getSavedSongTime();
                                            String startTimeOpt = ":start-time=" + (savedTime / 1000.0f);

                                            vlcPlayer.audio().setVolume(0);
                                            asyncExecutor.submit(() -> {
                                                try {
                                                    // Force volume 0 for 1.5 seconds without flooding JavaFX thread
                                                    long endTime = System.currentTimeMillis() + 1500;
                                                    while (System.currentTimeMillis() < endTime) {
                                                        vlcPlayer.audio().setVolume(0);
                                                        Thread.sleep(100);
                                                    }
                                                } catch (Exception e) {
                                                }
                                            });

                                            vlcPlayer.media().play(tempFile.getAbsolutePath(), startTimeOpt);
                                            vlcPlayer.audio().setVolume(0);

                                            schedular.schedule(() -> {
                                                asyncExecutor.submit(() -> {
                                                    try {
                                                        vlcPlayer.audio().setVolume(0);
                                                        int steps = 20;
                                                        for (int i = 1; i <= steps; i++) {
                                                            int currentVol = (int) (originalVol * ((double) i / steps));
                                                            vlcPlayer.audio().setVolume(currentVol);
                                                            Thread.sleep(100);
                                                        }
                                                        vlcPlayer.audio().setVolume(originalVol);
                                                        
                                                        Platform.runLater(() -> {
                                                            if (globalBottomBar != null && controlsUtil != null) {
                                                                Slider volumeSlider = controlsUtil.getVolumeSlider(globalBottomBar);
                                                                if (volumeSlider != null) {
                                                                    volumeSlider.setValue(originalVol);
                                                                }
                                                            }
                                                        });
                                                    } catch (Exception e) {
                                                    }
                                                });
                                            }, 1500, TimeUnit.MILLISECONDS);
                                        });
                                    } catch (Exception e) {
                                        e.printStackTrace();
                                    }
                                });
                            } else if (NetworkMonitor.getInstance().isOnline()) {
                                AppLogger.log("[AdPlayer] Resuming from URL: " + track.getUrl());
                                int originalVol = (int) prefs.getDouble(PREF_VOLUME, 85.0);
                                long savedTime = adPlayer.getSavedSongTime();
                                String startTimeOpt = ":start-time=" + (savedTime / 1000.0f);

                                vlcPlayer.audio().setVolume(0);
                                asyncExecutor.submit(() -> {
                                    try {
                                        for (int i = 0; i < 50; i++) {
                                            Platform.runLater(() -> {
                                                try {
                                                    vlcPlayer.audio().setVolume(0);
                                                } catch (Exception ex) {
                                                }
                                            });
                                            Thread.sleep(30);
                                        }
                                    } catch (Exception e) {
                                    }
                                });

                                vlcPlayer.media().play(encodeMediaUrl(track.getUrl()), startTimeOpt);
                                vlcPlayer.audio().setVolume(0);

                                schedular.schedule(() -> {
                                    asyncExecutor.submit(() -> {
                                        try {
                                            Platform.runLater(() -> {
                                                try {
                                                    vlcPlayer.audio().setVolume(0);
                                                } catch (Exception ex) {
                                                }
                                            });
                                            int steps = 20;
                                            for (int i = 1; i <= steps; i++) {
                                                int currentVol = (int) (originalVol * ((double) i / steps));
                                                Platform.runLater(() -> {
                                                    try {
                                                        vlcPlayer.audio().setVolume(currentVol);
                                                    } catch (Exception ex) {
                                                    }
                                                });
                                                Thread.sleep(100);
                                            }
                                            Platform.runLater(() -> {
                                                try {
                                                    vlcPlayer.audio().setVolume(originalVol);
                                                    if (globalBottomBar != null && controlsUtil != null) {
                                                        Slider volumeSlider = controlsUtil.getVolumeSlider(globalBottomBar);
                                                        if (volumeSlider != null) {
                                                            volumeSlider.setValue(originalVol);
                                                        }
                                                    }
                                                } catch (Exception ex) {
                                                }
                                            });
                                        } catch (Exception e) {
                                        }
                                    });
                                }, 1500, TimeUnit.MILLISECONDS);
                            } else {
                                AppLogger.log("[AdPlayer] Cannot resume — offline and no local file. Playing next.");
                                playNextTrack(globalAlbumHeading, globalTitleLabel,
                                        globalProgressSlider, null, null,
                                        globalControlsWrapper, globalBottomBar, null);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void onPlaybackError(Exception ex) {
                AppLogger.log("[AdPlayer] Playback error: " + ex.getMessage());
            }
        });

        adPlayer.setAdVolumeProvider(this::getCurrentAdVolume);

        // 2. Fetch ads from server
        try {
            PlaylistApiService api = apiService;
            allAds = api.fetchAds();

            AppLogger.log("[PlayerController] Total ads = " + allAds.size());

            for (Ad ad : allAds) {
                AppLogger.log("==============");
                AppLogger.log("Campaign: " + ad.getCampaignName());
                AppLogger.log("ScheduleType: " + ad.getScheduleType());
                AppLogger.log("PlayTimes: " + ad.getPlayTimes());
                AppLogger.log("ActiveDays: " + ad.getActiveDays());
            }

            if (allAds != null && !allAds.isEmpty()) {
                OfflineCache.saveAdSchedule(allAds);
                AdDownloadManager.downloadAllAds(allAds);
            }

            if (allAds == null) {
                allAds = new ArrayList<>();
            }
            AppLogger.log("[AdSystem] Loaded " + allAds.size() + " ads");
        } catch (Exception e) {
            AppLogger.log("[AdSystem] Failed to load ads online, trying cache...");
            allAds = OfflineCache.loadAdSchedule();
            AppLogger.log("[AdSystem] Loaded " + allAds.size() + " ads from cache");
        }

        // 3. Create scheduler with listeners
        adScheduler = new AdScheduler(allAds, new AdScheduler.AdScheduleListener() {
            @Override
            public void onAdsReady(List<Ad> ads) {
                AppLogger.log("[AdScheduler] " + ads.size() + " ads due to play");
                // Queue them for playback
                if (adPlayer != null) {
                    adPlayer.queueAds(ads);
                }
            }

            @Override
            public void onScheduleError(Exception ex) {
                AppLogger.log("[AdScheduler] Error: " + ex.getMessage());
            }
        });

        // 4. Start the scheduler
        adScheduler.start();
    }

    private void syncAdsFromServer() throws Exception {

        if (!NetworkMonitor.getInstance().isOnline()) {
            AppLogger.log("[SYNC] Offline — skipping ad sync");
            return;
        }

        PlaylistApiService api = apiService;
        List<Ad> serverAds = api.fetchAds();

        if (serverAds != null && !serverAds.isEmpty()) {
            allAds = new ArrayList<>(serverAds);
            if (adScheduler != null) {
                adScheduler.updateAds(allAds);
            }
            OfflineCache.saveAdSchedule(allAds);
            AdDownloadManager.downloadAllAds(allAds);
            AppLogger.log("[SYNC] Ads updated: " + allAds.size() + " ads");
        }
    }

    private void deleteSongFile(int id) {
        String baseDownloadDir = System.getProperty("user.home")
                + File.separator + ".scamusica"
                + File.separator + "downloads";

        File baseDir = new File(baseDownloadDir);

        File[] folders = baseDir.listFiles();
        if (folders == null)
            return;

        for (File folder : folders) {
            File file = new File(folder, "song-" + id + ".dat");
            if (file.exists()) {
                AppLogger.log("[DELETE] Removing file for ID: " + id);
                file.delete();
            }
        }
    }

    private void hideDropdown(VBox dropdownCard) {
        dropdownCard.setVisible(false);
        dropdownCard.setManaged(false);
    }

    private int countExistingInGenreFolder(String genreFolderPath) {
        File dir = new File(genreFolderPath);
        if (!dir.exists() || !dir.isDirectory())
            return 0;
        File[] files = dir.listFiles();
        if (files == null)
            return 0;
        int c = 0;
        for (File f : files) {
            if (!f.isDirectory() && f.getName().startsWith("song-") && f.getName().endsWith(".dat")) {
                if (f.length() > 10_000) {
                    c++;
                } else {
                    AppLogger.log("[PlayerController] Deleting incomplete file: " + f.getName() + " (" + f.length() + " bytes)");
                    f.delete();
                }
            }
        }
        return c;
    }

    private int countExistingDownloadedFiles(File root) {
        if (root == null || !root.exists())
            return 0;
        int count = 0;
        File[] files = root.listFiles();
        if (files == null)
            return 0;
        for (File f : files) {
            if (f.isDirectory()) {
                count += countExistingDownloadedFiles(f);
            } else {
                String name = f.getName();
                if (name.startsWith("song-") && name.endsWith(".dat")) {
                    if (f.length() > 10_000) {
                        count++;
                    } else {
                        AppLogger.log("[PlayerController] Deleting incomplete file: " + f.getName() + " (" + f.length() + " bytes)");
                        f.delete();
                    }
                }
            }
        }
        return count;
    }

    private void recomputeGlobalCountAndUpdateUI() {
        Platform.runLater(() -> {
            String baseDownloadDir = System.getProperty("user.home")
                    + File.separator + ".scamusica"
                    + File.separator + "downloads";

            File baseDir = new File(baseDownloadDir);
            if (!baseDir.exists()) {
                boolean created = baseDir.mkdirs();
                AppLogger.log("[PlayerController] Base dir created: " + created);
            }

            // Mac installer permissions fix
            baseDir.setWritable(true, false);
            baseDir.setReadable(true, false);
            baseDir.setExecutable(true, false);

            int globalExisting = countExistingDownloadedFiles(new File(baseDownloadDir));
            totalDownloadedCounter.set(globalExisting);
            albumUtil.setSongCount(globalExisting);
        });
    }

    private Button getBigPlayButton(HBox controlsWrapper) {
        try {
            return (Button) ((StackPane) controlsWrapper.getChildren().get(1)).getChildren().get(0);
        } catch (Exception e) {
            return null;
        }
    }

    private void updatePlayButtonState(HBox controlsWrapper) {
        Button bigPlayBtn = getBigPlayButton(controlsWrapper);
        if (bigPlayBtn == null)
            return;

        // We now support streaming fallback, so the play button should always be
        // enabled
        Platform.runLater(() -> bigPlayBtn.setDisable(false));
    }

    private void setGenreSwitchEnabled(boolean enabled) {
        if (playlistPill != null) {
            Platform.runLater(() -> playlistPill.setDisable(!enabled));
        }
        if (playlistDropdownCard != null) {
            Platform.runLater(() -> playlistDropdownCard.setDisable(!enabled));
        }
    }

    private void loadPlaylistAndStart(String playlistName,
            Label albumHeading,
            Label titleLabel,
            Slider progressSlider,
            Label leftTime,
            Label rightTime,
            HBox controlsWrapper,
            HBox bottomBar,
            Label downloadLabel,
            boolean autoPlay) throws URISyntaxException {

        stopPlayback(progressSlider, leftTime, rightTime, controlsWrapper, downloadLabel);

        playQueue.clear();
        currentTrackIndex = 0;
        isFirstTrackStarted = false;

        try {
            PlaylistApiService playlistApiService = apiService;

            // ✅ STEP 1: Fetch both tracks AND download sequence
            List<PlaylistTrack> fetchedTracks = playlistApiService.fetchTracksForGenre(playlistName);
            List<Integer> downloadSeq = playlistApiService.fetchDownloadSequenceForGenre(playlistName);

            if (downloadSeq == null)
                downloadSeq = new ArrayList<>();

            // ✅ STEP 2: KEY FIX - Reorder playQueue to match downloadSequence
            // This ensures first songs in queue are the first ones being downloaded
            if (fetchedTracks != null && !fetchedTracks.isEmpty()) {
                if (!downloadSeq.isEmpty()) {
                    // Create a map of ID -> Track for quick lookup
                    Map<Integer, PlaylistTrack> trackMap = new HashMap<>();
                    for (PlaylistTrack t : fetchedTracks) {
                        trackMap.put(t.getId(), t);
                    }

                    // Reorder playQueue to match downloadSeq order
                    // e.g., if downloadSeq = [561, 572, 564], then playQueue[0]=561,
                    // playQueue[1]=572, etc.
                    List<PlaylistTrack> reorderedQueue = new ArrayList<>();

                    String baseDownloadDir = System.getProperty("user.home") + java.io.File.separator + ".scamusica" + java.io.File.separator + "downloads";
                    String genreFolderPath = baseDownloadDir + java.io.File.separator + playlistName.replaceAll("\\s+", "_");

                    // 1. First add downloaded tracks
                    for (Integer id : downloadSeq) {
                        if (trackMap.containsKey(id)) {
                            java.io.File candidate = new java.io.File(genreFolderPath, "song-" + id + ".dat");
                            if (candidate.exists() && candidate.length() > 0) {
                                reorderedQueue.add(trackMap.get(id));
                            }
                        }
                    }

                    // 2. Then add not yet downloaded tracks
                    for (Integer id : downloadSeq) {
                        if (trackMap.containsKey(id)) {
                            java.io.File candidate = new java.io.File(genreFolderPath, "song-" + id + ".dat");
                            if (!candidate.exists() || candidate.length() == 0) {
                                reorderedQueue.add(trackMap.get(id));
                            }
                        }
                    }

                    // Add remaining tracks (not in downloadSeq) - just in case
                    Set<Integer> seenIds = new HashSet<>(downloadSeq);
                    for (PlaylistTrack t : fetchedTracks) {
                        if (!seenIds.contains(t.getId())) {
                            reorderedQueue.add(t);
                        }
                    }

                    playQueue.addAll(reorderedQueue);
                } else {
                    // No download sequence, shuffle normally
                    List<PlaylistTrack> shuffled = new ArrayList<>(fetchedTracks);
                    java.util.Collections.shuffle(shuffled);
                    playQueue.addAll(shuffled);
                }
            }

            recomputeGlobalCountAndUpdateUI();

            if (downloadManager != null) {
                downloadManager.stop();
                downloadManager = null;
            }

            // Set first image AFTER reordering queue
            if (!playQueue.isEmpty() && albumImageView != null) {
                String firstImgUrl = playQueue.get(0).getAlbumImageUrl();
                if (firstImgUrl != null && !firstImgUrl.trim().isEmpty()) {
                    if (!firstImgUrl.equals(currentAlbumImgUrl)) {
                        currentAlbumImgUrl = firstImgUrl;
                        Platform.runLater(() -> albumImageView.setImage(null));
                        asyncExecutor.submit(() -> {
                            try {
                                Image image = com.musicplayer.scamusica.util.ImageCache.getImage(firstImgUrl);
                                Platform.runLater(() -> albumImageView.setImage(image));
                            } catch (Exception ignored) {
                            }
                        });
                    }
                } else {
                    currentAlbumImgUrl = null;
                    Platform.runLater(() -> albumImageView.setImage(null));
                }
            }

            currentGenreTotalFiles = downloadSeq.size();

            String baseDownloadDir = System.getProperty("user.home") + File.separator + ".scamusica" + File.separator
                    + "downloads";

            String genreFolderPath = baseDownloadDir + File.separator + playlistName.replaceAll("\\s+", "_");

            File genreDir = new File(genreFolderPath);
            if (!genreDir.exists()) {
                boolean created = genreDir.mkdirs();
                AppLogger.log("[PlayerController] Genre folder created: " + created + " at " + genreFolderPath);
            }
            genreDir.setWritable(true, false);

            int existingInGenre = countExistingInGenreFolder(genreFolderPath);
            currentGenreDownloadedCount.set(existingInGenre);

            updateGenreDownloadLabel(downloadLabel);

            updatePlayButtonState(controlsWrapper);

            boolean needDownload = false;
            if (downloadSeq.isEmpty()) {
                needDownload = false;
            } else {
                for (Integer id : downloadSeq) {
                    File candidate = new File(genreFolderPath, "song-" + id + ".dat");
                    if (candidate.exists() && candidate.length() <= 10_000) {
                        AppLogger.log("[PlayerController] Deleting incomplete file during sequence check: " + candidate.getName() + " (" + candidate.length() + " bytes)");
                        candidate.delete();
                    }
                    if (!candidate.exists() || candidate.length() <= 10_000) {
                        needDownload = true;
                    }
                }
            }

            setGenreSwitchEnabled(true);

            if (!downloadSeq.isEmpty()) {
                downloadManager = new DownloadManager(genreFolderPath,
                        new DownloadManager.DownloadListener() {
                            @Override
                            public void onDownloadStarted(int songId, File outputFile) {
                                currentFileProgressFraction = 0.0;

                                updatePlayButtonState(controlsWrapper);

                                if (downloadLabel != null) {
                                    Platform.runLater(() -> {
                                        try {
                                            downloadLabel.textProperty().unbind();
                                        } catch (Exception ignored) {
                                        }
                                        updateGenreDownloadLabel(downloadLabel);
                                    });
                                }
                            }

                            @Override
                            public void onDownloadProgress(int songId, long bytesDownloaded, long contentLength) {
                                double frac = 0.0;
                                if (contentLength > 0) {
                                    frac = ((double) bytesDownloaded) / ((double) contentLength);
                                    if (frac < 0.0)
                                        frac = 0.0;
                                    if (frac > 1.0)
                                        frac = 1.0;
                                } else {
                                    frac = Math.min(1.0, bytesDownloaded / (1024.0 * 200));
                                }
                                currentFileProgressFraction = frac;
                                updateGenreDownloadLabel(downloadLabel);
                            }

                            // @Override
                            // public void onDownloadCompleted(int songId, File outputFile) {
                            //
                            // recomputeGlobalCountAndUpdateUI();
                            //
                            // Platform.runLater(() -> {
                            //
                            // int newGenreCount = countExistingInGenreFolder(genreFolderPath);
                            // currentGenreDownloadedCount.set(newGenreCount);
                            //
                            // currentFileProgressFraction = 0.0;
                            //
                            // updateGenreDownloadLabel(downloadLabel);
                            // updatePlayButtonState(controlsWrapper);
                            // AppLogger.log("[AUTO-PLAY] Downloaded count: " + newGenreCount);
                            // if (newGenreCount >= 2) {
                            // if (!vlcPlayer.status().isPlaying() && !userPaused) {
                            // try {
                            // AppLogger.log("[AutoPlay] 2 songs downloaded. Starting playback." +
                            // "..");
                            // playTrack(
                            // albumHeading,
                            // titleLabel,
                            // progressSlider,
                            // leftTime,
                            // rightTime,
                            // controlsWrapper,
                            // bottomBar,
                            // downloadLabel,
                            // true
                            // );
                            // } catch (URISyntaxException e) {
                            // e.printStackTrace();
                            // }
                            // }
                            // }
                            // });
                            // }

                            @Override
                            public void onDownloadCompleted(int songId, File outputFile) {
                                recomputeGlobalCountAndUpdateUI();

                                Platform.runLater(() -> {
                                    int newGenreCount = countExistingInGenreFolder(genreFolderPath);
                                    currentGenreDownloadedCount.set(newGenreCount);
                                    currentFileProgressFraction = 0.0;

                                    updateGenreDownloadLabel(downloadLabel);
                                    updatePlayButtonState(controlsWrapper);
                                    AppLogger.log(
                                            "[AUTO-PLAY] Downloaded: " + newGenreCount + "/" + currentGenreTotalFiles);

                                    // Autoplay logic removed: loadPlaylistAndStart() already starts playback immediately.
                                });
                            }

                            @Override
                            public void onDownloadSkipped(int songId, File existingFile) {
                                recomputeGlobalCountAndUpdateUI();

                                int newGenreCount = countExistingInGenreFolder(genreFolderPath);
                                currentGenreDownloadedCount.set(newGenreCount);

                                updateGenreDownloadLabel(downloadLabel);
                                updatePlayButtonState(controlsWrapper);
                            }

                            @Override
                            public void onDownloadFailed(int songId, Exception ex) {
                                AppLogger.log(
                                        "[PlayerController] Download failed id=" + songId + " -> " + ex.getMessage());
                                currentFileProgressFraction = 0.0;
                                updateGenreDownloadLabel(downloadLabel);
                            }

                            @Override
                            public void onAllDownloadsFinished() {
                                AppLogger.log("[PlayerController] All downloads finished for genre: " + playlistName);
                                setGenreSwitchEnabled(true);

                                recomputeGlobalCountAndUpdateUI();
                                int newGenreCount = countExistingInGenreFolder(genreFolderPath);
                                currentGenreDownloadedCount.set(newGenreCount);

                                updatePlayButtonState(controlsWrapper);
                            }

                            @Override
                            public void onCancelled() {
                                AppLogger.log("[PlayerController] Downloads cancelled for genre: " + playlistName);
                                setGenreSwitchEnabled(true);
                                recomputeGlobalCountAndUpdateUI();
                                int newGenreCount = countExistingInGenreFolder(genreFolderPath);
                                currentGenreDownloadedCount.set(newGenreCount);
                                updatePlayButtonState(controlsWrapper);
                            }
                        });

                downloadManager.start();
                synchronized (playQueue) {
                    for (PlaylistTrack pt : playQueue) {
                        if (pt != null && pt.getId() != null && pt.getUrl() != null) {
                            downloadManager.registerFallbackUrl(pt.getId(), pt.getUrl());
                        }
                    }
                }
                for (Integer id : downloadSeq) {
                    downloadManager.queueDownload(id);
                }
            } else {
                currentFileProgressFraction = 0.0;
                updateGenreDownloadLabel(downloadLabel);
                setGenreSwitchEnabled(true);
                updatePlayButtonState(controlsWrapper);
            }

        } catch (Exception e) {
            e.printStackTrace();
            setGenreSwitchEnabled(true);
            updatePlayButtonState(controlsWrapper);
        }

        albumHeading.textProperty().bind(LanguageManager.createStringBinding("label.loading"));
        if (!playQueue.isEmpty()) {
            isFirstTrackStarted = true;
            playTrack(
                    albumHeading,
                    titleLabel,
                    progressSlider,
                    leftTime,
                    rightTime,
                    controlsWrapper,
                    bottomBar,
                    downloadLabel,
                    autoPlay);
        } else {
            albumHeading.textProperty().bind(LanguageManager.createStringBinding("label.noSong"));
        }
    }

    private void updateGenreDownloadLabel(Label downloadLabel) {
        if (downloadLabel == null)
            return;

        double percent = 0.0;
        if (currentGenreTotalFiles <= 0) {
            percent = 100.0;
        } else {
            double completed = currentGenreDownloadedCount.get();
            double frac = currentFileProgressFraction;
            percent = ((completed + frac) / (double) currentGenreTotalFiles) * 100.0;
            if (percent < 0.0)
                percent = 0.0;
            if (percent > 100.0)
                percent = 100.0;
        }

        final String text = String.format("%.0f%% %s (%d/%d)", percent, LanguageManager.createStringBinding("label" +
                ".download").get(),
                currentGenreDownloadedCount.get(), currentGenreTotalFiles);

        boolean isDone = (currentGenreDownloadedCount.get() == currentGenreTotalFiles
                && currentGenreTotalFiles > 0);

        Platform.runLater(() -> {
            try {
                downloadLabel.textProperty().unbind();
            } catch (Exception ignored) {
            }
            downloadLabel.setText(text);

            if (isDone) {
                downloadLabel.setStyle("-fx-text-fill: #22c55e;"); // green
            } else {
                downloadLabel.setStyle("-fx-text-fill: #ef4444;"); // red
            }
        });
    }

    private void playTrack(Label albumHeading,
            Label titleLabel,
            Slider progressSlider,
            Label leftTime,
            Label rightTime,
            HBox controlsWrapper,
            HBox bottomBar,
            Label downloadLabel,
            boolean autoPlay) throws URISyntaxException {

        if (playQueue.isEmpty())
            return;

        if (currentTrackIndex < 0 || currentTrackIndex >= playQueue.size()) {
            stopPlayback(progressSlider, leftTime, rightTime, controlsWrapper, downloadLabel);
            return;
        }

        PlaylistTrack track = playQueue.get(currentTrackIndex);
        // New Code for generating the file
        PlaybackHistoryLogger.logSong(track);
        // New Code for generating the file
        AppLogger.log("[PLAYER][PLAY] " + track.getTitle() + " (ID: " + track.getId() + ")");

        // if (albumImageView != null) {
        // String albumImgUrl = track.getAlbumImageUrl();
        // if (albumImgUrl != null && !albumImgUrl.trim().isEmpty()) {
        // try {
        // albumImageView.setImage(new Image(albumImgUrl, true));
        // } catch (Exception ex) {
        // ex.printStackTrace();
        // }
        // }
        // }

        if (albumImageView != null) {
            String albumImgUrl = track.getAlbumImageUrl();
            if (albumImgUrl != null && !albumImgUrl.trim().isEmpty()) {
                if (!albumImgUrl.equals(currentAlbumImgUrl)) {
                    currentAlbumImgUrl = albumImgUrl;
                    albumImageView.setImage(null);
                    asyncExecutor.submit(() -> {
                        try {
                            Image image = com.musicplayer.scamusica.util.ImageCache.getImage(albumImgUrl);
                            Platform.runLater(() -> albumImageView.setImage(image));
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                }
            } else {
                currentAlbumImgUrl = null;
                albumImageView.setImage(null);
            }
        }

        String folderTitle = track.getFolderTitle();
        if (folderTitle != null && !folderTitle.trim().isEmpty()) {
            albumHeading.textProperty().unbind();
            albumHeading.setText(folderTitle);
        } else {
            albumHeading.textProperty().bind(LanguageManager.createStringBinding("label.unknownFolder"));
        }

        titleLabel.setText(track.getTitle());

        if (vlcPlayer != null && vlcPlayer.status().isPlaying()) {
            try {
                vlcPlayer.controls().pause();
            } catch (Exception ignored) {
            }
        }

        String safeUrl = encodeMediaUrl(track.getUrl());

        if (!safeUrl.contains(".mp3")) {
            safeUrl += "?ext=.mp3";
        }

        AppLogger.log("FIXED MEDIA URL = " + safeUrl);

        try {
            String baseDownloadDir = System.getProperty("user.home")
                    + File.separator + ".scamusica"
                    + File.separator + "downloads";

            String genreFolder = (currentPlaylistName != null)
                    ? currentPlaylistName.replaceAll("\\s+", "_")
                    : track.getFolderTitle().replaceAll("\\s+", "_");

            File encryptedFile = new File(baseDownloadDir + File.separator + genreFolder,
                    "song-" + track.getId() + ".dat");

            AppLogger.log("Encrypted file path: " + encryptedFile.getAbsolutePath());
            AppLogger.log("File exists: " + encryptedFile.exists());

            if (!encryptedFile.exists() && !NetworkMonitor.getInstance().isOnline()) {
                AppLogger.log(
                        "[PLAYER] Offline and file doesn't exist for song-" + track.getId() + ", skipping to next.");
                
                consecutiveErrorCount++;
                if (consecutiveErrorCount > 3) {
                    AppLogger.log("[PLAYER] Too many offline skips. Stopping loop.");
                    return;
                }

                schedular.schedule(() -> {
                    Platform.runLater(() -> {
                        try {
                            playNextTrack(albumHeading, titleLabel, progressSlider,
                                    leftTime, rightTime, controlsWrapper, bottomBar, downloadLabel);
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    });
                }, 2000, java.util.concurrent.TimeUnit.MILLISECONDS);
                return;
            }

            if (encryptedFile.exists() && encryptedFile.length() <= 10_000) {
                AppLogger.log("[PLAYER] Deleting corrupted/incomplete file before playback for song-" + track.getId() + " (" + encryptedFile.length() + " bytes)");
                encryptedFile.delete();
                if (downloadManager != null) {
                    if (track.getUrl() != null) {
                        downloadManager.registerFallbackUrl(track.getId(), track.getUrl());
                    }
                    downloadManager.queueDownload(track.getId());
                }
            }

            if (encryptedFile.exists() && encryptedFile.length() > 10_000) {
                AppLogger.log("[PLAYER] Playing from local file: " + encryptedFile.getAbsolutePath());
                final String fallbackUrl = safeUrl;
                asyncExecutor.submit(() -> {
                    int originalPriority = Thread.currentThread().getPriority();
                    Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
                    try {
                        cleanupTempFile();
                        File tempFile = decryptToTemp(encryptedFile);
                        synchronized (PlayerController.this) {
                            currentTempFile = tempFile;
                        }
                        String localUrl = tempFile.toURI().toString();

                        if (!localUrl.contains(".mp3")) {
                            localUrl += "?ext=.mp3";
                        }

                        final String finalUrl = localUrl;

                        Platform.runLater(() -> {

                            vlcPlayer.media().play(tempFile.getAbsolutePath());

                            if (!vlcHandlersAttached) {

                                attachVlcHandlers(
                                        albumHeading,
                                        titleLabel,
                                        progressSlider,
                                        leftTime,
                                        rightTime,
                                        controlsWrapper,
                                        bottomBar,
                                        downloadLabel,
                                        autoPlay);

                                vlcHandlersAttached = true;
                            }
                        });

                    } catch (Exception e) {
                        AppLogger.log("[PLAYER] Decryption failed for song-" + track.getId()
                                + ", file corrupted. Deleting and streaming from URL. Error: " + e.getMessage());
                        encryptedFile.delete();

                        if (NetworkMonitor.getInstance().isOnline()) {
                            Platform.runLater(() -> {
                                AppLogger.log("[PLAYER] Falling back to stream: " + fallbackUrl);
                                vlcPlayer.media().play(fallbackUrl);
                                if (!vlcHandlersAttached) {
                                    attachVlcHandlers(albumHeading, titleLabel, progressSlider,
                                            leftTime, rightTime, controlsWrapper, bottomBar, downloadLabel, autoPlay);
                                    vlcHandlersAttached = true;
                                }
                            });
                        } else {
                            AppLogger.log("[PLAYER] Offline — cannot stream fallback for song-" + track.getId()
                                    + ", skipping to next.");
                            
                            consecutiveErrorCount++;
                            if (consecutiveErrorCount > 3) {
                                AppLogger.log("[PLAYER] Too many offline decryption skips. Stopping loop.");
                                return;
                            }

                            schedular.schedule(() -> {
                                Platform.runLater(() -> {
                                    try {
                                        playNextTrack(albumHeading, titleLabel, progressSlider,
                                                leftTime, rightTime, controlsWrapper, bottomBar, downloadLabel);
                                    } catch (Exception ex) {
                                        ex.printStackTrace();
                                    }
                                });
                            }, 2000, java.util.concurrent.TimeUnit.MILLISECONDS);
                        }
                    } finally {
                        Thread.currentThread().setPriority(originalPriority);
                    }
                });

                return;
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        AppLogger.log("[PLAYER] Streaming from URL: " + safeUrl);

        vlcPlayer.media().play(safeUrl);

        if (!vlcHandlersAttached) {

            attachVlcHandlers(
                    albumHeading,
                    titleLabel,
                    progressSlider,
                    leftTime,
                    rightTime,
                    controlsWrapper,
                    bottomBar,
                    downloadLabel,
                    autoPlay);

            vlcHandlersAttached = true;
        }
    }

    private void attachVlcHandlers(
            Label albumHeading,
            Label titleLabel,
            Slider progressSlider,
            Label leftTime,
            Label rightTime,
            HBox controlsWrapper,
            HBox bottomBar,
            Label downloadLabel,
            boolean autoPlay) {

        if (currentVlcListener != null) {
            return;
        }

        currentVlcListener = new MediaPlayerEventAdapter() {
            private long lastTimeChangedMillis = 0;

            @Override
            public void playing(MediaPlayer mediaPlayer) {
                consecutiveErrorCount = 0;
                Platform.runLater(() -> {
                    FontIcon bigIcon = controlsUtil.getBigPlayIcon(controlsWrapper);
                    if (bigIcon != null) {
                        bigIcon.setIconLiteral("fas-pause");
                    }
                    // ✅ VU Meter start
                    if (ledVuMeter != null) {
                        ledVuMeter.start();
                    }
                });
            }

            @Override
            public void paused(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    FontIcon bigIcon = controlsUtil.getBigPlayIcon(controlsWrapper);
                    if (bigIcon != null) {
                        bigIcon.setIconLiteral("fas-play");
                    }
                    // ✅ VU Meter stop
                    if (ledVuMeter != null) {
                        ledVuMeter.stop();
                    }
                });
            }

            @Override
            public void stopped(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    FontIcon bigIcon = controlsUtil.getBigPlayIcon(controlsWrapper);
                    if (bigIcon != null) {
                        bigIcon.setIconLiteral("fas-play");
                    }
                    // ✅ VU Meter stop
                    if (ledVuMeter != null) {
                        ledVuMeter.stop();
                    }
                });
            }

            @Override
            public void timeChanged(MediaPlayer mediaPlayer, long newTime) {
                long now = System.currentTimeMillis();
                if (now - lastTimeChangedMillis < 250)
                    return; // Throttle to 250ms
                lastTimeChangedMillis = now;

                Platform.runLater(() -> {

                    long duration = mediaPlayer.status().length();

                    if (duration > 0) {

                        double progress = (double) newTime / duration;

                        progressSlider.setValue(progress * 100);

                        leftTime.setText(formatTime(newTime / 1000));

                        rightTime.setText("-" + formatTime((duration - newTime) / 1000));
                    }
                    if (ledVuMeter != null && vlcPlayer != null) {
                        int vol = (int) prefs.getDouble(PREF_VOLUME, 85.0); // Use cached!
                        if (vol <= 0) {
                            ledVuMeter.setManualLevel(0f);
                        } else {
                            float volFactor = vol / 100.0f;
                            float minLevel = 0.25f * volFactor;
                            float maxLevel = volFactor;
                            float sim = minLevel + (float) (Math.random() * (maxLevel - minLevel));
                            ledVuMeter.setManualLevel(sim);
                        }
                    }
                });
            }

            @Override
            public void finished(MediaPlayer mediaPlayer) {

                if (adPlayer != null && adPlayer.isPlayingAd()) {
                    AppLogger.log("[PLAYER] Media finished but ad is active, ignoring");
                    return;
                }

                cleanupTempFile();

                Platform.runLater(() -> {
                    try {

                        AppLogger.log("[PLAYER] Song finished, playing next track");

                        playNextTrack(
                                albumHeading,
                                titleLabel,
                                progressSlider,
                                leftTime,
                                rightTime,
                                controlsWrapper,
                                bottomBar,
                                downloadLabel);

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }

            @Override
            public void error(MediaPlayer mediaPlayer) {
                AppLogger.log("[PLAYER] VLC encountered an error during playback (skipped API DB log).");
                
                consecutiveErrorCount++;
                if (consecutiveErrorCount > 3) {
                    AppLogger.log("[PLAYER] Too many streaming errors. Stopping playback to wait for downloads.");
                    return;
                }
                
                // Play next track on error to avoid getting stuck
                schedular.schedule(() -> {
                    Platform.runLater(() -> {
                        try {
                            playNextTrack(albumHeading, titleLabel, progressSlider, leftTime, rightTime, controlsWrapper, bottomBar, downloadLabel);
                        } catch (Exception e) {}
                    });
                }, 2000, java.util.concurrent.TimeUnit.MILLISECONDS);
            }
        };
        vlcPlayer.events().addMediaPlayerEventListener(currentVlcListener);
    }

    private void playNextTrack(Label albumHeading,
            Label titleLabel,
            Slider progressSlider,
            Label leftTime,
            Label rightTime,
            HBox controlsWrapper,
            HBox bottomBar,
            Label downloadLabel) throws URISyntaxException {

        currentTrackIndex++;
        AppLogger.log("[PLAYER] Next track index: " + currentTrackIndex);
        if (currentTrackIndex >= playQueue.size()) {
            AppLogger.log("[PlayerController] All tracks finished. Reshuffling and looping...");
            java.util.Collections.shuffle(playQueue);
            currentTrackIndex = 0;
        }

        playTrack(
                albumHeading,
                titleLabel,
                progressSlider,
                leftTime,
                rightTime,
                controlsWrapper,
                bottomBar,
                downloadLabel,
                true);
    }

    private void setupBigPlayBehaviour(Label albumHeading,
            Label titleLabel,
            HBox controlsWrapper,
            Slider progressSlider,
            Label leftTime,
            Label rightTime,
            HBox bottomBar,
            Label downloadLabel) {

        Button bigPlayBtn;
        StackPane playContainer;
        try {
            playContainer = (StackPane) controlsWrapper.getChildren().get(1);
            bigPlayBtn = (Button) playContainer.getChildren().get(0);
        } catch (Exception e) {
            return;
        }

        FontIcon bigIcon = controlsUtil.getBigPlayIcon(controlsWrapper);

        if (bigPlayBtn == null || bigIcon == null) {
            return;
        }

        javafx.event.EventHandler<javafx.scene.input.MouseEvent> clickHandler = e -> {
            e.consume();
            if (playQueue.isEmpty()) {
                return;
            }

            if (vlcPlayer == null || currentTrackIndex >= playQueue.size() || currentTrackIndex < 0) {
                currentTrackIndex = 0;
                try {
                    playTrack(
                            albumHeading,
                            titleLabel,
                            progressSlider,
                            leftTime,
                            rightTime,
                            controlsWrapper,
                            bottomBar,
                            downloadLabel,
                            true);
                } catch (URISyntaxException ex) {
                    throw new RuntimeException(ex);
                }
                return;
            }

            if (vlcPlayer.status().isPlaying()) {
                vlcPlayer.controls().pause();
                userPaused = true;
                bigIcon.setIconLiteral("fas-play");
                bigIcon.setIconColor(javafx.scene.paint.Color.WHITE);
            } else {
                if (vlcPlayer.status().state() == uk.co.caprica.vlcj.player.base.State.STOPPED ||
                        vlcPlayer.status().state() == uk.co.caprica.vlcj.player.base.State.ENDED) {
                    try {
                        playTrack(
                                albumHeading,
                                titleLabel,
                                progressSlider,
                                leftTime,
                                rightTime,
                                controlsWrapper,
                                bottomBar,
                                downloadLabel,
                                true);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                } else {
                    vlcPlayer.controls().play();
                    userPaused = false;
                    bigIcon.setIconLiteral("fas-pause");
                    bigIcon.setIconColor(javafx.scene.paint.Color.WHITE);
                }
            }
        };

        bigPlayBtn.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, clickHandler);
        playContainer.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_CLICKED, clickHandler);
    }

    private void stopPlayback(Slider progressSlider,
            Label leftTime,
            Label rightTime,
            HBox controlsWrapper,
            Label downloadLabel) {

        // Intentionally NOT removing VLC listener or setting vlcHandlersAttached to
        // false
        // to prevent JNA native callback memory leaks

        cleanupTempFile();

        if (vlcPlayer != null) {
            try {
                vlcPlayer.controls().stop();
            } catch (Exception ignored) {
            }
        }

        if (leftTime != null) {
            try {
                leftTime.textProperty().unbind();
            } catch (Exception ignored) {
            }
        }

        if (rightTime != null) {
            try {
                rightTime.textProperty().unbind();
            } catch (Exception ignored) {
            }
        }

        if (downloadLabel != null) {
            try {
                downloadLabel.textProperty().unbind();
            } catch (Exception ignored) {
            }
        }

        if (progressSlider != null) {
            try {
                progressSlider.valueProperty().unbind();
            } catch (Exception ignored) {
            }
            progressSlider.setValue(0);
        }

        if (leftTime != null) {
            leftTime.setText("0:00");
        }

        if (rightTime != null) {
            rightTime.setText("-0:00");
        }

        if (downloadLabel != null) {
            downloadLabel.textProperty().bind(Bindings.concat(
                    "0% ",
                    LanguageManager.createStringBinding("label.download")));
        }

        if (downloadManager != null) {
            downloadManager.stop();
            downloadManager = null;
        }

        FontIcon bigIcon = controlsUtil.getBigPlayIcon(controlsWrapper);
        if (bigIcon != null) {
            bigIcon.setIconLiteral("fas-play");
            bigIcon.setIconColor(javafx.scene.paint.Color.WHITE);
        }
    }

    private String encodeMediaUrl(String rawUrl) {
        try {
            URL url = new URL(rawUrl);
            String normalizedPath = Normalizer.normalize(url.getPath(), Normalizer.Form.NFC);

            String encodedPath = Arrays.stream(normalizedPath.split("/"))
                    .map(segment -> URLEncoder.encode(segment, StandardCharsets.UTF_8)
                            .replace("+", "%20"))
                    .collect(Collectors.joining("/"));

            String protocol = url.getProtocol();
            String host = url.getHost();
            int port = url.getPort();
            String portStr = (port == -1) ? "" : ":" + port;

            return protocol + "://" + host + portStr + encodedPath;

        } catch (Exception e) {
            System.err.println("URL Encoding Error: " + e.getMessage());
            return rawUrl;
        }
    }

    private File decryptToTemp(File encryptedFile) throws Exception {
        File tempDir = new File(System.getProperty("user.home")
                + File.separator + ".scamusica"
                + File.separator + "temp");
        tempDir.mkdirs();
        File tempFile = new File(tempDir, "play_" + System.currentTimeMillis() + ".mp3");

        try (FileInputStream fis = new FileInputStream(encryptedFile);
                CipherInputStream cis = CryptoUtil.decrypt(fis);
                FileOutputStream fos = new FileOutputStream(tempFile)) {

            byte[] buffer = new byte[8192];
            int read;

            while ((read = cis.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }

        return tempFile;
    }

    private void cleanupTempFile() {
        synchronized (this) {
            if (currentTempFile != null) {
                try {
                    if (currentTempFile.exists()) {
                        currentTempFile.delete();
                        AppLogger.log("[TEMP] Deleted: " + currentTempFile.getName());
                    }
                } catch (Exception e) {
                    AppLogger.log("[TEMP] Failed to delete: " + e.getMessage());
                }
                currentTempFile = null;
            }
        }
    }

    private String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }

    public static void main(String[] args) {
        launch(args);
    }

    private int getCurrentAdVolume() {
        if (currentVolumeSettings == null || currentVolumeSettings.getAdVolume() == null) {
            return (int) prefs.getDouble(PREF_VOLUME, 85.0);
        }
        if (currentScheduleId != null && currentScheduleId != -999 && currentVolumeSettings.getSchedules() != null) {
            for (VolumeSchedule sched : currentVolumeSettings.getSchedules()) {
                if (sched.getId() == currentScheduleId) {
                    return sched.getAdVolume();
                }
            }
        }
        return currentVolumeSettings.getAdVolume();
    }

    private void checkAndApplyVolumeSchedule() {
        if (currentVolumeSettings == null) return;
        
        // Do not interfere with volume if an ad is currently playing
        if (adPlayer != null && adPlayer.isPlayingAd()) {
            return;
        }

        String currentTime = java.time.LocalTime.now().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
        Integer activeScheduleId = null;
        Integer targetMusicVolume = currentVolumeSettings.getMusicVolume();
        
        // Disable slider if volume_source is 'admin' or 'player' or if music_volume is explicitly configured.
        final boolean isVolumeFromAdmin = currentVolumeSettings.getMusicVolume() != null &&
            currentVolumeSettings.getVolumeSource() != null && 
            ("admin".equalsIgnoreCase(currentVolumeSettings.getVolumeSource()) || "player".equalsIgnoreCase(currentVolumeSettings.getVolumeSource()));

        if (currentVolumeSettings.getSchedules() != null) {
            for (VolumeSchedule sched : currentVolumeSettings.getSchedules()) {
                if (sched.getStartTime() != null && sched.getEndTime() != null) {
                    if (currentTime.compareTo(sched.getStartTime()) >= 0 && currentTime.compareTo(sched.getEndTime()) <= 0) {
                        activeScheduleId = sched.getId();
                        targetMusicVolume = sched.getMusicVolume();
                        break;
                    }
                }
            }
        }

        final boolean isScheduleActive = (activeScheduleId != null);
        final boolean shouldDisableSlider = isVolumeFromAdmin || isScheduleActive;
        
        Platform.runLater(() -> {
            try {
                if (globalBottomBar != null && controlsUtil != null) {
                    Slider volumeSlider = controlsUtil.getVolumeSlider(globalBottomBar);
                    if (volumeSlider != null) {
                        volumeSlider.setDisable(shouldDisableSlider);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Use a consistent comparison, treating -999 as null for the baseline
        Integer effectiveCurrentScheduleId = (currentScheduleId != null && currentScheduleId == -999) ? null : currentScheduleId;
        boolean scheduleChanged = !java.util.Objects.equals(activeScheduleId, effectiveCurrentScheduleId);
        
        int currentSetVol = (int) prefs.getDouble(PREF_VOLUME, 85.0);
        boolean volumeNeedsUpdate = (targetMusicVolume != null && targetMusicVolume != currentSetVol);

        if (scheduleChanged || (shouldDisableSlider && volumeNeedsUpdate)) {
            AppLogger.log("[Volume] Applying volume update. Schedule changed: " + scheduleChanged + 
                          ", target volume: " + targetMusicVolume);
            currentScheduleId = activeScheduleId; // Correctly store the exact schedule ID (or null)
            if (targetMusicVolume != null) {
                final int volToApply = targetMusicVolume;
                Platform.runLater(() -> {
                    try {
                        prefs.putDouble(PREF_VOLUME, volToApply);
                        if (vlcPlayer != null && vlcPlayer.audio() != null) {
                            vlcPlayer.audio().setVolume(volToApply);
                        }
                        if (globalBottomBar != null && controlsUtil != null) {
                            Slider volumeSlider = controlsUtil.getVolumeSlider(globalBottomBar);
                            if (volumeSlider != null) {
                                volumeSlider.setValue(volToApply);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }
    }

    /**
     * Deletes download folders and cache files for sequences that are
     * no longer assigned to this player (unassigned, deleted, or expired).
     */
    private void cleanupOrphanedSequences(List<String> serverTitles) {
        try {
            String baseDownloadDir = System.getProperty("user.home")
                    + File.separator + ".scamusica"
                    + File.separator + "downloads";

            File baseDir = new File(baseDownloadDir);
            if (!baseDir.exists() || !baseDir.isDirectory()) return;

            java.util.Set<String> validFolderNames = new java.util.HashSet<>();
            for (String title : serverTitles) {
                validFolderNames.add(title.replaceAll("\\s+", "_"));
            }
            // Always retain the Default sequence folder so it's ready when falling back
            validFolderNames.add("Default");

            File[] folders = baseDir.listFiles();
            if (folders == null) return;

            for (File folder : folders) {
                if (!folder.isDirectory()) continue;

                String folderName = folder.getName();

                if (!validFolderNames.contains(folderName)) {
                    AppLogger.log("[CLEANUP] Orphaned sequence folder found: " + folderName);

                    File[] files = folder.listFiles();
                    int deletedCount = 0;
                    if (files != null) {
                        for (File f : files) {
                            if (f.delete()) {
                                deletedCount++;
                            }
                        }
                    }

                    boolean folderDeleted = folder.delete();
                    AppLogger.log("[CLEANUP] Deleted " + deletedCount + " files, folder removed: "
                            + folderDeleted + " (" + folderName + ")");

                    String sequenceName = folderName.replaceAll("_", " ");
                    com.musicplayer.scamusica.util.OfflineCache.removeSequenceCache(sequenceName);
                }
            }
        } catch (Exception e) {
            AppLogger.log("[CLEANUP] Error during orphaned sequence cleanup: " + e.getMessage());
        }
    }
}