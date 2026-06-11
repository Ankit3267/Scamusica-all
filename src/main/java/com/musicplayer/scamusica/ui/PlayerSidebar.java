package com.musicplayer.scamusica.ui;

/**
 * Handles the creation and management of the sidebar navigation in the music player.
 * Provides methods to create styled sidebar buttons and manage their active states.
 */

import com.musicplayer.scamusica.controller.CodeVerificationController;
import com.musicplayer.scamusica.manager.SessionManager;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import java.util.List;

public class PlayerSidebar {
    /**
     * Creates a styled icon button for the sidebar with the specified icon.
     * 
     * @param iconLiteral The icon literal from the icon font library
     * @return A styled Button with the specified icon
     */
    public Button createIconButton(String iconLiteral) {
        FontIcon icon = new FontIcon(iconLiteral);
        icon.setIconSize(19);
        Button button = new Button("", icon);
        button.getStyleClass().add("sidebar-button");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    /**
     * Adds interaction logic to sidebar buttons, managing active state styling.
     * 
     * @param buttons List of all sidebar buttons
     * @param activeButton The initially active button
     */
    public void addSidebarLogic(List<Button> buttons, Button activeButton) {
        activeButton.getStyleClass().add("sidebar-active");
        buttons.forEach(b -> b.setOnAction(e -> {
            buttons.forEach(x -> x.getStyleClass().remove("sidebar-active"));
            b.getStyleClass().add("sidebar-active");
        }));
    }

    /**
     * Creates the top section of the sidebar containing the main navigation buttons.
     * 
     * @param headphonesButton The main navigation button
     * @return VBox containing the top section of the sidebar
     */
    public VBox createSidebarTop(Button headphonesButton) {
        VBox sidebarTop = new VBox(20, headphonesButton);
        sidebarTop.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        return sidebarTop;
    }

    /**
     * Creates a settings icon for the bottom of the sidebar.
     * Which opens a Dialog for leaving the Application
     * 
     * @return Configured FontIcon for settings
     */
    public FontIcon createSettingsIcon(Stage parentStage, Runnable onLeaveAction) {
        FontIcon icon = new FontIcon("fas-cog");
        icon.getStyleClass().add("settings-icon");
        icon.setOnMouseClicked(e -> {
            LeavingPopup popup = new LeavingPopup();
            popup.show(parentStage, new LeavingPopup.Callback() {
                public void onYes() {
                    SessionManager.clearToken();

                    if (onLeaveAction != null) {
                        onLeaveAction.run();
                    }

                    Platform.runLater(() -> {
                        try {
                            CodeVerificationController controller = new CodeVerificationController();
                            controller.start(parentStage);
                        } catch (Exception e) {
                            e.printStackTrace();
                            Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to open Verification screen.");
                            alert.initOwner(parentStage);
                            alert.showAndWait();
                        }
                    });

                }
                public void onNo() {
                    // Do nothing, Popup will close on clicked at No button
                }
            });
        });
        return icon;
    }

    /**
     * Assembles the complete sidebar with top and bottom sections.
     * 
     * @param sidebarTop The top section of the sidebar
     * @param settingsIcon The settings icon for the bottom
     * @return Complete VBox containing the sidebar
     */
    public VBox createSidebar(VBox sidebarTop, FontIcon settingsIcon) {
        VBox sidebar = new VBox(20);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPadding(new javafx.geometry.Insets(30, 20, 30, 20));
        sidebar.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        Region spacerLeft = new Region();
        VBox.setVgrow(spacerLeft, Priority.ALWAYS);
        sidebar.getChildren().addAll(sidebarTop, spacerLeft, settingsIcon);
        return sidebar;
    }
}
