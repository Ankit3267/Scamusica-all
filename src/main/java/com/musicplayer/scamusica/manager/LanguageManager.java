package com.musicplayer.scamusica.manager;

import com.musicplayer.scamusica.ui.LangItem;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableMap;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.ResourceBundle;

public class LanguageManager {
    private static final ObjectProperty<Locale> locale = new SimpleObjectProperty<>(Locale.ENGLISH);
    private static final ObservableMap<String, String> translations = FXCollections.observableHashMap();

    static {
        locale.addListener((obs, oldLocale, newLocale) -> {
            Locale.setDefault(newLocale);
            SessionManager.saveLanguage(newLocale.getLanguage());
            // Clear and reload translations if needed
            translations.clear();
        });
    }

    public static void setLanguage(String langCode) {

        if (langCode == null || langCode.isEmpty()) {
            langCode = "es";
        }

        Locale newLocale = switch (langCode) {
            case "es" -> new Locale("es");
            case "en" -> new Locale("en");
            case "de" -> new Locale("de");
            case "fr" -> new Locale("fr");
            case "pt" -> new Locale("pt");
            case "it" -> new Locale("it");
            case "zh" -> new Locale("zh");
            case "ja" -> new Locale("ja");
            case "ar" -> new Locale("ar");
            case "hi" -> new Locale("hi");
            default -> new Locale("es");
        };
        
        SessionManager.saveLanguage(newLocale.getLanguage());
        locale.set(newLocale);
    }

    public static String getLangCode() {
        return SessionManager.getLanguage();
    }

    // Core method for non-binding use
    public static String get(String key, Object... args) {
        ResourceBundle bundle = ResourceBundle.getBundle("i18n.messages", locale.get());
        String message = bundle.getString(key);
        return args.length > 0 ? MessageFormat.format(message, args) : message;
    }

    // MAGIC: Create reactive binding for any text property
    public static StringBinding createStringBinding(String key, Object... args) {
        return Bindings.createStringBinding(() -> get(key, args), locale);
    }

    // Updated selector (remove controller param and reload calls)
    public static ComboBox<LangItem> createLanguageSelector() {
        // ... your existing ComboBox setup ...
        ComboBox<LangItem> languageCombo = new ComboBox<>();

        // --- 10 Languages ---
        languageCombo.getItems().addAll(
                new LangItem("en", "English", "/images/flags/en.png"),
                new LangItem("es", "Spanish", "/images/flags/es.png"),
                new LangItem("fr", "French", "/images/flags/fr.png"),
                new LangItem("de", "German", "/images/flags/de.png"),
                new LangItem("pt", "Portuguese", "/images/flags/pt.png"),
                new LangItem("it", "Italian", "/images/flags/it.png"),
                new LangItem("zh", "Chinese", "/images/flags/zh.png"),
                new LangItem("ja", "Japanese", "/images/flags/ja.png"),
                new LangItem("ar", "Arabic", "/images/flags/ar.png"),
                new LangItem("hi", "Hindi", "/images/flags/hi.png")
        );
        // load saved language from SessionManager
        String savedLanguage = SessionManager.getLanguage();
        LangItem defaultItem = languageCombo.getItems()
                .stream()
                .filter(l -> l.code().equals(savedLanguage))
                .findFirst()
                .orElse(languageCombo.getItems().get(0));

        languageCombo.setValue(defaultItem);

        // --- Custom cell factory (dropdown list) ---
        languageCombo.setCellFactory(listView -> new ListCell<>() {
            @Override
            protected void updateItem(LangItem item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                HBox box = new HBox(8);
                ImageView img = new ImageView(new Image(
                        Objects.requireNonNull(getClass().getResourceAsStream(item.iconPath()))
                ));
                img.setFitWidth(20);
                img.setFitHeight(15);

                Label lbl = new Label(item.name());
                lbl.setStyle("-fx-text-fill: black; -fx-font-size: 14px;");

                box.getChildren().addAll(img, lbl);
                setGraphic(box);
            }
        });

        // --- Button area (selected item) ---
        languageCombo.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(LangItem item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setGraphic(null);
                    setText(null);
                    return;
                }

                HBox box = new HBox(8);
                ImageView img = new ImageView(new Image(
                        Objects.requireNonNull(getClass().getResourceAsStream(item.iconPath()))
                ));
                img.setFitWidth(22);
                img.setFitHeight(16);

                Label lbl = new Label(item.name());
                lbl.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-weight: 600;");

                box.getChildren().addAll(img, lbl);
                setGraphic(box);
            }
        });

        // --- Handle language change ---
        languageCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) return;

            LanguageManager.setLanguage(newVal.code());

            SessionManager.saveLanguage(newVal.code()); // Save language
        });

        return languageCombo;
    }
}

