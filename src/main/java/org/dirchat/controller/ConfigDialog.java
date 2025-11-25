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
    public ConfigDialog(Stage parent) {
        setTitle(java.util.ResourceBundle.getBundle("messages").getString("config.title")); // Localized title
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
        javafx.scene.control.TextField overlapField = new javafx.scene.control.TextField(String.valueOf(ConfigManager.getOverlapCharacters()));
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
        outputModeLabels.put("truncate", bundle.getString("output.mode.truncate"));
        outputModeLabels.put("summarize", bundle.getString("output.mode.summarize"));
        outputModeLabels.put("none", bundle.getString("output.mode.none"));

        // Populate outputModeComboBox with localized labels
        outputModeComboBox.getItems().addAll(outputModeLabels.values());
        // Set current value based on config
        String currentMode = ConfigManager.getOutputMode();
        String currentLabel = outputModeLabels.getOrDefault(currentMode, outputModeLabels.get("none"));
        outputModeComboBox.setValue(currentLabel);

        String[] methods = {"default", "sbert", "ollama"};
        ComboBox<String> methodSelector = new ComboBox<>();
        methodSelector.getItems().addAll(methods);
        methodSelector.setValue(ConfigManager.getIndexingMethod());
        methodSelector.setTooltip(new Tooltip(bundle.getString("config.method.tooltip")));

        // Supported languages and display names
        java.util.Map<String, String> languageMap = new java.util.LinkedHashMap<>();
        languageMap.put("en", bundle.getString("language.en"));
        languageMap.put("es", bundle.getString("language.es"));
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

        Label queryAnswerLabel = new Label(bundle.getString("config.queryAnswer"));
        queryAnswerLabel.setTooltip(new Tooltip(bundle.getString("config.queryAnswer.tooltip")));
        javafx.scene.control.CheckBox queryAnswerCheckBox = new javafx.scene.control.CheckBox();
        queryAnswerCheckBox.setSelected(ConfigManager.getQueryAnswer());
        queryAnswerCheckBox.setTooltip(new Tooltip(bundle.getString("config.queryAnswer.tooltip")));

        Label chunkLimitLabel = new Label(bundle.getString("config.chunkCharacterLimit"));
        javafx.scene.control.TextField chunkLimitField = new javafx.scene.control.TextField(String.valueOf(ConfigManager.getChunkCharacterLimit()));
        chunkLimitLabel.setTooltip(new Tooltip(bundle.getString("config.chunkCharacterLimit.tooltip")));
        chunkLimitField.setTooltip(new Tooltip(bundle.getString("config.chunkCharacterLimit.tooltip")));

        Label minScoreLabel = new Label(bundle.getString("config.minScore"));
        javafx.scene.control.TextField minScoreField = new javafx.scene.control.TextField(String.valueOf(ConfigManager.getMinScore()));
        minScoreLabel.setTooltip(new Tooltip(bundle.getString("config.minScore.tooltip")));
        minScoreField.setTooltip(new Tooltip(bundle.getString("config.minScore.tooltip")));

        Button saveButton = new Button(bundle.getString("button.saveConfig"));
        saveButton.setOnAction(event -> {
            try{
                ConfigManager.setIndexingMethod(methodSelector.getValue());
                ConfigManager.setParagraphsPerChunk(Integer.parseInt(paragraphsField.getText()));
                ConfigManager.setOverlapCharacters(Integer.parseInt(overlapField.getText()));
                ConfigManager.setOllamaBaseUrl(ollamaUrlField.getText());
                ConfigManager.setTopK(Integer.parseInt(topKField.getText()));
                String selectedLabel = outputModeComboBox.getValue();
                String selectedMode = outputModeLabels.entrySet().stream()
                    .filter(e -> e.getValue().equals(selectedLabel))
                    .map(java.util.Map.Entry::getKey)
                    .findFirst().orElse("none");
                ConfigManager.setOutputMode(selectedMode);
                ConfigManager.setQueryAnswer(queryAnswerCheckBox.isSelected());
                ConfigManager.setChunkCharacterLimit(Integer.parseInt(chunkLimitField.getText()));
                ConfigManager.setMinScore(Double.parseDouble(minScoreField.getText()));
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

        VBox vbox = new VBox(12,
            paragraphsLabel, paragraphsField,
            overlapLabel, overlapField,
            ollamaUrlLabel, ollamaUrlField,
            topKLabel, topKField,
            chunkLimitLabel, chunkLimitField,
            minScoreLabel, minScoreField,
            outputModeLabel, outputModeComboBox,
            methodSelector,
            languageLabel, languageComboBox,
            queryAnswerLabel, queryAnswerCheckBox,
            saveButton
        );
        vbox.setPadding(new Insets(18));
        setScene(new Scene(vbox));

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
