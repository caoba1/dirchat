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

// Handles JavaFX UI interactions

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import org.dirchat.document.DocumentLoader;
import org.dirchat.embedding.*;
import org.dirchat.util.LogMessages;
import org.dirchat.util.ConfigManager;
import org.dirchat.util.Messages;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Future;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.nio.file.Path;
import java.nio.file.Paths;


public class UIController {
    @FXML
    private TextArea questionField;
    @FXML
    private Button loadFolderButton;
    @FXML
    private Button askButton;
    @FXML
    private Label folderLabel;
    @FXML
    private TableView<DocumentLoader.LoadedDocument> indexStatusTable;
    @FXML
    private TableColumn<DocumentLoader.LoadedDocument, String> scoreColumn;
    @FXML
    private TableColumn<DocumentLoader.LoadedDocument, String> fileNameColumn;
    @FXML
    private TableColumn<DocumentLoader.LoadedDocument, Void> openColumn;
    @FXML
    private ListView<String> chatListView;
    @FXML
    private Label messageBar;
    @FXML
    private BorderPane mainBorderPane;
    @FXML
    private Button configButton;
    @FXML
    private Label indexLoadedIndicator;
    @FXML
    private Label tablePlaceholderLabel;
    @FXML
    private Button exportChatButton;
    @FXML
    private Button resetChatButton;
    @FXML
    private Button toggleFilesButton;
    @FXML
    private ImageView logoImageView;

    private String selectedFolder;
    private VectorStore vectorStore;
    private IndexStatus currentIndexStatus = IndexStatus.NOT_SELECTED;
    private boolean hasIndexStatus;

    // 251210: Make list unmodifiable and do not re asign the reference.
    private final List<DocumentLoader.LoadedDocument> loadedDocuments = new ArrayList<>();


    private final java.util.concurrent.ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final List<Future<?>> futures = new java.util.ArrayList<>();
    private boolean showingScoredFiles = false;


    @FXML
    public void initialize() {
        refreshLocalizedText();

        loadFolderButton.setOnAction(e -> handleLoadFolder());
        askButton.setOnAction(e -> handleAskQuestion());
        exportChatButton.setOnAction(e -> exportChatHistory());
        resetChatButton.setOnAction(e -> handleReset());

        configButton.setOnAction(e -> {
            String previousLanguage = ConfigManager.getLanguage();
            ConfigDialog configDialog = new ConfigDialog((Stage) mainBorderPane.getScene().getWindow());
            configDialog.showAndWait();
            if (!previousLanguage.equals(ConfigManager.getLanguage())) {
                refreshLocalizedText();
                ((Stage) mainBorderPane.getScene().getWindow()).setTitle(Messages.get("app.window.title"));
            }
        });

        // Table view configuration
        scoreColumn.setCellValueFactory(cellData -> {
            Double score = cellData.getValue().getScore();
            if (score == null)
            {
                return new javafx.beans.property.SimpleStringProperty("");
            }
            return new javafx.beans.property.SimpleStringProperty(
                    String.format(Messages.getLocale(), "%.2f", score));
        });
        scoreColumn.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            {
                setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
                if (empty || item == null || item.isBlank()) {
                    setStyle("-fx-text-fill: gray;");
                } else {
                    setStyle("");
                }
            }
        });
        // Set cell factory for fileNameColumn to CENTER_LEFT
        fileNameColumn.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            {
                setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
            }
        });
        // Set cell factory for openColumn to CENTER_LEFT
        openColumn.setCellFactory(col -> new TableCell<>() {
            private final Button openButton = new Button();
            {
                setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                openButton.setOnAction(e -> {
                    DocumentLoader.LoadedDocument doc = getTableView().getItems().get(getIndex());
                    openDocument(doc);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                openButton.setText(Messages.get("directory.file.open"));
                setGraphic(empty ? null : openButton);
            }
        });

        chatListView.getItems().clear();
        askButton.setDisable(true); // Disable until folder is loaded

        // Wrap chat messages in chatListView
        chatListView.setCellFactory(lv -> new ListCell<>() {
            private final Label label = new Label();
            {
                label.setWrapText(true);
                label.setMaxWidth(Double.MAX_VALUE);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    label.setText(item);
                    // Bind label width to listview width minus some padding
                    label.maxWidthProperty().bind(chatListView.widthProperty().subtract(30));
                    setGraphic(label);
                }
            }
        });

        //251210: Update table after setting cell factories.
        indexStatusTable.refresh();

        if (logoImageView != null && logoImageView.getImage() != null) {
            double originalWidth = logoImageView.getImage().getWidth();
            double originalHeight = logoImageView.getImage().getHeight();
            logoImageView.setFitWidth(originalWidth / 2);
            logoImageView.setFitHeight(originalHeight / 2);
        }
    }

    public void refreshLocalizedText() {
        loadFolderButton.setText(Messages.get("directory.load.action"));
        loadFolderButton.setTooltip(new Tooltip(Messages.get("directory.load.tooltip")));
        folderLabel.setText(selectedFolder == null
                ? Messages.get("directory.selection.none")
                : Messages.format("directory.selection.selected", selectedFolder));
        questionField.setPromptText(Messages.get("search.prompt"));
        askButton.setText(Messages.get("search.action.ask"));
        askButton.setTooltip(new Tooltip(Messages.get("search.action.ask.tooltip")));
        exportChatButton.setText(Messages.get("export.action"));
        exportChatButton.setTooltip(new Tooltip(Messages.get("export.action.tooltip")));
        resetChatButton.setText(Messages.get("chat.action.reset"));
        resetChatButton.setTooltip(new Tooltip(Messages.get("chat.action.reset.tooltip")));
        configButton.setText(Messages.get("config.action.open"));
        configButton.setTooltip(new Tooltip(Messages.get("config.action.open.tooltip")));
        tablePlaceholderLabel.setText(Messages.get("search.files.placeholder"));
        fileNameColumn.setText(Messages.get("search.table.filename"));
        scoreColumn.setText(Messages.get("search.table.score"));
        openColumn.setText(Messages.get("search.table.open"));
        if (toggleFilesButton != null) {
            toggleFilesButton.setText(Messages.get(
                    showingScoredFiles ? "search.files.show-all" : "search.files.show-scored"));
        }
        indexStatusTable.refresh();
        if (hasIndexStatus) {
            updateIndexLoadedIndicator(currentIndexStatus);
        }
    }

    public void setMessageBar(String message) {
        setMessageBar(message, "black");
    }

    // Overloaded to allow color
    public void setMessageBar(String message, String color) {
        if (messageBar != null) {
            messageBar.setText(message);
            messageBar.setStyle("-fx-text-fill: " + color + ";");
        }
    }

    //TODO: Refactor to a leaner code.
    private void handleLoadFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(Messages.get("directory.dialog.select.title"));
        File folder = chooser.showDialog(mainBorderPane.getScene().getWindow());
        if (folder == null) {
            setMessageBar(Messages.get("indexing.status.cancelled"));
            return;
        }
        selectedFolder = folder.getAbsolutePath(); //TODO: Change to path.
        folderLabel.setText(Messages.format("directory.selection.selected", selectedFolder));
        // Delete index folder if config option is enabled
        if (ConfigManager.getDeleteIndexOnLoad()) {
            Path indexPath = Paths.get(selectedFolder, ConfigManager.DIRECTORY);
            File indexFolder = indexPath.toFile();
            if (indexFolder.exists()) {
                deleteDirectoryRecursively(indexFolder);
            }
        }
        vectorStore = new VectorStore(selectedFolder);
        ProgressDialog progressDialog = new ProgressDialog(
                (Stage) mainBorderPane.getScene().getWindow(),
                Messages.get("indexing.progress.started"));
        progressDialog.show();
        mainBorderPane.setCursor(Cursor.WAIT);
        javafx.concurrent.Task<Void> loadTask = new IndexingTask( vectorStore, progressDialog );
        loadTask.setOnSucceeded(e -> {

            // 251210: Clear and update the existing list instead of reassigning.
            loadedDocuments.clear();
            loadedDocuments.addAll( ((IndexingTask)loadTask).getLoadedDocuments());

            Platform.runLater(() -> {
                progressDialog.close();
                mainBorderPane.setCursor(Cursor.DEFAULT);
                folderLabel.setText(Messages.format("directory.selection.selected", selectedFolder));
                setMessageBar(Messages.get("indexing.status.loaded"));
                indexStatusTable.getItems().clear();
                indexStatusTable.getItems().addAll(loadedDocuments);
                askButton.setDisable(false);
                updateIndexLoadedIndicator(IndexStatus.LOADED);
            });
        });
        loadTask.setOnCancelled(e -> Platform.runLater(() -> {
            progressDialog.close();
            mainBorderPane.setCursor(Cursor.DEFAULT);
            setMessageBar(Messages.get("indexing.status.cancelled"));
            updateIndexLoadedIndicator(IndexStatus.NOT_SELECTED);
        }));
        loadTask.setOnFailed(e -> Platform.runLater(() -> {
            progressDialog.close();
            mainBorderPane.setCursor(Cursor.DEFAULT);
            setMessageBar(Messages.get("indexing.status.failed"), "red");
            updateIndexLoadedIndicator(IndexStatus.FAILED);
        }));
        futures.add(executorService.submit(loadTask));
    }

    public void handleReset() {
        chatListView.getItems().clear();
        indexStatusTable.getItems().clear();
        indexStatusTable.getItems().addAll(loadedDocuments);
        setMessageBar(Messages.get("chat.status.reset"));
    }

    public void shutdown() {
        for (Future<?> future : futures) {
            future.cancel(true);
        }
        executorService.shutdownNow();
    }

    private void handleAskQuestion() {
        final String question = questionField.getText();
        if (question == null || question.isBlank()) {
            return;
        }
        mainBorderPane.setCursor(Cursor.WAIT);
        chatListView.getItems().clear();
        // TODO> Refactor to separate class. This may be a task.
        // Controller saves the session, right?
        javafx.concurrent.Task<String> askTask = new QueryAnswerTask(
                vectorStore,
                loadedDocuments,
                question,
                this);
        askTask.setOnSucceeded(e -> {
            mainBorderPane.setCursor(Cursor.DEFAULT);
            String answer = askTask.getValue();
            chatListView.getItems().add(Messages.format("chat.transcript.question", question));
            chatListView.getItems().add(Messages.format("chat.transcript.answer", answer));
            questionField.clear();
            mainBorderPane.setCursor(Cursor.DEFAULT);
        });
        askTask.setOnFailed(e -> {
            mainBorderPane.setCursor(Cursor.DEFAULT);
            setMessageBar(Messages.get("chat.answer.failed"), "red");
            if (askTask.getException() != null) {
                LogMessages.logWarning("Chat answer task failed.", askTask.getException());
            }
        });
        askTask.setOnCancelled(e -> {
            mainBorderPane.setCursor(Cursor.DEFAULT);
            setMessageBar(Messages.get("chat.status.cancelled"));
        });
        mainBorderPane.setCursor(Cursor.WAIT);
        futures.add(executorService.submit(askTask));
    }

    private void openDocument(DocumentLoader.LoadedDocument doc) {
        try {
            if (!Desktop.isDesktopSupported()) {
                throw new UnsupportedOperationException("Desktop file opening is not supported.");
            }
            Desktop.getDesktop().open(doc.absolutePath.toFile());
        } catch (Exception e) {
            LogMessages.logWarning("Could not open a selected document.", e);
            setMessageBar(Messages.get("directory.file.open.error"), "red");
        }
    }

    public void setDocumentListItems(List<DocumentLoader.LoadedDocument> docs) {
        if (indexStatusTable != null) {
            indexStatusTable.getItems().setAll(docs);
        }
    }

    /**
     * Updates loadedDocuments and indexStatusList to reflect top scored documents.
     * @param topScores LinkedHashMap of documentId to score, sorted by score descending
     */
    public void updateTopDocuments(LinkedHashMap<Integer, Double> topScores) {
        if (loadedDocuments == null || topScores == null || topScores.isEmpty()) return;
        // Map documentId to LoadedDocument for fast lookup
        java.util.Map<Integer, DocumentLoader.LoadedDocument> docMap = new java.util.HashMap<>();
        for (DocumentLoader.LoadedDocument doc : loadedDocuments) {
            docMap.put(doc.getDocumentId(), doc);
        }
        // Build new ordered list
        java.util.List<DocumentLoader.LoadedDocument> orderedDocs = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Integer, Double> entry : topScores.entrySet()) {
            DocumentLoader.LoadedDocument doc = docMap.get(entry.getKey());
            if (doc != null) {
                LogMessages.debug("Updated a retrieved document score.");
                doc.setScore(entry.getValue());
                orderedDocs.add(doc);
            }
        }
        // Only show retrieved documents
        setDocumentListItems(orderedDocs);
    }

    // In UIController.java
    public enum IndexStatus {
        LOADED, FAILED, NOT_SELECTED
    }

    public void updateIndexLoadedIndicator(IndexStatus status) {
        if (indexLoadedIndicator == null) return;
        currentIndexStatus = status;
        hasIndexStatus = true;
        switch (status) {
            case LOADED:
                indexLoadedIndicator.setText(Messages.get("indexing.indicator.loaded"));
                indexLoadedIndicator.setStyle("-fx-font-size: 18; -fx-text-fill: green;");
                indexLoadedIndicator.setVisible(true);
                break;
            case FAILED:
                indexLoadedIndicator.setText(Messages.get("indexing.indicator.failed"));
                indexLoadedIndicator.setStyle("-fx-font-size: 18; -fx-text-fill: red;");
                indexLoadedIndicator.setVisible(true);
                break;
            case NOT_SELECTED:
                indexLoadedIndicator.setText(Messages.get("indexing.indicator.not-selected"));
                indexLoadedIndicator.setStyle("-fx-font-size: 18; -fx-text-fill: orange;");
                indexLoadedIndicator.setVisible(true);
                break;
        }
    }

    private void exportChatHistory() {
        java.util.List<String> chat = chatListView.getItems();
        if (chat == null || chat.isEmpty()) {
            setMessageBar(Messages.get("export.status.empty"));
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(Messages.get("export.dialog.title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                Messages.get("export.filter.text"), "*.txt"));
        File file = fileChooser.showSaveDialog(mainBorderPane.getScene().getWindow());
        if (file != null) {
            try (java.io.BufferedWriter out = java.nio.file.Files.newBufferedWriter(
                    file.toPath(), StandardCharsets.UTF_8)) {
                for (String line : chat) {
                    out.write(line);
                    out.newLine();
                }
                setMessageBar(Messages.format("export.status.saved", file.getAbsolutePath()));
            } catch (IOException e) {
                LogMessages.logWarning("Could not export chat history.", e);
                setMessageBar(Messages.format("export.error.failed", e.getMessage()), "red");
            }
        }
    }

    private void handleToggleFiles() {
        if (showingScoredFiles) {
            // Show all loaded files
            indexStatusTable.getItems().setAll(loadedDocuments);
            toggleFilesButton.setText(Messages.get("search.files.show-scored"));
            showingScoredFiles = false;
        } else {
            // Show only scored files
            List<DocumentLoader.LoadedDocument> scored = loadedDocuments.stream()
                .filter(doc -> {
                    try {
                        String scoreStr = scoreColumn.getCellObservableValue(doc).getValue();
                        return scoreStr != null && !scoreStr.isEmpty() && !scoreStr.equals("0");
                    } catch (Exception ex) {
                        return false;
                    }
                })
                .toList();
            indexStatusTable.getItems().setAll(scored);
            toggleFilesButton.setText(Messages.get("search.files.show-all"));
            showingScoredFiles = true;
        }
    }

    // Utility method to delete a directory recursively
    private void deleteDirectoryRecursively(File dir) {
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDirectoryRecursively(child);
                }
            }
        }
        dir.delete();
    }
}
