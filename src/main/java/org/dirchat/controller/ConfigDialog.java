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
import org.dirchat.util.LogMessages;
import org.dirchat.util.Messages;

import java.io.IOException;
import java.util.Locale;

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

    public ConfigDialog(Stage parent) {
        setTitle(Messages.get("config.title"));
        initOwner(parent);
        initModality(Modality.APPLICATION_MODAL);
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/view/ConfigMenu.fxml"),
                    Messages.getBundle());
            loader.setController(this);
            Parent root = loader.load();
            setScene(new Scene(root));
            sizeToScene();
            centerOnScreen();
        } catch (IOException e) {
            LogMessages.logWarning("Could not load configuration dialog.", e);
            throw new IllegalStateException("Could not load configuration dialog.", e);
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
        configureLocalizedOptions(outputModeComboBox, "config.augmentation.output-mode.");
        outputModeComboBox.getItems().addAll("truncate", "summarize", "none");
        outputModeComboBox.setValue(ConfigManager.getOutputMode());
        configureLocalizedOptions(methodSelector, "config.indexing.method.");
        methodSelector.getItems().addAll("default", "sbert", "ollama");
        methodSelector.setValue(ConfigManager.getIndexingMethod());
        configureLocalizedOptions(languageComboBox, "config.language.");
        languageComboBox.getItems().addAll("en", "es", "de", "fr");
        languageComboBox.setValue(ConfigManager.getLanguage());
        configureLocalizedOptions(themeComboBox, "config.display.theme.");
        themeComboBox.getItems().addAll("Light", "Dark");
        themeComboBox.setValue("Light");
        deleteIndexCheckBox.setSelected(ConfigManager.getDeleteIndexOnLoad());

        paragraphsField.setTooltip(new Tooltip(Messages.get("config.indexing.paragraphs.tooltip")));
        overlapField.setTooltip(new Tooltip(Messages.get("config.indexing.overlap.tooltip")));
        ollamaUrlField.setTooltip(new Tooltip(Messages.get("config.models.ollama-url.tooltip")));
        topKField.setTooltip(new Tooltip(Messages.get("config.retrieval.top-k.tooltip")));
        methodSelector.setTooltip(new Tooltip(Messages.get("config.indexing.method.tooltip")));
        outputModeComboBox.setTooltip(new Tooltip(Messages.get("config.augmentation.output-mode.tooltip")));
    }

    @FXML
    private void saveConfig() {
        try {
            int chunkCharacterLimit = parseInteger(chunkLimitField, "config.validation.chunk-limit");
            double minScore;
            try {
                minScore = Double.parseDouble(minScoreField.getText().trim());
            } catch (NumberFormatException e) {
                throw new ConfigManager.ConfigValidationException("config.validation.minimum-score");
            }

            ConfigManager.Settings settings = new ConfigManager.Settings(
                    parseInteger(paragraphsField, "config.validation.paragraphs"),
                    parseInteger(overlapField, "config.validation.overlap"),
                    ollamaUrlField.getText(),
                    parseInteger(topKField, "config.validation.top-k"),
                    chunkCharacterLimit,
                    minScore,
                    queryAnswerCheckBox.isSelected(),
                    outputModeComboBox.getValue(),
                    methodSelector.getValue(),
                    languageComboBox.getValue(),
                    deleteIndexCheckBox.isSelected());
            ConfigManager.saveSettings(settings);
            Messages.setLanguage(settings.language());
            close();
        } catch (ConfigManager.ConfigValidationException e) {
            showSaveError(Messages.get(e.getMessageKey()));
        } catch (IOException e) {
            showSaveError(Messages.format("config.error.save", e.getMessage()));
        }
    }

    private int parseInteger(TextField field, String messageKey) {
        try {
            return Integer.parseInt(field.getText().trim());
        } catch (NumberFormatException e) {
            throw new ConfigManager.ConfigValidationException(messageKey);
        }
    }

    private void configureLocalizedOptions(ComboBox<String> comboBox, String keyPrefix) {
        comboBox.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null
                        ? null
                        : Messages.get(keyPrefix + item.toLowerCase(Locale.ROOT)));
            }
        });
        comboBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null
                        ? null
                        : Messages.get(keyPrefix + item.toLowerCase(Locale.ROOT)));
            }
        });
    }

    private void showSaveError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initOwner(this);
        alert.setTitle(getTitle());
        alert.setHeaderText(Messages.get("config.error.title"));
        alert.setContentText(message);
        alert.getButtonTypes().setAll(new ButtonType(
                Messages.get("common.action.ok"), ButtonBar.ButtonData.OK_DONE));
        alert.showAndWait();
    }
}
