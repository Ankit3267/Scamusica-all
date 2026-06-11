package com.musicplayer.scamusica.ui;

import com.musicplayer.scamusica.manager.LanguageManager;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Rectangle;
import org.kordamp.ikonli.javafx.FontIcon;

public class PlayerAlbum {
    private Label songCountLabel;

    /**
     * Creates the album heading label with default styling.
     *
     * @return Styled Label for the album heading
     */
    public Label createAlbumHeading() {
        return createAlbumHeading("Escuchando Top 40");
    }

    /**
     * Overload to allow custom initial heading text.
     *
     * @param title Initial heading text
     * @return Styled Label for the album heading
     */
    public Label createAlbumHeading(String title) {
        Label albumHeading = new Label(title);
        albumHeading.getStyleClass().add("heading");
        albumHeading.setAlignment(javafx.geometry.Pos.CENTER);
        return albumHeading;
    }

    /**
     * Creates and configures an ImageView for displaying album art.
     *
     * @param clazz The class used for resource loading
     * @return Configured ImageView with the album art
     */
    public ImageView createAlbumImage(Class<?> clazz) {
        ImageView img = new ImageView();
        try {
            Image imgResource = clazz.getResource("/images/album_cover.png") == null ? null :
                    new Image(clazz.getResource("/images/album_cover.png").toExternalForm());
            if (imgResource != null) img.setImage(imgResource);
        } catch (Exception ignored) {
        }
        img.setPreserveRatio(true);
        img.getStyleClass().add("album-art-left");
        return img;
    }

    /**
     * Applies a rounded rectangle clip to an ImageView to create rounded corners.
     *
     * @param img The ImageView to apply the clip to
     */
    public void applyClip(ImageView img) {
        Rectangle clip = new Rectangle();
        clip.setArcWidth(18);
        clip.setArcHeight(18);
        clip.widthProperty().bind(img.fitWidthProperty());
        clip.heightProperty().bind(img.fitHeightProperty());
        img.setClip(clip);
    }

    /**
     * Creates a horizontal box containing a music icon and song count.
     *
     * @return HBox containing the song count display
     */
    public HBox createSongsBox() {
        FontIcon musicIcon = new FontIcon("fas-music");
        musicIcon.setIconSize(16);
        musicIcon.getStyleClass().add("music-note-icon");

        songCountLabel = new Label();
        songCountLabel.textProperty().bind(Bindings.concat("0 ", LanguageManager.createStringBinding("label.songs")));
        songCountLabel.getStyleClass().add("songs-label-left");
        HBox songsBox = new HBox(8, musicIcon, songCountLabel);
        songsBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        songsBox.setPadding(new Insets(6, 0, 0, 6));
        return songsBox;
    }

    /**
     * Update the song count label text (call from UI thread or wrap in Platform.runLater).
     *
     * @param count the number of downloaded songs to show
     */
    public void setSongCount(int count) {
        if (songCountLabel == null) return;
        songCountLabel.textProperty().bind(Bindings.concat(count+ " ", LanguageManager.createStringBinding("label.songs")));

    }

    /**
     * Returns the internal song count label (if caller wants to do more with it).
     */
    public Label getSongCountLabel() {
        return songCountLabel;
    }

    /**
     * Creates a vertical box containing album information including heading, image, and song count.
     *
     * @param albumHeading The label containing the album title
     * @param img          The ImageView containing the album art
     * @param songsBox     The HBox containing song count information
     * @return VBox containing all album information
     */
    public VBox createLeftAlbumVBox(Label albumHeading, ImageView img, HBox songsBox) {
        VBox leftAlbumVBox = new VBox(8, albumHeading, img, songsBox);
        leftAlbumVBox.setAlignment(javafx.geometry.Pos.TOP_LEFT);
        HBox.setMargin(leftAlbumVBox, new Insets(2, 0, 5, 80));
        leftAlbumVBox.setPrefWidth(340);
        leftAlbumVBox.setMaxWidth(520);
        leftAlbumVBox.getStyleClass().add("left-album");
        return leftAlbumVBox;
    }

    /**
     * Creates the top row of the player interface containing album info, center content, and playlist header.
     *
     * @param leftAlbumVBox     The VBox containing album information
     * @param centerContainer   The VBox containing center content
     * @param playlistHeaderBox The HBox containing playlist header controls
     * @return HBox containing all top row components
     */
    public HBox createTopRow(VBox leftAlbumVBox, VBox centerContainer, HBox playlistHeaderBox) {
        HBox topRow = new HBox(24, leftAlbumVBox, centerContainer, playlistHeaderBox);
        topRow.setAlignment(javafx.geometry.Pos.TOP_CENTER);
        topRow.setPadding(new Insets(20, 18, 6, 18));
        HBox.setHgrow(centerContainer, Priority.ALWAYS);
        return topRow;
    }

    public void bindImageSize(ImageView img, Scene scene, HBox progressRow, Label titleCentered) {
        img.fitWidthProperty().bind(Bindings.createDoubleBinding(() -> {
            double w = scene.getWidth() * 0.27;
            double h = scene.getHeight() * 0.35; // Don't exceed 35% of window height
            double size = Math.min(w, h);
            if (size < 120) size = 120; // Reduced min size for small screens
            if (size > 520) size = 520;
            return size;
        }, scene.widthProperty(), scene.heightProperty()));
        img.fitHeightProperty().bind(img.fitWidthProperty());
        titleCentered.prefWidthProperty().bind(progressRow.widthProperty());
    }
}
