package com.musicplayer.scamusica;

import com.musicplayer.scamusica.controller.CodeVerificationController;
import com.musicplayer.scamusica.controller.PlayerController;
import com.musicplayer.scamusica.manager.LanguageManager;
import com.musicplayer.scamusica.manager.SessionManager;
import javafx.application.Application;
import javafx.stage.Stage;
import com.musicplayer.scamusica.util.AppLogger;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        //set prefer language from the session
        String savedLang = SessionManager.getLanguage();
        LanguageManager.setLanguage(savedLang!=null? savedLang:"en");
        if (SessionManager.isUserLoggedIn()) {
            // User already has valid token → skip login screen
            System.out.println("Auto-login using saved token");
            new PlayerController().start(primaryStage);
            return;
        }else{
            CodeVerificationController codeVerificationController = new CodeVerificationController();
            codeVerificationController.start(primaryStage);
        }

    }

    public static void main(String[] args) {
        AppLogger.init();
        launch(args);
    }
}
