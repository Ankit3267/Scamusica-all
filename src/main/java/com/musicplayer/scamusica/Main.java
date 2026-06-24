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
        // set prefer language from the session
        String savedLang = SessionManager.getLanguage();
        LanguageManager.setLanguage(savedLang != null ? savedLang : "en");
        if (SessionManager.isUserLoggedIn()) {
            // User already has valid token → skip login screen
            System.out.println("Auto-login using saved token");
            new PlayerController().start(primaryStage);
            return;
        } else {
            CodeVerificationController codeVerificationController = new CodeVerificationController();
            codeVerificationController.start(primaryStage);
        }

    }

    public static void main(String[] args) {
        AppLogger.init();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            AppLogger.log("[Main] Uncaught Exception in thread " + thread.getName() + ": " + throwable.getMessage());
            throwable.printStackTrace();

            boolean isJnaError = false;
            Throwable cause = throwable;
            while (cause != null) {
                String msg = cause.getMessage();
                String str = cause.toString();
                if ((msg != null && msg.contains("JNA")) || (str != null && str.contains("JNA"))) {
                    isJnaError = true;
                    break;
                }
                cause = cause.getCause();
            }

            if (isJnaError) {
                AppLogger.log("[Main] JNA error detected. Restarting application...");
                try {
                    String[] possiblePaths = {
                            System.getProperty("user.home") + java.io.File.separator + "scamusica"
                                    + java.io.File.separator + "restart_scamusica.sh",
                            System.getProperty("user.dir") + java.io.File.separator + "scripts" + java.io.File.separator
                                    + "restart_scamusica.sh",
                            System.getProperty("user.dir") + java.io.File.separator + "restart_scamusica.sh",
                            "/opt/scamusica/bin/restart_scamusica.sh",
                            "/opt/scamusica/lib/app/restart_scamusica.sh"
                    };

                    java.io.File scriptFile = null;
                    for (String path : possiblePaths) {
                        java.io.File f = new java.io.File(path);
                        if (f.exists() && f.canExecute()) {
                            scriptFile = f;
                            break;
                        }
                    }

                    if (scriptFile != null) {
                        AppLogger.log("[Main] Launching restart script: " + scriptFile.getAbsolutePath());
                        new ProcessBuilder(scriptFile.getAbsolutePath()).start();
                    } else {
                        AppLogger.log(
                                "[Main] Restart script not found or not executable. Relying on systemd Restart=always if configured.");
                    }
                } catch (Exception e) {
                    AppLogger.log("[Main] Failed to launch restart script: " + e.getMessage());
                }

                new Thread(() -> {
                    try {
                        Thread.sleep(3000);
                    } catch (Exception ignored) {
                    }
                    AppLogger.log("[Main] Exiting JVM now due to JNA error.");
                    AppLogger.close();
                    System.exit(1);
                }).start();
            }
        });

        launch(args);
    }
}
