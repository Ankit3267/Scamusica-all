package com.musicplayer.scamusica.ui;

/**
 * Handles the creation and management of dropdown menus in the player interface.
 * Manages playlist selection dropdowns with custom styling and behavior.
 */

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class PlayerDropdown {
    /**
     * Creates a styled playlist selection pill with a dropdown indicator.
     *
     * @param currentSelectionText The text to display as the current selection
     * @return HBox containing the styled playlist pill
     */
    public HBox createPlaylistPill(String currentSelectionText) {
        Label selLabel = new Label(currentSelectionText);
        selLabel.getStyleClass().add("playlist-visual-label");
        selLabel.setMaxWidth(Double.MAX_VALUE);
        selLabel.setWrapText(false);
        HBox.setHgrow(selLabel, Priority.ALWAYS);

        StackPane chevronCircle = new StackPane();
        chevronCircle.getStyleClass().add("playlist-chevron-circle");

        FontIcon chevronIcon = new FontIcon("fas-chevron-down");
        chevronIcon.setIconSize(12);
        chevronIcon.getStyleClass().add("playlist-chevron-icon");
        chevronCircle.getChildren().add(chevronIcon);

        HBox pill = new HBox();
        pill.getStyleClass().add("playlist-visual");
        pill.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        pill.setPrefWidth(500);
        pill.setMaxWidth(500);
        pill.setMinWidth(500);
        pill.setPrefHeight(30);
        pill.setMaxHeight(30);
        pill.setMinHeight(30);
        pill.setSpacing(8);
        pill.setPadding(new Insets(4, 8, 4, 12));
        pill.getChildren().addAll(selLabel, chevronCircle);
        return pill;
    }

    /**
     * Creates a dropdown card containing a list of selectable items.
     *
     * @param viewItems        The observable list of items to display in the dropdown
     * @param currentSelection Array containing the currently selected item
     * @param masterList       The complete list of all available items
     * @param pill             The parent pill that triggers this dropdown
     * @return VBox containing the dropdown list
     */
    public VBox createDropdownCard(ObservableList<String> viewItems,
                                   String[] currentSelection,
                                   List<String> masterList,
                                   HBox pill) {
        VBox dropdownCard = new VBox();
        dropdownCard.getStyleClass().add("dropdown-card");
        dropdownCard.setVisible(false);
        dropdownCard.setManaged(false);

        ListView<String> dropdownList = new ListView<>(viewItems);
        dropdownList.getStyleClass().add("dropdown-list");
        dropdownList.setPrefHeight(220);

        dropdownCard.getChildren().add(dropdownList);
        return dropdownCard;
    }

    /**
     * Creates a header box containing the playlist selection pill.
     *
     * @param pill The playlist pill to include in the header
     * @return HBox containing the playlist header
     */
    public HBox createPlaylistHeaderBox(HBox pill) {
        HBox playlistHeaderBox = new HBox();
        playlistHeaderBox.setAlignment(javafx.geometry.Pos.TOP_RIGHT);
        playlistHeaderBox.setPadding(new Insets(12, 24, 0, 0));
        playlistHeaderBox.setPrefWidth(380);
        playlistHeaderBox.setMinWidth(380);
        playlistHeaderBox.setMaxWidth(380);
        playlistHeaderBox.setPrefHeight(56);
        playlistHeaderBox.setMinHeight(56);
        playlistHeaderBox.setMaxHeight(56);
        playlistHeaderBox.getChildren().add(pill);
        HBox.setHgrow(pill, Priority.NEVER);
        return playlistHeaderBox;
    }

    /**
     * OLD SIGNATURE – kept for backward compatibility.
     * Calls the new handler with null playlist-change callback.
     */
    public void setupDropdownHandlers(HBox pill,
                                      VBox dropdownCard,
                                      List<String> masterList,
                                      ObservableList<String> viewItems,
                                      String[] currentSelection,
                                      Scene scene,
                                      Pane rootOverlay,
                                      ImageView img,
                                      Consumer<VBox> hideDropdown) {
        setupDropdownHandlers(
                pill,
                dropdownCard,
                masterList,
                viewItems,
                currentSelection,
                scene,
                rootOverlay,
                img,
                hideDropdown,
                null
        );
    }

    /**
     * Sets up mouse event handlers for the dropdown functionality.
     *
     * @param pill              The pill that triggers the dropdown
     * @param dropdownCard      The dropdown card to show/hide
     * @param masterList        Complete list of all available items
     * @param viewItems         Observable list of items currently in view
     * @param currentSelection  Array containing the currently selected item
     * @param scene             The application scene
     * @param rootOverlay       The root overlay pane
     * @param img               Reference image for positioning
     * @param hideDropdown      Callback to hide the dropdown
     * @param onPlaylistChanged Callback fired when a playlist is selected (may be null)
     */
    public void setupDropdownHandlers(HBox pill,
                                      VBox dropdownCard,
                                      List<String> masterList,
                                      ObservableList<String> viewItems,
                                      String[] currentSelection,
                                      Scene scene,
                                      Pane rootOverlay,
                                      ImageView img,
                                      Consumer<VBox> hideDropdown,
                                      Consumer<String> onPlaylistChanged) {

        @SuppressWarnings("unchecked")
        ListView<String> dropdownList = (ListView<String>) dropdownCard.getChildren().get(0);

        dropdownList.setOnMouseClicked(e -> {
            String sel = dropdownList.getSelectionModel().getSelectedItem();
            if (sel != null) {
                currentSelection[0] = sel;

                ((Label) pill.getChildren().get(0)).setText(currentSelection[0]);

                viewItems.setAll(
                        masterList.stream()
                                .filter(s -> !s.equals(currentSelection[0]))
                                .collect(Collectors.toList())
                );

                hideDropdown.accept(dropdownCard);

                if (onPlaylistChanged != null) {
                    onPlaylistChanged.accept(sel);
                }
            }
        });

        pill.setOnMouseClicked(evt -> {
            if (evt.getButton() != javafx.scene.input.MouseButton.PRIMARY) return;

            if (dropdownCard.isVisible()) {
                hideDropdown.accept(dropdownCard);
            } else {
                viewItems.setAll(
                        masterList.stream()
                                .filter(s -> !s.equals(currentSelection[0]))
                                .collect(Collectors.toList())
                );
                showDropdownUnderPill(dropdownCard, dropdownList, pill, scene, rootOverlay, img);
            }
            evt.consume();
        });

        scene.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, ev -> {
            if (!dropdownCard.isVisible()) return;
            Node target = ev.getPickResult().getIntersectedNode();
            if (target == null) return;
            boolean clickedInsideDropdown = isDescendant(target, dropdownCard);
            boolean clickedOnPill = isDescendant(target, pill) || target == pill;
            if (!clickedInsideDropdown && !clickedOnPill) {
                hideDropdown.accept(dropdownCard);
            }
        });
    }

    /**
     * Positions and shows the dropdown card under the pill.
     *
     * @param dropdownCard The dropdown card to show
     * @param listView     The list view inside the dropdown
     * @param pill         The pill above which to position the dropdown
     * @param scene        The application scene
     * @param overlay      The overlay pane
     * @param albumImage   Reference image for height calculation
     */
    private void showDropdownUnderPill(VBox dropdownCard,
                                       ListView<?> listView,
                                       Node pill,
                                       Scene scene,
                                       Pane overlay,
                                       ImageView albumImage) {
        javafx.geometry.Bounds pillBoundsInScene = pill.localToScene(pill.getBoundsInLocal());
        if (pillBoundsInScene == null) {
            dropdownCard.setVisible(true);
            dropdownCard.setManaged(true);
            return;
        }
        double popupW = Math.max(160, pillBoundsInScene.getWidth());
        dropdownCard.setPrefWidth(popupW);

        double albumH = 220;
        try {
            javafx.geometry.Bounds imgBounds = albumImage.localToScene(albumImage.getBoundsInLocal());
            if (imgBounds != null) albumH = imgBounds.getHeight();
        } catch (Exception ignored) {
        }
        double sceneW = scene.getWidth();
        double sceneH = scene.getHeight();

        double x = pillBoundsInScene.getMinX();
        double y = pillBoundsInScene.getMaxY() + 2;
        if (x + popupW > sceneW - 8) x = Math.max(8, sceneW - popupW - 8);
        if (x < 8) x = 8;
        double desiredListH = Math.max(100, albumH);
        double totalCardH = desiredListH + 12;
        if (y + totalCardH > sceneH - 8) {
            double altY = pillBoundsInScene.getMinY() - totalCardH - 6;
            if (altY > 8) {
                y = altY;
            } else {
                desiredListH = Math.max(80, sceneH - y - 16);
            }
        }
        double maxListHBelow = sceneH - y - 16;
        double listH = Math.min(desiredListH, Math.max(80, maxListHBelow));
        listView.setPrefHeight(listH);
        dropdownCard.setVisible(true);
        dropdownCard.setManaged(true);
        dropdownCard.setLayoutX(x);
        dropdownCard.setLayoutY(y);
        dropdownCard.setPrefHeight(listH + 12);
        dropdownCard.toFront();
    }

    /**
     * Checks if a node is a descendant of another node in the scene graph.
     *
     * @param target   The node to check
     * @param ancestor The potential ancestor node
     * @return true if target is a descendant of ancestor, false otherwise
     */
    private boolean isDescendant(Node target, Node ancestor) {
        Node cur = target;
        while (cur != null) {
            if (cur == ancestor) return true;
            cur = cur.getParent();
        }
        return false;
    }
}
