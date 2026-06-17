package com.musicplayer.scamusica.ui;

import com.musicplayer.scamusica.service.AudioCallbackHandler;
import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

/**
 * A real-time stereo LED VU meter visualization component.
 * Uses a Canvas for high-performance rendering and an AnimationTimer for smooth,
 * frame-rate independent updates, decay fall-off, and peak-hold indicators.
 */
public class LedVuMeter extends Canvas {
    private AudioCallbackHandler audioCallbackHandler;

    private float currentLeft = 0.0f;
    private float currentRight = 0.0f;

    private float peakLeft = 0.0f;
    private float peakRight = 0.0f;

    private double leftPeakHoldTime = 0.0;
    private double rightPeakHoldTime = 0.0;

    // Configuration constants
    private final float decayRate = 1.8f;          // Rate at which levels fall back down (units per second)
    private final float peakDecayRate = 0.8f;      // Rate at which peak dots fall back down (units per second)
    private final double peakHoldDuration = 0.45;  // How long peak dots float before starting to fall (seconds)
    private final int numSegments = 15;            // Number of LED blocks per channel

    private final AnimationTimer timer;

    public LedVuMeter() {
        // Default size of the VU meter widget
        setWidth(110);
        setHeight(18);

        this.timer = new AnimationTimer() {
            private long lastUpdate = 0;

            @Override
            public void handle(long now) {
                if (lastUpdate == 0) {
                    lastUpdate = now;
                    return;
                }
                long elapsedNanos = now - lastUpdate;
                lastUpdate = now;

                double dt = elapsedNanos / 1_000_000_000.0;

                // Read real-time peaks from the audio callback
                float targetLeft = 0.0f;
                float targetRight = 0.0f;

                if (audioCallbackHandler != null) {
                    targetLeft = audioCallbackHandler.getLeftPeak();
                    targetRight = audioCallbackHandler.getRightPeak();
                }

                // Smooth decay logic for main level indicators
                if (targetLeft > currentLeft) {
                    currentLeft = targetLeft;
                } else {
                    currentLeft -= decayRate * dt;
                    if (currentLeft < 0.0f) {
                        currentLeft = 0.0f;
                    }
                }

                if (targetRight > currentRight) {
                    currentRight = targetRight;
                } else {
                    currentRight -= decayRate * dt;
                    if (currentRight < 0.0f) {
                        currentRight = 0.0f;
                    }
                }

                // Peak hold and decay logic for Left Channel
                if (targetLeft > peakLeft) {
                    peakLeft = targetLeft;
                    leftPeakHoldTime = 0.0;
                } else {
                    leftPeakHoldTime += dt;
                    if (leftPeakHoldTime >= peakHoldDuration) {
                        peakLeft -= peakDecayRate * dt;
                        if (peakLeft < currentLeft) {
                            peakLeft = currentLeft;
                        }
                    }
                }

                // Peak hold and decay logic for Right Channel
                if (targetRight > peakRight) {
                    peakRight = targetRight;
                    rightPeakHoldTime = 0.0;
                } else {
                    rightPeakHoldTime += dt;
                    if (rightPeakHoldTime >= peakHoldDuration) {
                        peakRight -= peakDecayRate * dt;
                        if (peakRight < currentRight) {
                            peakRight = currentRight;
                        }
                    }
                }

                draw();
            }
        };

        // Initialize display to empty state
        draw();
    }

    public void setAudioCallbackHandler(AudioCallbackHandler handler) {
        this.audioCallbackHandler = handler;
    }

    public void start() {
        timer.start();
    }

    public void stop() {
        timer.stop();
        // Clear visualization values
        currentLeft = 0.0f;
        currentRight = 0.0f;
        peakLeft = 0.0f;
        peakRight = 0.0f;
        draw();
    }

    private void draw() {
        GraphicsContext gc = getGraphicsContext2D();
        double w = getWidth();
        double h = getHeight();

        // Clear previous drawing
        gc.clearRect(0, 0, w, h);

        double rowSpacing = 3.0;
        double rowHeight = (h - rowSpacing) / 2.0;

        // Draw Left Channel (top row)
        drawChannel(gc, 0, rowHeight, currentLeft, peakLeft);
        // Draw Right Channel (bottom row)
        drawChannel(gc, rowHeight + rowSpacing, rowHeight, currentRight, peakRight);
    }

    private void drawChannel(GraphicsContext gc, double y, double height, float level, float peak) {
        double w = getWidth();
        double segSpacing = 1.5;
        double segWidth = (w - (numSegments - 1) * segSpacing) / numSegments;

        int activeSegments = Math.round(level * numSegments);
        int peakSegment = Math.round(peak * numSegments) - 1;

        for (int i = 0; i < numSegments; i++) {
            double x = i * (segWidth + segSpacing);

            // Custom Color Logic based on percentage thresholds:
            // 0 - 60% (indices 0 to 8) -> Green
            // 60% - 85% (indices 9 to 12) -> Yellow
            // 85% - 100% (indices 13 to 14) -> Red
            Color baseColor;
            double percent = (double) (i + 1) / numSegments;
            if (percent <= 0.60) {
                baseColor = Color.web("#2ecc71"); // Premium Neon Green
            } else if (percent <= 0.85) {
                baseColor = Color.web("#f1c40f"); // Warm Amber Yellow
            } else {
                baseColor = Color.web("#e74c3c"); // Vibrant Crimson Red
            }

            boolean isActive = i < activeSegments;
            boolean isPeakDot = (i == peakSegment && peakSegment >= 0);

            if (isActive) {
                // Lit LED segment
                gc.setFill(baseColor);
                gc.fillRoundRect(x, y, segWidth, height, 1.5, 1.5);
            } else if (isPeakDot) {
                // Peak indicator dot (extra bright pink-red)
                gc.setFill(Color.web("#ff1744"));
                gc.fillRoundRect(x, y, segWidth, height, 1.5, 1.5);
            } else {
                // Unlit LED segment (very faint opacity for hardware glow/display mesh effect)
                gc.setFill(baseColor.deriveColor(0, 1, 1, 0.12));
                gc.fillRoundRect(x, y, segWidth, height, 1.5, 1.5);
            }
        }
    }
}
