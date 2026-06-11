package com.musicplayer.scamusica.ui;

import com.musicplayer.scamusica.manager.LanguageManager;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * A custom popup dialog that appears when the user attempts to leave the application.
 * Provides a confirmation dialog with 'Yes' and 'No' options.
 */
public class LeavingPopup {
    public interface Callback {
        void onYes();

        void onNo();
    }

    /**
     * Displays the leaving confirmation popup.
     *
     * @param parentStage The parent stage to which this popup will be modal.
     * @param callback    The callback to handle user's response.
     */
    public void show(Stage parentStage, Callback callback) {
        StackPane overlayPane = new StackPane();
        overlayPane.setStyle("-fx-background-color: rgba(30,30,30,0.22);");

        VBox popup = new VBox(24);
        popup.setPrefWidth(370);
        popup.setPrefHeight(155);
        popup.setAlignment(Pos.CENTER);
        popup.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #ffffff, #8AB5CA);" +
                        "-fx-border-width: 0;"
        );

        Label heading = new Label();
        heading.textProperty().bind(LanguageManager.createStringBinding("label.leaving"));
        heading.setAlignment(Pos.CENTER);
        heading.setStyle("-fx-font-size: 28px; -fx-font-weight: bold; -fx-text-fill: #2c2c2c; -fx-padding: 0 0 10 0;");

        Button yesButton = new Button();
        yesButton.textProperty().bind(LanguageManager.createStringBinding("button.yes"));
        yesButton.setStyle("-fx-background-color: #222222; -fx-background-radius: 8;"
                + "-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 5 34 5 34;");
        Button noButton = new Button();
        noButton.textProperty().bind(LanguageManager.createStringBinding("button.no"));
        noButton.setStyle("-fx-background-color: linear-gradient(to bottom, #ffffff, #8AB5CA);  " +
                "-fx-background-radius: 8;"
                + "-fx-text-fill: black; -fx-font-size: 16px; -fx-font-weight: bold; -fx-padding: 5 34 5 34;");

        HBox buttons = new HBox(18, yesButton, noButton);
        buttons.setAlignment(Pos.CENTER);

        popup.getChildren().addAll(heading, buttons);

        overlayPane.getChildren().add(popup);

        Scene popupScene = new Scene(overlayPane, 390, 170);
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.WINDOW_MODAL);
        popupStage.initOwner(parentStage);
        popupStage.initStyle(StageStyle.TRANSPARENT);
        popupStage.setScene(popupScene);
        popupStage.setResizable(false);

        popupScene.setFill(javafx.scene.paint.Color.TRANSPARENT);

        yesButton.setOnAction(e -> {
            popupStage.close();
            if (callback != null) callback.onYes();
        });
        noButton.setOnAction(e -> {
            popupStage.close();
            if (callback != null) callback.onNo();
        });

        popupStage.setOnShown(e -> {
            double x = parentStage.getX() + (parentStage.getWidth() - popupScene.getWidth()) / 2;
            double y = parentStage.getY() + (parentStage.getHeight() - popupScene.getHeight()) / 2;
            popupStage.setX(x);
            popupStage.setY(y);
        });

        popupStage.showAndWait();
    }
}
