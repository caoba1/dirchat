package org.dirchat.controller;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.dirchat.util.ConfigManager;

public class ConfigDialog extends Stage {
    public ConfigDialog( Stage parent) {
        setTitle("dirchat - Configuration");
        initOwner(parent);
        initModality(Modality.APPLICATION_MODAL);

        // Load resource bundle for localization
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("messages");

        // Localized labels and tooltips
        Label paragraphsLabel = new Label(bundle.getString("config.paragraphsPerChunk"));
        javafx.scene.control.TextField paragraphsField = new javafx.scene.control.TextField(String.valueOf(ConfigManager.getParagraphsPerChunk()));
        paragraphsLabel.setTooltip(new Tooltip(bundle.getString("config.paragraphsPerChunk.tooltip")));
        paragraphsField.setTooltip(new Tooltip(bundle.getString("config.paragraphsPerChunk.tooltip")));

        Label overlapLabel = new Label(bundle.getString("config.paragraphOverlap"));
        javafx.scene.control.TextField overlapField = new javafx.scene.control.TextField(String.valueOf(ConfigManager.getOverlap()));
        overlapLabel.setTooltip(new Tooltip(bundle.getString("config.paragraphOverlap.tooltip")));
        overlapField.setTooltip(new Tooltip(bundle.getString("config.paragraphOverlap.tooltip")));

        Label ollamaUrlLabel = new Label(bundle.getString("config.ollamaUrl"));
        javafx.scene.control.TextField ollamaUrlField = new javafx.scene.control.TextField(ConfigManager.getOllamaBaseUrl());
        ollamaUrlLabel.setTooltip(new Tooltip(bundle.getString("config.ollamaUrl.tooltip")));
        ollamaUrlField.setTooltip(new Tooltip(bundle.getString("config.ollamaUrl.tooltip")));

        Label topKLabel = new Label(bundle.getString("config.topK"));
        javafx.scene.control.TextField topKField = new javafx.scene.control.TextField(String.valueOf(ConfigManager.getTopK()));
        topKLabel.setTooltip(new Tooltip(bundle.getString("config.topK.tooltip")));
        topKField.setTooltip(new Tooltip(bundle.getString("config.topK.tooltip")));

        Label outputModeLabel = new Label(bundle.getString("config.outputMode"));
        outputModeLabel.setTooltip(new Tooltip(bundle.getString("config.outputMode.tooltip")));

        ComboBox<String> outputModeComboBox = new ComboBox<>();
        outputModeComboBox.setTooltip(new Tooltip(bundle.getString("config.outputMode.tooltip")));

        // Output mode labels for localization
        java.util.Map<String, String> outputModeLabels = new java.util.LinkedHashMap<>();
        outputModeLabels.put("default", bundle.getString("outputMode.default"));
        outputModeLabels.put("sbert", bundle.getString("outputMode.sbert"));
        outputModeLabels.put("ollama", bundle.getString("outputMode.ollama"));

        // Populate outputModeComboBox with localized labels
        outputModeComboBox.getItems().addAll(outputModeLabels.values());
        // Set current value based on config
        String currentMode = ConfigManager.getOutputMode();
        String currentLabel = outputModeLabels.getOrDefault(currentMode, outputModeLabels.get("default"));
        outputModeComboBox.setValue(currentLabel);

        String[] methods = {"default", "sbert", "ollama"};
        ComboBox<String> methodSelector = new ComboBox<>();
        methodSelector.getItems().addAll(methods);
        methodSelector.setValue(ConfigManager.getIndexingMethod());
        methodSelector.setTooltip(new Tooltip(bundle.getString("config.method.tooltip")));

        // Supported languages and display names
        java.util.Map<String, String> languageMap = new java.util.LinkedHashMap<>();
        languageMap.put("en", bundle.getString("language.en"));
        languageMap.put("de", bundle.getString("language.de"));
        languageMap.put("fr", bundle.getString("language.fr"));
        Label languageLabel = new Label(bundle.getString("config.language"));
        languageLabel.setTooltip(new Tooltip(bundle.getString("config.language.tooltip")));
        ComboBox<String> languageComboBox = new ComboBox<>();
        languageComboBox.getItems().addAll(languageMap.values());
        languageComboBox.setTooltip(new Tooltip(bundle.getString("config.language.tooltip")));
        // Set ComboBox to current language
        String currentLang = org.dirchat.util.ConfigManager.getLanguage();
        languageComboBox.setValue(languageMap.getOrDefault(currentLang, languageMap.get("en")));
        // Language change handler
        languageComboBox.setOnAction(e -> {
            String selectedDisplay = languageComboBox.getValue();
            String selectedLang = languageMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(selectedDisplay))
                .map(java.util.Map.Entry::getKey)
                .findFirst().orElse("en");
            try {
                org.dirchat.util.ConfigManager.setLanguage(selectedLang);
                // Reload dialog texts (simple approach: close and reopen)
                close();
                new org.dirchat.controller.ConfigDialog(parent).showAndWait();
            } catch (Exception ex) {
                System.err.println("Failed to set language: " + ex.getMessage());
            }
        });

        Button saveButton = new Button(bundle.getString("button.saveConfig"));
        saveButton.setOnAction(event -> {
            try{
                ConfigManager.setIndexingMethod(methodSelector.getValue());
                ConfigManager.setParagraphsPerChunk(Integer.parseInt(paragraphsField.getText()));
                ConfigManager.setOverlap(Integer.parseInt(overlapField.getText()));
                ConfigManager.setOllamaBaseUrl(ollamaUrlField.getText());
                ConfigManager.setTopK(Integer.parseInt(topKField.getText()));
                // Find config value from selected label
                String selectedLabel = outputModeComboBox.getValue();
                String selectedMode = outputModeLabels.entrySet().stream()
                    .filter(e -> e.getValue().equals(selectedLabel))
                    .map(java.util.Map.Entry::getKey)
                    .findFirst().orElse("none");
                ConfigManager.setOutputMode(selectedMode);
                close();
            } catch (Exception e) {
                System.err.println("Failed to save configuration: " + e.getMessage());
                org.dirchat.util.LogMessages.logWarning(e.getMessage());
            }
        });

        // Set window/taskbar icon for the config dialog
        java.io.InputStream iconStream = getClass().getResourceAsStream("/icons/dirchat-logo.png");
        if (iconStream != null) {
            getIcons().add(new javafx.scene.image.Image(iconStream));
        }

        // Add a visual icon at the top of the dialog
        java.io.InputStream iconStream2 = getClass().getResourceAsStream("/icons/dirchat-logo.png");
        javafx.scene.image.ImageView iconView = null;
        if (iconStream2 != null) {
            iconView = new javafx.scene.image.ImageView(new javafx.scene.image.Image(iconStream2));
            iconView.setFitHeight(40);
            iconView.setPreserveRatio(true);
        }

        Label askButtonHelp = new Label(bundle.getString("config.askButtonHelp"));
        askButtonHelp.setWrapText(true);

        VBox vbox = new VBox(10,
                iconView != null ? iconView : new Label(),
                languageLabel, languageComboBox,
                askButtonHelp,
                methodSelector,
                paragraphsLabel, paragraphsField,
                overlapLabel, overlapField,
                ollamaUrlLabel, ollamaUrlField,
                topKLabel, topKField,
                outputModeLabel,
                outputModeComboBox,
                saveButton);
        vbox.setPadding(new Insets(20));
        setScene(new Scene(vbox, 300, 520));

        // Tooltips for configuration options
        methodSelector.setTooltip(new Tooltip("Select the method used to generate document embeddings for search and retrieval."));
        paragraphsLabel.setTooltip(new Tooltip("Set how many paragraphs are grouped together as a chunk for indexing and search."));
        paragraphsField.setTooltip(new Tooltip("Set how many paragraphs are grouped together as a chunk for indexing and search."));
        overlapLabel.setTooltip(new Tooltip("Set how many paragraphs overlap between chunks. Higher overlap can improve context but increases index size."));
        overlapField.setTooltip(new Tooltip("Set how many paragraphs overlap between chunks. Higher overlap can improve context but increases index size."));
        ollamaUrlLabel.setTooltip(new Tooltip("Specify the base URL for your Ollama server. Used for model inference and embedding generation."));
        ollamaUrlField.setTooltip(new Tooltip("Specify the base URL for your Ollama server. Used for model inference and embedding generation."));
        topKLabel.setTooltip(new Tooltip("Set how many top results to return when searching for relevant document chunks."));
        topKField.setTooltip(new Tooltip("Set how many top results to return when searching for relevant document chunks."));
        outputModeLabel.setTooltip(new Tooltip("Choose how answers are presented: none (raw), truncate (shorten), or summarize (generate summary)."));
        outputModeComboBox.setTooltip(new Tooltip("Choose how answers are presented: none (raw), truncate (shorten), or summarize (generate summary)."));
    }
}
