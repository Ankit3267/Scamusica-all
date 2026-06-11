package com.musicplayer.scamusica.service;

public final class InternetState {

    public enum State {
        ONLINE,
        OFFLINE,
        UNKNOWN
    }

    private static volatile State state = State.UNKNOWN;

    private InternetState() {}

    public static boolean isOnline() {
        return state == State.ONLINE;
    }

    public static boolean isOffline() {
        return state == State.OFFLINE;
    }

    public static State getState() {
        return state;
    }

    public static void markOnline() {
        if (state != State.ONLINE) {
            state = State.ONLINE;
            System.out.println("[InternetState] -> ONLINE");
        }
    }

    public static void markOffline() {
        if (state != State.OFFLINE) {
            state = State.OFFLINE;
            System.out.println("[InternetState] -> OFFLINE");
        }
    }
}
