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

    // Class fields mein add karo:
    private final Map<Integer, LocalTime> lastPlayedTime = new ConcurrentHashMap<>();

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
        scheduler = Executors.newScheduledThreadPool(1);

        AppLogger.log("[AdScheduler] Starting with " + allAds.size() + " ads");

        // Check every minute for schedule changes
        scheduler.scheduleAtFixedRate(
                this::checkAndTriggerAds,
                0,
                1,
                TimeUnit.MINUTES
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
                    continue; // Date range check failed
                }

                if (!isAdDayActive(ad, today)) {
                    continue; // Not an active day
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

        String dayName = date.getDayOfWeek().toString(); // "MONDAY", "TUESDAY", etc
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
        Object playTimesObj = ad.getPlayTimes();     // ✅ Object le lo
        if (!(playTimesObj instanceof List)) {       // ✅ Check if it's actually List
            return false;
        }
        @SuppressWarnings("unchecked")
        List<String> playTimes = (List<String>) playTimesObj;  // ✅ Safe cast

        if (playTimes == null || playTimes.isEmpty()) {
            return false;
        }

        // playTimes = ["16:15", "19:20"]
        // Check if current minute matches any scheduled time (within 1 min tolerance)
        String currentMinute = currentTime.format(TIME_FORMATTER); // "16:15"

        for (String scheduledTime : playTimes) {
            try {

                LocalTime scheduled = LocalTime.parse(scheduledTime, TIME_FORMATTER);

                long diff = Math.abs(
                        Duration.between(scheduled, currentTime).toSeconds()
                );

                AppLogger.log("[AdScheduler] Current=" + currentTime +
                        " Scheduled=" + scheduled +
                        " Diff=" + diff);

                if (diff <= 59) {
                    AppLogger.log("[AdScheduler] Ad matched for current time");
                    return true;
                }

            } catch (Exception e) {
                AppLogger.log("[AdScheduler] Invalid time format: " + scheduledTime);
            }
        }

        return false;
    }

    private boolean shouldPlayInterval(Ad ad, LocalTime currentTime) {
        // playTimes = 30 (minutes)
        // Check if current minute is divisible by interval
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

        // ✅ Last played time check karo
        LocalTime lastPlayed = lastPlayedTime.get(ad.getId());
        if (lastPlayed == null) {
            // Pehli baar — interval check karo
            int totalMinutes = currentTime.getHour() * 60 + currentTime.getMinute();
            boolean due = totalMinutes % intervalMinutes == 0;
            if (due) lastPlayedTime.put(ad.getId(), currentTime);
            return due;
        }

        // ✅ Last played ke baad se enough time gaya?
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
            // Expect "2026-05-09" format
            return LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            AppLogger.log("[AdScheduler] Failed to parse date: " + dateStr);
            return null;
        }
    }
}
