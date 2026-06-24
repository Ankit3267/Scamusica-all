package com.musicplayer.scamusica.service;

import com.musicplayer.scamusica.model.Ad;
import com.musicplayer.scamusica.util.AppLogger;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class AdScheduler {

    public interface AdScheduleListener {
        /**
         * Called when an ad is due to play
         * @param ads List of ads (1 or more) that should play at this moment
         */
        void onAdsReady(List<Ad> ads);

        /**
         * Called when scheduler encounters error
         */
        void onScheduleError(Exception ex);
    }

    private final AdScheduleListener listener;
    private final List<Ad> allAds;
    private ScheduledExecutorService scheduler;
    private volatile boolean running = false;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final ZoneId SYSTEM_ZONE = ZoneId.systemDefault();

    private final Map<Integer, LocalTime> lastPlayedTime = new ConcurrentHashMap<>();
    private final Map<String, LocalDate> lastPlayedCustom = new ConcurrentHashMap<>();

    public AdScheduler(List<Ad> ads, AdScheduleListener listener) {
        this.allAds = ads != null ? new ArrayList<>(ads) : new ArrayList<>();
        this.listener = listener;
    }

    public void start() {
        if (running) {
            AppLogger.log("[AdScheduler] Already running");
            return;
        }

        running = true;
        scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "AdScheduler");
            t.setDaemon(true);
            return t;
        });

        AppLogger.log("[AdScheduler] Starting with " + allAds.size() + " ads");

        // Run an immediate check for the current minute on startup
        scheduler.submit(this::checkAndTriggerAds);

        // Schedule periodic checks to execute precisely at the start of each wall-clock minute
        long initialDelayMs = 60000 - (System.currentTimeMillis() % 60000);
        scheduler.scheduleAtFixedRate(
                this::checkAndTriggerAds,
                initialDelayMs,
                60000,
                TimeUnit.MILLISECONDS
        );
    }

    public void stop() {
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
            AppLogger.log("[AdScheduler] Stopped");
        }
    }

    public void updateAds(List<Ad> newAds) {
        synchronized (allAds) {
            allAds.clear();
            if (newAds != null) {
                allAds.addAll(newAds);
                Set<Integer> validIds = newAds.stream()
                        .filter(ad -> ad.getId() != null)
                        .map(Ad::getId)
                        .collect(java.util.stream.Collectors.toSet());
                lastPlayedTime.keySet().retainAll(validIds);
                lastPlayedCustom.keySet().removeIf(key -> {
                    try {
                        int adId = Integer.parseInt(key.split("-")[0]);
                        return !validIds.contains(adId);
                    } catch (Exception e) {
                        return true;
                    }
                });
            } else {
                lastPlayedTime.clear();
                lastPlayedCustom.clear();
            }
        }
        AppLogger.log("[AdScheduler] Updated with " + allAds.size() + " ads");
    }

    private void checkAndTriggerAds() {
        try {
            List<Ad> dueAds = getDueAds();
            if (!dueAds.isEmpty()) {
                AppLogger.log("[AdScheduler] Due ads: " + dueAds.size());
                listener.onAdsReady(dueAds);
            }
        } catch (Exception e) {
            AppLogger.log("[AdScheduler] Error: " + e.getMessage());
            listener.onScheduleError(e);
        }
    }

    private List<Ad> getDueAds() {
        List<Ad> due = new ArrayList<>();
        LocalDate today = LocalDate.now(SYSTEM_ZONE);
        LocalTime currentTime = LocalTime.now(SYSTEM_ZONE);

        synchronized (allAds) {
            for (Ad ad : allAds) {
                if (!isAdActive(ad, today)) {
                    continue;
                }

                if (!isAdDayActive(ad, today)) {
                    continue;
                }

                if (shouldPlayNow(ad, currentTime)) {
                    due.add(ad);
                }
            }
        }

        return due;
    }

    private boolean isAdActive(Ad ad, LocalDate today) {
        if (ad.getStartDate() == null || ad.getEndDate() == null) {
            return false;
        }

        LocalDate start = parseDate(ad.getStartDate());
        LocalDate end = parseDate(ad.getEndDate());

        if (start == null || end == null) {
            return false;
        }

        return !today.isBefore(start) && !today.isAfter(end);
    }

    private boolean isAdDayActive(Ad ad, LocalDate date) {
        List<String> activeDays = ad.getActiveDays();
        if (activeDays == null || activeDays.isEmpty()) {
            return false;
        }

        String dayName = date.getDayOfWeek().toString();
        return activeDays.stream()
                .anyMatch(day -> day.toUpperCase().equals(dayName));
    }

    private boolean shouldPlayNow(Ad ad, LocalTime currentTime) {
        if ("custom".equalsIgnoreCase(ad.getScheduleType())) {
            return shouldPlayCustom(ad, currentTime);
        } else if ("interval".equalsIgnoreCase(ad.getScheduleType())) {
            return shouldPlayInterval(ad, currentTime);
        }
        return false;
    }

    private boolean shouldPlayCustom(Ad ad, LocalTime currentTime) {
        Object playTimesObj = ad.getPlayTimes();
        if (!(playTimesObj instanceof List)) {
            return false;
        }
        @SuppressWarnings("unchecked")
        List<String> playTimes = (List<String>) playTimesObj;

        if (playTimes == null || playTimes.isEmpty()) {
            return false;
        }

        LocalDate today = LocalDate.now(SYSTEM_ZONE);
        lastPlayedCustom.values().removeIf(date -> !date.equals(today));

        for (String scheduledTime : playTimes) {
            try {
                LocalTime scheduled = LocalTime.parse(scheduledTime, TIME_FORMATTER);

                if (currentTime.getHour() == scheduled.getHour() && currentTime.getMinute() == scheduled.getMinute()) {
                    String key = ad.getId() + "-" + scheduledTime;
                    LocalDate lastPlayedDate = lastPlayedCustom.get(key);
                    if (lastPlayedDate == null || !lastPlayedDate.equals(today)) {
                        AppLogger.log("[AdScheduler] Ad matched for current time: " + scheduledTime);
                        lastPlayedCustom.put(key, today);
                        return true;
                    }
                }
            } catch (Exception e) {
                AppLogger.log("[AdScheduler] Invalid time format: " + scheduledTime);
            }
        }

        return false;
    }

    private boolean shouldPlayInterval(Ad ad, LocalTime currentTime) {
        Object playTimesObj = ad.getPlayTimes();
        if (playTimesObj == null) {
            return false;
        }

        int intervalMinutes = 0;
        if (playTimesObj instanceof Integer) {
            intervalMinutes = (Integer) playTimesObj;
        } else if (playTimesObj instanceof Double) {
            intervalMinutes = ((Double) playTimesObj).intValue();
        } else {
            return false;
        }

        if (intervalMinutes <= 0) {
            return false;
        }

        LocalTime lastPlayed = lastPlayedTime.get(ad.getId());
        if (lastPlayed == null) {
            int totalMinutes = currentTime.getHour() * 60 + currentTime.getMinute();
            boolean due = totalMinutes % intervalMinutes == 0;
            if (due) lastPlayedTime.put(ad.getId(), currentTime);
            return due;
        }

        long minutesSinceLast = Duration.between(lastPlayed, currentTime).toMinutes();
        if (minutesSinceLast < 0) minutesSinceLast += 24 * 60; // midnight crossover handle

        boolean due = minutesSinceLast >= intervalMinutes;
        if (due) lastPlayedTime.put(ad.getId(), currentTime);
        return due;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) {
            return null;
        }

        try {
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            AppLogger.log("[AdScheduler] Failed to parse date: " + dateStr);
            return null;
        }
    }
}