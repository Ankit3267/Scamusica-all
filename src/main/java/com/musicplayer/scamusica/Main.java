package com.musicplayer.scamusica;

import com.musicplayer.scamusica.controller.CodeVerificationController;
import com.musicplayer.scamusica.controller.PlayerController;
import com.musicplayer.scamusica.manager.LanguageManager;
import com.musicplayer.scamusica.manager.SessionManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import java.util.ResourceBundle;
import java.util.Locale;

import com.musicplayer.scamusica.service.LogSyncService;
import com.musicplayer.scamusica.util.AppLogger;

public class Main extends Application {
    @Override
    public void start(Stage primaryStage) {
        // 1. Show splash immediately
        Stage splashStage = createSplashStage();
        splashStage.show();

        // Setup VLC JNA path
        String appDir = System.getProperty("user.dir");
        String vlcPath = appDir + java.io.File.separator + "vlc";
        System.setProperty("jna.library.path", vlcPath);

        // 2. Run heavy initialization on a background thread so the splash
        //    has time to render before we proceed to the main UI.
        new Thread(() -> {
            try {
                // Pre-load session data off the FX thread
                String savedLang = SessionManager.getLanguage();
                String langToUse = savedLang != null ? savedLang : "en";
                boolean isLoggedIn = SessionManager.isUserLoggedIn();

                // Switch back to FX thread to build & show the real UI
                Platform.runLater(() -> {
                    try {
                        LanguageManager.setLanguage(langToUse);

                        if (isLoggedIn) {
                            System.out.println("Auto-login using saved token");
                            new PlayerController().start(primaryStage);
                        } else {
                            CodeVerificationController codeVerificationController = new CodeVerificationController();
                            codeVerificationController.start(primaryStage);
                        }
                    } catch (Exception e) {
                        AppLogger.log("[Main] Failed to start application: " + e.getMessage());
                        e.printStackTrace();
                    } finally {
                        // 3. Close splash after real UI is shown
                        splashStage.close();
                    }
                });
            } catch (Exception e) {
                AppLogger.log("[Main] Background init failed: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(splashStage::close);
            }
        }, "Splash-Init-Thread").start();
    }

    private Stage createSplashStage() {
        Stage splashStage = new Stage();
        // FIX: Only call initStyle() ONCE. TRANSPARENT already includes UNDECORATED behavior.
        // Calling initStyle() twice throws IllegalStateException on most packaged JREs,
        // causing the window to remain opaque/black on client devices.
        splashStage.initStyle(StageStyle.TRANSPARENT);

        VBox root = new VBox(20);
        root.setAlignment(Pos.CENTER);
        // Dark gradient matching the app's theme
        root.setStyle("-fx-background-color: linear-gradient(to bottom right, #1e1e1e, #0a0a0a); " +
                      "-fx-border-color: #333333; -fx-border-width: 1px; -fx-background-radius: 10px; -fx-border-radius: 10px;");
        root.setPrefSize(400, 300);

        // FIX: Use getResource().toExternalForm() instead of getResourceAsStream().
        // getResourceAsStream("/images/logo.png") returns null when running as a
        // Java module (packaged via jpackage) because the resource package isn't
        // opened in module-info.java. getResource() works consistently in both
        // classpath mode (IntelliJ) and modular mode (client devices).
        try {
            java.net.URL logoUrl = getClass().getResource("/images/logo.png");
            if (logoUrl != null) {
                ImageView logoView = new ImageView(new Image(logoUrl.toExternalForm()));
                logoView.setFitWidth(150);
                logoView.setPreserveRatio(true);
                root.getChildren().add(logoView);
            } else {
                AppLogger.log("[Main] logo.png not found in resources (getResource returned null)");
            }
        } catch (Exception e) {
            AppLogger.log("[Main] Could not load logo for splash screen: " + e.getMessage());
        }

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.setStyle("-fx-progress-color: #1DB954;"); // Spotify-like green or app theme color
        spinner.setMaxSize(40, 40);
        // Fix direction: Scale X by -1 to mirror it and make it spin counter-clockwise
        spinner.setScaleX(-1);

        Label messageLabel = new Label();
        messageLabel.setTextFill(Color.WHITE);
        messageLabel.setStyle("-fx-font-family: 'Arial'; -fx-font-size: 14px;");
        
        // Fast localized string loading
        try {
            String savedLang = SessionManager.getLanguage();
            Locale loc = new Locale(savedLang != null ? savedLang : "es");
            ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", loc);
            messageLabel.setText(bundle.getString("splash.loading"));
        } catch (Exception e) {
            messageLabel.setText("La aplicación se está iniciando. Por favor, espere");
        }

        root.getChildren().addAll(spinner, messageLabel);

        Scene scene = new Scene(root, 400, 300);
        scene.setFill(Color.TRANSPARENT);
        splashStage.setScene(scene);
        splashStage.centerOnScreen();

        return splashStage;
    }

    public static void main(String[] args) {
        AppLogger.init();

        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            AppLogger.log("[Main] Uncaught Exception in thread " + thread.getName() + ": " + throwable.getMessage());
            try {
                com.musicplayer.scamusica.service.LogSyncService.getInstance().addErrorLog(
                        "Crash: " + throwable.getMessage(), "Main UncaughtExceptionHandler");
            } catch (Exception ex) {
                // Ignore sync errors during crash
            }
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
