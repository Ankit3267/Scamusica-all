package com.musicplayer.scamusica.service;

import com.musicplayer.scamusica.util.ApiClient;
import com.musicplayer.scamusica.util.Utility;

import java.util.function.Consumer;

public class ConnectivityMonitor {

    public enum Status { ONLINE, OFFLINE }

    private volatile boolean running = false;
    private final Consumer<Status> listener;
    private Status lastStatus = null;

    public ConnectivityMonitor(Consumer<Status> listener) {
        this.listener = listener;
    }

    public void start() {
        running = true;
        new Thread(this::loop, "ConnectivityMonitor").start();
    }

    public void stop() {
        running = false;
    }

    private void loop() {
        while (running) {
            Status current = checkApiConnectivity();

            if (current != lastStatus) {
                lastStatus = current;
                listener.accept(current);
            }

            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {}
        }
    }

    private Status checkApiConnectivity() {
        try {
            // ApiClient use karo — yeh bundled JRE ke certs use karta hai
            // isliye Windows pe bhi SSL sahi kaam karega
            ApiClient.get(Utility.BASE_URL.get(), null);
            return Status.ONLINE;
        } catch (Exception e) {
            System.out.println("[ConnectivityMonitor] Connectivity check failed: " + e.getClass().getSimpleName());
            return Status.OFFLINE;
        }
    }
}
