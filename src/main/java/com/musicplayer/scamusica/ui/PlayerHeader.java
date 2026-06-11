package com.musicplayer.scamusica.ui;

/**
 * Handles the creation and management of the player's header section.
 * Contains version information, application logo, and user controls.
 */

import com.musicplayer.scamusica.manager.LanguageManager;
import com.musicplayer.scamusica.manager.SessionManager;
import com.musicplayer.scamusica.service.NetworkMonitor;
import javafx.beans.binding.Bindings;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;

public class PlayerHeader {
    /**
     * Creates the left metadata section containing version, ID, and support button.
     *
     * @return HBox containing the left-aligned metadata elements
     */
    public HBox createLeftMeta() {
        HBox leftMeta = new HBox(14);
        leftMeta.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        Label versionLbl = new Label();
        versionLbl.textProperty().bind(
                Bindings.concat(
                        LanguageManager.createStringBinding("label.version")," 11"
                )
        );
        versionLbl.getStyleClass().add("meta-text");
        Label idLbl = new Label("ID: "+ (SessionManager.isUserLoggedIn()? SessionManager.getUserId():null));
        idLbl.getStyleClass().add("meta-text");
        Button supportBtn = new Button();
        supportBtn.textProperty().bind(LanguageManager.createStringBinding("button.support"));
        supportBtn.getStyleClass().add("support-pill");
        leftMeta.getChildren().addAll(versionLbl, idLbl, supportBtn);
        return leftMeta;
    }

    /**
     * Creates and configures the application logo view.
     *
     * @param clazz The class used for resource loading
     * @return ImageView containing the application logo
     */
    public ImageView createLogoView(Class<?> clazz) {
        ImageView logoView = new ImageView();
        try {
            Image logo = clazz.getResource("/images/logo.png") == null ? null :
                    new Image(clazz.getResource("/images/logo.png").toExternalForm());
            if (logo != null) logoView.setImage(logo);
        } catch (Exception ignored) {}
        logoView.setPreserveRatio(true);
        logoView.setFitHeight(50);
        return logoView;
    }

    /**
     * Creates the right metadata section showing online status.
     *
     * @return HBox containing the right-aligned online status indicator
     */
//    public HBox createRightMeta() {
//        Label onlineLbl = new Label();
//        onlineLbl.textProperty().bind(LanguageManager.createStringBinding("label.online"));
//        onlineLbl.setStyle("-fx-color: #000000;" + "-fx-font-size: 14px;");
//        HBox rightMeta = new HBox(8, new javafx.scene.shape.Circle(8, javafx.scene.paint.Color.web("#248924")), onlineLbl);
//        rightMeta.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
//        return rightMeta;
//    }

    public HBox createRightMeta() {
        javafx.scene.shape.Circle statusCircle =
                new javafx.scene.shape.Circle(8, javafx.scene.paint.Color.web("#ef4444"));

        Label onlineLbl = new Label();
        onlineLbl.setStyle("-fx-color: #000000; -fx-font-size: 14px;");

        NetworkMonitor monitor = NetworkMonitor.getInstance();

        // ✅ Circle + Label dono update karne ka ek Runnable
        Runnable updateUI = () -> {
            boolean isOnline = monitor.isOnline();
            statusCircle.setFill(
                    isOnline
                            ? javafx.scene.paint.Color.web("#248924")  // green
                            : javafx.scene.paint.Color.web("#ef4444")  // red
            );
            String key = isOnline ? "label.online" : "label.offline";
            onlineLbl.textProperty().bind(LanguageManager.createStringBinding(key));
        };

        // ✅ Listener — jab bhi change ho
        monitor.onlineProperty().addListener((obs, old, now) ->
                javafx.application.Platform.runLater(updateUI)
        );

        // ✅ Initial state — monitor start hone ke baad check karo
        // Platform.runLater se guarantee hoga ki monitor.start() ho chuka hai
        javafx.application.Platform.runLater(updateUI);

        HBox rightMeta = new HBox(8, statusCircle, onlineLbl);
        rightMeta.setAlignment(javafx.geometry.Pos.CENTER_RIGHT);
        return rightMeta;
    }

    /**
     * Creates the main header container with left, center, and right sections.
     *
     * @param left Left section containing metadata
     * @param center Center section containing the logo
     * @param right Right section containing status indicators
     * @return BorderPane containing the complete header
     */
    public BorderPane createHeader(HBox left, ImageView center, HBox right) {
        BorderPane header = new BorderPane();
        header.getStyleClass().add("app-header");
        header.setLeft(left);
        header.setCenter(center);
        header.setRight(right);
        header.setPadding(new javafx.geometry.Insets(20, 40, 20, 40));
        return header;
    }

    /**
     * Creates the main title label for the player.
     *
     * @return Label displaying the current track information
     */
    public Label createPlayerTitle() {
        Label titleCentered = new Label("Escuchando Top 40");
        titleCentered.getStyleClass().add("player-title");
        titleCentered.setAlignment(javafx.geometry.Pos.CENTER);
        return titleCentered;
    }

    /**
     * Creates a centered container for the player title.
     *
     * @param titleCentered The label to be centered
     * @return VBox containing the centered title
     */
    public VBox createCenterContainer(Label titleCentered) {
        VBox centerContainer = new VBox();
        centerContainer.setAlignment(javafx.geometry.Pos.CENTER);
        centerContainer.getChildren().add(titleCentered);
        return centerContainer;
    }

    /**
     * Loads custom fonts for the application.
     *
     * @param clazz The class used for resource loading
     */
    public void loadFonts(Class<?> clazz) {
        try {
            Font.loadFont(clazz.getResourceAsStream("/fonts/Poppins-Regular.ttf"), 12);
            Font.loadFont(clazz.getResourceAsStream("/fonts/Poppins-Bold.ttf"), 12);

            // Fallback fonts for multilingual support
            Font.loadFont(clazz.getResourceAsStream("/fonts/NotoSans-Regular.ttf"), 12);
            Font.loadFont(clazz.getResourceAsStream("/fonts/NotoSansArabic-Regular.ttf"), 12);
            Font.loadFont(clazz.getResourceAsStream("/fonts/NotoSansDevanagari-Regular.ttf"), 12);
            Font.loadFont(clazz.getResourceAsStream("/fonts/NotoSansJP-Regular.ttf"), 12);
            Font.loadFont(clazz.getResourceAsStream("/fonts/NotoSansSC-Regular.ttf"), 12);
            Font.loadFont(clazz.getResourceAsStream("/fonts/NotoSansTC-Regular.ttf"), 12);

        } catch (Exception ex) {}
    }
}
