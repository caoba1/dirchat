package org.dirchat.controller;

import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.dirchat.util.ConfigManager;

public class ConfigDialog extends Stage {
    public ConfigDialog( Stage parent) {
        setTitle("dirchat - Configuration");
        initOwner(parent);
        initModality(Modality.APPLICATION_MODAL);

        String[] methods = {"default", "sbert", "ollama"};
        ComboBox<String> methodSelector = new ComboBox<>();
        methodSelector.getItems().addAll(methods);
        methodSelector.setValue(ConfigManager.getIndexingMethod());

        // Add controls for paragraphsPerChunk
        Label paragraphsLabel = new Label("Paragraphs per Chunk:");
        javafx.scene.control.TextField paragraphsField = new javafx.scene.control.TextField(String.valueOf(ConfigManager.getParagraphsPerChunk()));

        // Add controls for paragraphOverlap
        Label overlapLabel = new Label("Paragraph Overlap:");
        javafx.scene.control.TextField overlapField = new javafx.scene.control.TextField(String.valueOf(ConfigManager.getOverlap()));

        // Add controls for Ollama base URL
        Label ollamaUrlLabel = new Label("Ollama Base URL:");
        javafx.scene.control.TextField ollamaUrlField = new javafx.scene.control.TextField(ConfigManager.getOllamaBaseUrl());

        // Add controls for topK
        Label topKLabel = new Label("Top K (search results):");
        javafx.scene.control.TextField topKField = new javafx.scene.control.TextField(String.valueOf(ConfigManager.getTopK()));

        // Load resource bundle for localization
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("messages");

        // Map config values to localized labels
        java.util.Map<String, String> outputModeLabels = new java.util.LinkedHashMap<>();
        outputModeLabels.put("none", bundle.getString("output.mode.none"));
        outputModeLabels.put("truncate", bundle.getString("output.mode.truncate"));
        outputModeLabels.put("summarize", bundle.getString("output.mode.summarize"));

        Label outputModeLabel = new Label(bundle.getString("output.mode"));
        ComboBox<String> outputModeComboBox = new ComboBox<>();
        outputModeComboBox.getItems().addAll(outputModeLabels.values());
        // Set selected value based on config
        String currentMode = ConfigManager.getOutputMode();
        outputModeComboBox.setValue(outputModeLabels.getOrDefault(currentMode, outputModeLabels.get("none")));

        Button saveButton = new Button(bundle.getString("button.ask"));
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

        VBox vbox = new VBox(10,
                methodSelector,
                paragraphsLabel, paragraphsField,
                overlapLabel, overlapField,
                ollamaUrlLabel, ollamaUrlField,
                topKLabel, topKField,
                outputModeLabel,
                outputModeComboBox,
                saveButton);
        vbox.setPadding(new Insets(20));
        setScene(new Scene(vbox, 300, 420));
    }
}
