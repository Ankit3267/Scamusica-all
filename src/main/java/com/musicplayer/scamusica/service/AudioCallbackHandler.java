package com.musicplayer.scamusica.service;

import com.sun.jna.Pointer;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.callback.AudioCallbackAdapter;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Handles raw PCM audio callback from LibVLC, calculates real-time peak volume
 * for Left and Right channels for the LedVuMeter visualization.
 * 
 * NOTE: Does NOT open or stream to a JavaSound SourceDataLine because LibVLC
 * handles audio output natively directly to the OS audio system.
 */
public class AudioCallbackHandler extends AudioCallbackAdapter {
    private static final int BLOCK_SIZE = 4; // 2 channels * 2 bytes per sample (16-bit)

    private final AtomicInteger volumePercent = new AtomicInteger(70);

    private volatile float leftPeak = 0.0f;
    private volatile float rightPeak = 0.0f;
    
    private byte[] buffer = new byte[16384]; // Pre-allocated buffer

    public AudioCallbackHandler() {
        super();
    }

    public void setVolume(int volume) {
        this.volumePercent.set(Math.max(0, Math.min(100, volume)));
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

        if (buffer.length < bytesCount) {
            buffer = new byte[bytesCount * 2];
        }
        samples.read(0, buffer, 0, bytesCount);
        byte[] data = buffer;

        // 1. Calculate Peak Amplitude for visualization
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

        this.leftPeak = Math.max(this.leftPeak, Math.min(1.0f, maxLeft));
        this.rightPeak = Math.max(this.rightPeak, Math.min(1.0f, maxRight));
    }

    public synchronized void pauseLine() {}

    public synchronized void resumeLine() {}

    public synchronized void flushLine() {}

    public synchronized void close() {
        leftPeak = 0.0f;
        rightPeak = 0.0f;
    }
}

