package com.musicplayer.scamusica.ui;

import com.musicplayer.scamusica.manager.LanguageManager;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.net.URISyntaxException;
import java.net.URL;
import java.util.Locale;

public class PlayerControls {

    private boolean seeking = false;

    public Slider createProgressSlider() {
        Slider slider = new Slider(0, 100, 0);
        slider.getStyleClass().add("player-progress");
        slider.setMinWidth(300);
        return slider;
    }

    public Label createTimeLabel(boolean isRemaining) {
        Label lbl = new Label(isRemaining ? "-0:00" : "0:00");
        lbl.getStyleClass().add("time-label");
        return lbl;
    }

    public HBox createTimesRow(Label leftTime, Label rightTime) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox timesRow = new HBox(leftTime, spacer, rightTime);
        timesRow.setAlignment(javafx.geometry.Pos.CENTER);
        timesRow.setPadding(new Insets(0, 12, 6, 12));
        return timesRow;
    }

    public HBox createTimesRow(Label leftTime, Label rightTime, Node visualizer) {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox rightContainer = new HBox(12, visualizer, rightTime);
        rightContainer.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        HBox timesRow = new HBox(leftTime, spacer, rightContainer);
        timesRow.setAlignment(javafx.geometry.Pos.CENTER);
        timesRow.setPadding(new Insets(0, 12, 6, 12));
        return timesRow;
    }

    public HBox createProgressRow(Slider progressSlider) {
        HBox progressRow = new HBox(progressSlider);
        progressRow.setAlignment(javafx.geometry.Pos.CENTER);
        progressRow.setPadding(new Insets(0, 18, 0, 18));
        HBox.setHgrow(progressSlider, Priority.ALWAYS);
        return progressRow;
    }

    public VBox createSliderContainer(Label titleCentered, HBox timesRow, HBox progressRow) {
        VBox sliderContainer = new VBox(4, titleCentered, timesRow, progressRow);
        sliderContainer.setAlignment(javafx.geometry.Pos.CENTER);
        sliderContainer.setPadding(new Insets(4, 24, 2, 24));
        return sliderContainer;
    }

    public HBox createControls(Slider progressSlider, Node pill) {
        FontIcon dislikeIcon = new FontIcon("fas-thumbs-down");
        dislikeIcon.setIconSize(40);
        Button dislikeBtn = new Button("", dislikeIcon);
        dislikeBtn.getStyleClass().add("control-icon");
        dislikeBtn.getStyleClass().add("small-control");

//        FontIcon likeIcon = new FontIcon("fas-thumbs-up");
//        likeIcon.setIconSize(32);
//        Button likeBtn = new Button("", likeIcon);
//        likeBtn.getStyleClass().add("control-icon");
//        likeBtn.getStyleClass().add("small-control");

//        FontIcon bigPlayIcon = new FontIcon("fas-play");
//        bigPlayIcon.setIconSize(35);
//        bigPlayIcon.setIconColor(Color.web("#fff"));
//        Button bigPlayBtn = new Button("", bigPlayIcon);
//        bigPlayBtn.setGraphic(bigPlayIcon);
//
//        bigPlayBtn.setMinSize(80, 80);
//        bigPlayBtn.setPrefSize(80, 80);
//        bigPlayBtn.setMaxSize(80, 80);
//        bigPlayBtn.setPadding(new Insets(0, -2, 0, 0));
//        bigPlayBtn.setStyle(
//                "-fx-background-color: #6E68A5; " +
//                        "-fx-text-fill: white;" +
//                        "-fx-background-radius: 999px; " +
//                        "-fx-effect: dropshadow(gaussian, rgba(126,97,204,0.28), 22, 0, 0, 8); " +
//                        "-fx-alignment: center;"
//        );
//        bigPlayBtn.getStyleClass().add("big-play-button");

        FontIcon forwardIcon = new FontIcon("fas-forward");
        forwardIcon.setIconSize(40);
        Button forwardBtn = new Button("", forwardIcon);
        forwardBtn.setId("forwardButton");
        forwardBtn.getStyleClass().add("control-icon");
        forwardBtn.getStyleClass().add("small-control");

        dislikeBtn.setPrefSize(60, 60);
//        likeBtn.setPrefSize(46, 46);
        forwardBtn.setPrefSize(60, 60);

        dislikeBtn.setPadding(new Insets(0));
//        likeBtn.setPadding(new Insets(0));
        forwardBtn.setPadding(new Insets(0));

        HBox likeDislikeBox = new HBox();
        likeDislikeBox.getStyleClass().add("small-control-box");
        likeDislikeBox.setAlignment(javafx.geometry.Pos.CENTER);
        likeDislikeBox.setSpacing(4);
        likeDislikeBox.getChildren().add(dislikeBtn); // Changed from addAll(dislikeBtn, likeBtn)

        StackPane playContainer = new StackPane();
        playContainer.getStyleClass().add("play-control-box");
//        playContainer.setMinSize(100, 100);
//        playContainer.setPrefSize(120, 100);
//        playContainer.setMaxSize(160, 100);
        playContainer.setAlignment(javafx.geometry.Pos.CENTER);
//        playContainer.setPadding(new Insets(0, 4, 0, 4));
//        playContainer.getChildren().add(bigPlayBtn);

        HBox forwardBox = new HBox();
        forwardBox.getStyleClass().add("forward-control-box");
        forwardBox.setAlignment(javafx.geometry.Pos.CENTER);
        forwardBox.getChildren().add(forwardBtn);

        HBox controlsWrapper = new HBox();
        controlsWrapper.getStyleClass().add("controls-wrapper");
        controlsWrapper.setAlignment(javafx.geometry.Pos.CENTER);
        controlsWrapper.setSpacing(14);
        controlsWrapper.setPadding(new Insets(6, 0, 6, 0));
        controlsWrapper.getChildren().addAll(likeDislikeBox, playContainer, forwardBox);

        dislikeBtn.setOnAction(e -> {
            if (dislikeBtn.getStyleClass().contains("control-active"))
                dislikeBtn.getStyleClass().remove("control-active");
            else {
                dislikeBtn.getStyleClass().add("control-active");
//                likeBtn.getStyleClass().remove("control-active");
            }
        });

//        likeBtn.setOnAction(e -> {
//            if (likeBtn.getStyleClass().contains("control-active")) likeBtn.getStyleClass().remove("control-active");
//            else {
//                likeBtn.getStyleClass().add("control-active");
//                dislikeBtn.getStyleClass().remove("control-active");
//            }
//        });

        forwardBtn.setOnAction(e -> {
            forwardBtn.getStyleClass().add("control-active");
            Platform.runLater(() -> forwardBtn.getStyleClass().remove("control-active"));
        });
        return controlsWrapper;
    }

    public HBox createBottomBar() {
        FontIcon downloadIcon = new FontIcon("fas-download");
        downloadIcon.setIconSize(14);
        downloadIcon.getStyleClass().add("download-icon");
        Label downloadLabel = new Label();
        downloadLabel.textProperty().bind(
                Bindings.concat(
                        "0%",
                        " ",
                        LanguageManager.createStringBinding("label.download")
                ));
        downloadLabel.getStyleClass().add("download-label");
        downloadLabel.setGraphicTextGap(10);
        downloadLabel.setPadding(new Insets(0, 0, 0, 40));

        FontIcon volLow = new FontIcon("fas-volume-down");
        volLow.setIconSize(14);
        volLow.getStyleClass().add("volume-icon");
        Slider volumeSlider = new Slider(0, 100, 50);
        volumeSlider.setPrefWidth(260);
        volumeSlider.setMinWidth(60);
        volumeSlider.setMaxWidth(260);
        volumeSlider.getStyleClass().add("volume-slider");
        FontIcon volHigh = new FontIcon("fas-volume-up");
        volHigh.setIconSize(14);
        volHigh.getStyleClass().add("volume-icon");
        Region hSpacer = new Region();
        HBox.setHgrow(hSpacer, Priority.ALWAYS);
        hSpacer.setMouseTransparent(true);

        HBox bottomBar = new HBox(12, downloadLabel, hSpacer, volLow, volumeSlider, volHigh);
        bottomBar.getStyleClass().add("bottom-bar");
        bottomBar.setPadding(new Insets(0, 24, 0, 24));
        bottomBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return bottomBar;
    }

    public Media loadMedia(Class<?> clazz) {
        Media media = null;
        try {
            URL mediaUrl = clazz.getResource("/audio/sample.mp3");
            if (mediaUrl == null) System.err.println("audio resource /audio/sample.mp3 not found.");
            else media = new Media(mediaUrl.toURI().toString());
        } catch (URISyntaxException | NullPointerException ex) {
            System.err.println("media error: " + ex.getMessage());
        }
        return media;
    }

    public void setupSliderFill(Slider progressSlider) {
        Node pTrackNode = progressSlider.lookup(".track");
        if (pTrackNode instanceof Region) {
            Region pTrack = (Region) pTrackNode;
            updateSliderFill(pTrack, 0, "#7e61cc", "#00000040");
            progressSlider.valueProperty().addListener((obs, oldV, newV) -> {
                double max = progressSlider.getMax() <= 0 ? 100 : progressSlider.getMax();
                double pct = (max <= 0) ? 0 : (newV.doubleValue() / max * 100);
                updateSliderFill(pTrack, pct, "#7e61cc", "#00000040");
            });
        } else {
            progressSlider.valueProperty().addListener((obs, oldV, newV) -> {
                double max = progressSlider.getMax() <= 0 ? 100 : progressSlider.getMax();
                double pct = (max <= 0) ? 0 : (newV.doubleValue() / max * 100);
                String pctStr = String.format(Locale.US, "%.4f", pct);
                progressSlider.setStyle("-fx-background-color: linear-gradient(to right, #7e61cc " + pctStr + "%, " +
                        "#00000040 " + pctStr + "%);");
            });
        }
    }

    public void setupVolumeSliderFill(Slider volumeSlider) {
        Node vTrackNode = volumeSlider.lookup(".track");
        if (vTrackNode instanceof Region) {
            Region vTrack = (Region) vTrackNode;
            updateSliderFill(vTrack, volumeSlider.getValue(), "#7e61cc", "#00000040");
            volumeSlider.valueProperty().addListener((obs, oldV, newV) -> updateSliderFill(vTrack,
                    newV.doubleValue(), "#7e61cc", "#00000040"));
        } else {
            volumeSlider.valueProperty().addListener((obs, oldV, newV) -> {
                double pct = newV.doubleValue();
                String pctStr = String.format(Locale.US, "%.4f", pct);
                volumeSlider.setStyle("-fx-background-color: linear-gradient(to right, #7e61cc " + pctStr + "%, " +
                        "#00000040 " + pctStr + "%);");
            });
        }
    }

    private void updateSliderFill(Region track, double pct, String fillColor, String bgColor) {
        double clamped = Math.max(0.0, Math.min(100.0, pct));
        String pctStr = String.format(Locale.US, "%.4f", clamped);
        String style = "-fx-background-color: linear-gradient(to right, " +
                fillColor + " 0%, " + fillColor + " " + pctStr + "%, " + bgColor + " " + pctStr + "%, " + bgColor +
                " 100%);" +
                "-fx-background-radius: 6;";
        track.setStyle(style);
    }

    public void setupMediaBindings(MediaPlayer mediaPlayer, Slider progressSlider, Label leftTime, Label rightTime) {
        mediaPlayer.setOnReady(() -> {
            Duration total = mediaPlayer.getMedia().getDuration();
            if (total != null && !total.isUnknown()) {
                progressSlider.setMax(total.toMillis());
                updateTimeLabel(rightTime, total, true);
            }
        });
        mediaPlayer.currentTimeProperty().addListener((obs, oldT, newT) -> {
            if (!seeking) {
                Duration current = newT;
                progressSlider.setValue(current.toMillis());
                updateTimeLabel(leftTime, current, false);
                Duration total = mediaPlayer.getMedia().getDuration();
                if (total != null && !total.isUnknown()) {
                    Duration remaining = total.subtract(current);
                    updateTimeLabel(rightTime, remaining, true);
                }
            }
        });
    }

    public void setupMediaBindingsWithDuration(MediaPlayer mediaPlayer,
                                               Slider progressSlider,
                                               Label leftTime,
                                               Label rightTime,
                                               int durationSeconds) {

        Duration total = Duration.seconds(durationSeconds);
        progressSlider.setMax(total.toMillis());
        updateTimeLabel(rightTime, total, true);

        mediaPlayer.currentTimeProperty().addListener((obs, oldT, newT) -> {
            if (!seeking) {
                Duration current = newT;
                progressSlider.setValue(current.toMillis());
                updateTimeLabel(leftTime, current, false);

                Duration remaining = total.subtract(current);
                if (remaining.lessThan(Duration.ZERO)) {
                    remaining = Duration.ZERO;
                }
                updateTimeLabel(rightTime, remaining, true);
            }
        });

        mediaPlayer.setOnReady(() -> {
            updateTimeLabel(leftTime, Duration.ZERO, false);
            updateTimeLabel(rightTime, total, true);
        });
    }

    public void setupControlEvents(HBox controlsWrapper,
                                   MediaPlayer mediaPlayer,
                                   Slider progressSlider,
                                   Label leftTime,
                                   Label rightTime,
                                   Slider volumeSlider,
                                   Integer durationSeconds,
                                   Label downloadLabel) {

        progressSlider.setOnMousePressed(e -> seeking = true);
        progressSlider.setOnMouseReleased(e -> {
            seeking = false;
            if (mediaPlayer == null) return;
            try {
                mediaPlayer.seek(Duration.millis(progressSlider.getValue()));
            } catch (NullPointerException ex) {
                System.out.println("Seek skipped (player disposed) on mouse release");
            }
        });

        progressSlider.valueChangingProperty().addListener((obs, wasChanging, isNowChanging) -> {
            if (!isNowChanging) {
                if (mediaPlayer == null) return;
                try {
                    mediaPlayer.seek(Duration.millis(progressSlider.getValue()));
                } catch (NullPointerException ex) {
                    System.out.println("Seek skipped (player disposed) on valueChanging");
                }
            }
        });

//        Button bigPlayBtn = (Button) ((StackPane) controlsWrapper.getChildren().get(1)).getChildren().get(0);
//        FontIcon bigIcon = (FontIcon) bigPlayBtn.getGraphic();
//
//        bigPlayBtn.setOnAction(e -> {
//            boolean hasDuration = (durationSeconds != null && durationSeconds > 0);
//            double curSec = mediaPlayer != null ? mediaPlayer.getCurrentTime().toSeconds() : 0.0;
//            boolean atEnd = hasDuration && curSec >= durationSeconds - 0.05;
//
//            if (mediaPlayer != null && atEnd) {
//                try {
//                    mediaPlayer.pause();
//                    mediaPlayer.seek(Duration.ZERO);
//                } catch (Exception ignored) {
//                }
//                progressSlider.setValue(0);
//
//                updateTimeLabel(leftTime, Duration.ZERO, false);
//                if (hasDuration) {
//                    updateTimeLabel(rightTime, Duration.seconds(durationSeconds), true);
//                }
//
//                if (downloadLabel != null) {
//                    downloadLabel.textProperty().bind(
//                            Bindings.concat(
//                                    "0%",
//                                    " ",
//                                    LanguageManager.createStringBinding("label.download")
//                            ));
//                }
//            }
//
//            if (mediaPlayer == null) return;
//
//            MediaPlayer.Status status = mediaPlayer.getStatus();
//            if (status == MediaPlayer.Status.PLAYING) {
//                mediaPlayer.pause();
//                bigIcon.setIconLiteral("fas-play");
//                bigIcon.setIconColor(Color.WHITE);
//            } else {
//                mediaPlayer.play();
//                bigIcon.setIconLiteral("fas-pause");
//                bigIcon.setIconColor(Color.WHITE);
//            }
//        });

        Button forwardBtn = (Button) ((HBox) controlsWrapper.getChildren().get(2)).getChildren().get(0);
        forwardBtn.setOnAction(e -> {
            if (mediaPlayer == null) return;
            try {
                Duration cur = mediaPlayer.getCurrentTime();
                Duration dur = mediaPlayer.getMedia().getDuration();
                Duration next = cur.add(Duration.seconds(10));
                if (dur != null && !dur.isUnknown() && next.greaterThan(dur)) next = dur;
                mediaPlayer.seek(next);
            } catch (NullPointerException ex) {
                System.out.println("Forward seek skipped (player disposed)");
            }
        });

        if (volumeSlider != null) {
            volumeSlider.valueProperty().addListener((obs, oldV, newV) -> {
                if (mediaPlayer == null) return;
                try {
                    mediaPlayer.setVolume(newV.doubleValue() / 100.0);
                } catch (NullPointerException ex) {
                    System.out.println("Volume change skipped (player disposed)");
                }
            });
            try {
                mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
            } catch (NullPointerException ex) {
                System.out.println("Initial volume set skipped (player disposed)");
            }
        }
    }

    /**
     * NOTE: This method intentionally does NOT bind downloadLabel to media time anymore.
     * Download percentage is controlled by the controller (per-genre logic).
     */
    public void setupDownloadProgressLabel(MediaPlayer mediaPlayer,
                                           Label downloadLabel,
                                           int durationSeconds) {
        // no-op: controller updates downloadLabel with genre-based percent.
    }

    public void setupBigPlayButtonDummy(HBox controlsWrapper) {
        try {
            Button bigPlayBtn = (Button) ((StackPane) controlsWrapper.getChildren().get(1)).getChildren().get(0);
            bigPlayBtn.setOnAction(e -> {
                FontIcon bigIcon = (FontIcon) bigPlayBtn.getGraphic();
                String cur = bigIcon.getIconLiteral();
                if ("fas-play".equals(cur) || cur == null) {
                    bigIcon.setIconLiteral("fas-pause");
                    bigIcon.setIconColor(Color.WHITE);
                } else {
                    bigIcon.setIconLiteral("fas-play");
                    bigIcon.setIconColor(Color.WHITE);
                }
            });
        } catch (Exception e) {
            // Ignored because bigPlayBtn is commented out
        }
    }

    public void setupDummySlider(Slider progressSlider, Label leftTime, Label rightTime, int duration) {
        progressSlider.valueProperty().addListener((obs, oldV, newV) -> {
            double max = progressSlider.getMax() <= 0 ? 100 : progressSlider.getMax();
            int curSec = (int) Math.round((newV.doubleValue() / max) * duration);
            if (curSec < 0) curSec = 0;
            int rem = duration - curSec;
            updateTimeLabel(leftTime, Duration.seconds(curSec), false);
            updateTimeLabel(rightTime, Duration.seconds(rem), true);
        });
    }

    private void updateTimeLabel(Label lbl, Duration dur, boolean isRemaining) {
        if (dur == null || dur.isUnknown()) {
            lbl.setText(isRemaining ? "-0:00" : "0:00");
            return;
        }
        int totalSeconds = (int) Math.floor(dur.toSeconds());
        if (totalSeconds < 0) totalSeconds = 0;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        String text = String.format("%d:%02d", minutes, seconds);
        if (isRemaining && !text.startsWith("-")) text = "-" + text;
        lbl.setText(text);
    }

    public Slider getVolumeSlider(HBox bottomBar) {
        for (Node n : bottomBar.getChildren()) {
            if (n instanceof Slider) return (Slider) n;
        }
        return null;
    }

    public Label getDownloadLabel(HBox bottomBar) {
        for (Node n : bottomBar.getChildren()) {
            if (n instanceof Label) {
                return (Label) n;
            }
        }
        return null;
    }

    public FontIcon getBigPlayIcon(HBox controlsWrapper) {
        try {
            Button bigPlayBtn =
                    (Button) ((StackPane) controlsWrapper.getChildren().get(1))
                            .getChildren().get(0);
            return (FontIcon) bigPlayBtn.getGraphic();
        } catch (Exception e) {
            return null;
        }
    }
}
