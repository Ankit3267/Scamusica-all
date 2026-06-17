package com.musicplayer.scamusica.service;

import com.sun.jna.Pointer;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles raw PCM audio callback from LibVLC, calculates real-time peak volume
 * for Left and Right channels, scales audio data based on volume, and streams
 * audio output to JavaSound SourceDataLine.
 */
public class AudioCallbackHandler extends AudioCallbackAdapter {
    private static final int SAMPLE_RATE = 44100;
    private static final int CHANNELS = 2;
    private static final int BLOCK_SIZE = 4; // 2 channels * 2 bytes per sample (16-bit)

    private SourceDataLine line;
    private final AtomicInteger volumePercent = new AtomicInteger(85);

    private volatile float leftPeak = 0.0f;
    private volatile float rightPeak = 0.0f;

    public AudioCallbackHandler() throws Exception {
        super();
        initLine();
    }

    private synchronized void initLine() throws Exception {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, CHANNELS, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        line = (SourceDataLine) AudioSystem.getLine(info);
        // Open with a small buffer for low latency (~100ms)
        line.open(format, SAMPLE_RATE * BLOCK_SIZE / 10);
        line.start();
    }

    public void setVolume(int volume) {
        this.volumePercent.set(volume);
    }

    public float getLeftPeak() {
        float val = leftPeak;
        leftPeak = 0.0f; // Reset after reading; UI handles decay
        return val;
    }

    public float getRightPeak() {
        float val = rightPeak;
        rightPeak = 0.0f; // Reset after reading; UI handles decay
        return val;
    }

    @Override
    public void play(MediaPlayer mediaPlayer, Pointer samples, int sampleCount, long pts) {
        int bytesCount = sampleCount * BLOCK_SIZE;
        if (bytesCount <= 0) {
            return;
        }

        byte[] data = samples.getByteArray(0, bytesCount);

        // 1. Calculate Peak Amplitude (Pre-volume scaling) for visualization
        float maxLeft = 0.0f;
        float maxRight = 0.0f;

        for (int i = 0; i < bytesCount; i += 4) {
            if (i + 1 < bytesCount) {
                short leftVal = (short) ((data[i] & 0xFF) | (data[i + 1] << 8));
                float l = Math.abs(leftVal) / 32768.0f;
                if (l > maxLeft) {
                    maxLeft = l;
                }
            }
            if (i + 3 < bytesCount) {
                short rightVal = (short) ((data[i + 2] & 0xFF) | (data[i + 3] << 8));
                float r = Math.abs(rightVal) / 32768.0f;
                if (r > maxRight) {
                    maxRight = r;
                }
            }
        }

        // Boost and clamp peaks slightly for nicer visualization dynamics
        this.leftPeak = Math.max(this.leftPeak, Math.min(1.0f, maxLeft * 1.15f));
        this.rightPeak = Math.max(this.rightPeak, Math.min(1.0f, maxRight * 1.15f));

        // 2. Scale samples by the fader volume
        double vol = volumePercent.get() / 100.0;
        if (vol != 1.0) {
            for (int i = 0; i < bytesCount; i += 2) {
                short sample = (short) ((data[i] & 0xFF) | (data[i + 1] << 8));
                sample = (short) (sample * vol);
                data[i] = (byte) (sample & 0xFF);
                data[i + 1] = (byte) ((sample >> 8) & 0xFF);
            }
        }

        // 3. Write to SourceDataLine
        synchronized (this) {
            try {
                if (line == null || !line.isOpen()) {
                    initLine();
                }
                if (line != null) {
                    if (!line.isActive()) {
                        line.start();
                    }
                    line.write(data, 0, bytesCount);
                }
            } catch (Exception e) {
                // If writing fails, try to reinitialize
                try {
                    initLine();
                } catch (Exception ignored) {}
            }
        }
    }

    public synchronized void pauseLine() {
        if (line != null && line.isRunning()) {
            line.stop();
        }
    }

    public synchronized void resumeLine() {
        if (line != null && !line.isRunning()) {
            line.start();
        }
    }

    public synchronized void flushLine() {
        if (line != null) {
            line.flush();
        }
    }

    public synchronized void close() {
        if (line != null) {
            try {
                line.stop();
                line.close();
            } catch (Exception ignored) {}
            line = null;
        }
    }
}
