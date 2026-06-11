module com.musicplayer.scamusica {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.graphics;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.fontawesome5;
    requires javafx.media;
    requires java.net.http;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.annotation;
    requires com.google.gson;
    requires java.desktop;
    requires java.prefs;
    requires uk.co.caprica.vlcj;
    requires com.sun.jna;
    requires com.sun.jna.platform;

    exports com.musicplayer.scamusica;
    /*exports com.musicplayer.scamusica.manager;
    exports com.musicplayer.scamusica.controller;
    exports com.musicplayer.scamusica.model;*/

    opens com.musicplayer.scamusica to javafx.fxml;

    exports com.musicplayer.scamusica.controller;
    opens com.musicplayer.scamusica.controller to javafx.fxml;

    opens com.musicplayer.scamusica.model to com.google.gson;
}
