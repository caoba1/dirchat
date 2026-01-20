/*
 * dirchat — A directory chat application written in Java.
 * A front end for directory querying using network embeddings and
 * question answering on retrieved local documents using language models.
 *
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 *
 * Contact: info@uiloops.com
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed WITHOUT ANY WARRANTY; without even the
 * implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Affero General Public License for more details.
 *
 */

package org.dirchat.controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.dirchat.util.ConfigManager;

public class ConfigDialog extends Stage {
    @FXML private ComboBox<String> languageComboBox;
    @FXML private ComboBox<String> themeComboBox;
    @FXML private CheckBox deleteIndexCheckBox;
    @FXML private TextField paragraphsField;
    @FXML private TextField overlapField;
    @FXML private TextField ollamaUrlField;
    @FXML private TextField topKField;
    @FXML private TextField chunkLimitField;
    @FXML private TextField minScoreField;
    @FXML private ComboBox<String> outputModeComboBox;
    @FXML private ComboBox<String> methodSelector;
    @FXML private CheckBox queryAnswerCheckBox;
    @FXML private Button saveButton;

    public ConfigDialog(Stage parent) {
        setTitle(java.util.ResourceBundle.getBundle("messages").getString("config.title"));
        initOwner(parent);
        initModality(Modality.APPLICATION_MODAL);
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/ConfigMenu.fxml"));
            loader.setController(this);
            Parent root = loader.load();
            setScene(new Scene(root));
        } catch (Exception e) {
            System.err.println("Failed to load ConfigMenu.fxml: " + e.getMessage());
        }
    }

    @FXML
    public void initialize() {
        paragraphsField.setText(String.valueOf(ConfigManager.getParagraphsPerChunk()));
        overlapField.setText(String.valueOf(ConfigManager.getOverlapCharacters()));
        ollamaUrlField.setText(ConfigManager.getOllamaBaseUrl());
        topKField.setText(String.valueOf(ConfigManager.getTopK()));
        chunkLimitField.setText(String.valueOf(ConfigManager.getChunkCharacterLimit()));
        minScoreField.setText(String.valueOf(ConfigManager.getMinScore()));
        queryAnswerCheckBox.setSelected(ConfigManager.getQueryAnswer());
        outputModeComboBox.getItems().addAll("truncate", "summarize", "none");
        outputModeComboBox.setValue(ConfigManager.getOutputMode());
        methodSelector.getItems().addAll("default", "sbert", "ollama");
        methodSelector.setValue(ConfigManager.getIndexingMethod());
        languageComboBox.getItems().addAll("en", "es", "de", "fr");
        languageComboBox.setValue(ConfigManager.getLanguage());
        themeComboBox.getItems().addAll("Light", "Dark");
        themeComboBox.setValue("Light");
        deleteIndexCheckBox.setSelected(ConfigManager.getDeleteIndexOnLoad());
        saveButton.setOnAction(e -> saveConfig());
    }

    private void saveConfig() {
        try {
            ConfigManager.setParagraphsPerChunk(Integer.parseInt(paragraphsField.getText()));
            ConfigManager.setOverlapCharacters(Integer.parseInt(overlapField.getText()));
            ConfigManager.setOllamaBaseUrl(ollamaUrlField.getText());
            ConfigManager.setTopK(Integer.parseInt(topKField.getText()));
            ConfigManager.setChunkCharacterLimit(Integer.parseInt(chunkLimitField.getText()));
            ConfigManager.setMinScore(Double.parseDouble(minScoreField.getText()));
            ConfigManager.setQueryAnswer(queryAnswerCheckBox.isSelected());
            ConfigManager.setOutputMode(outputModeComboBox.getValue());
            ConfigManager.setIndexingMethod(methodSelector.getValue());
            ConfigManager.setLanguage(languageComboBox.getValue());
            ConfigManager.setDeleteIndexOnLoad(deleteIndexCheckBox.isSelected());
            ((Stage)saveButton.getScene().getWindow()).close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
