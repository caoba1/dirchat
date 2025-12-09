package org.dirchat.controller;

// Handles JavaFX UI interactions

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import opennlp.tools.dictionary.Index;
import org.dirchat.document.DocumentLoader;
import org.dirchat.embedding.*;
import org.dirchat.util.LogMessages;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.concurrent.Future;
import java.util.LinkedHashMap;
import java.util.concurrent.Executors;

import org.dirchat.util.Dirchat;


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
    private Button exportChatButton;
    @FXML
    private Button resetChatButton;
    @FXML
    private Button toggleFilesButton;
    @FXML
    private CheckBox deleteIndexCheckBox;

    private final Dirchat.ResourceBundleWrapper messages = new Dirchat.ResourceBundleWrapper("messages");

    private String selectedFolder;
    private VectorStore vectorStore;
    private List<DocumentLoader.LoadedDocument> loadedDocuments;


    private final java.util.concurrent.ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final List<Future<?>> futures = new java.util.ArrayList<>();
    private boolean showingScoredFiles = false;


    @FXML
    public void initialize() {
        // Initial UI setup - arranged for clarity
        loadFolderButton.setText(messages.getString("button.loadFolder"));
        loadFolderButton.setOnAction(e -> handleLoadFolder());
        folderLabel.setText(messages.getString("label.noFolderSelected"));

        questionField.setPromptText(messages.getString("prompt.typeQuestion"));
        askButton.setText(messages.getString("button.ask"));
        askButton.setOnAction(e -> handleAskQuestion());

        exportChatButton.setText(messages.getString("button.exportChat"));
        exportChatButton.setOnAction(e -> exportChatHistory());

        resetChatButton.setText(messages.getString("button.resetChat"));
        resetChatButton.setOnAction(e -> handleReset());

        configButton.setText(messages.getString("button.config"));
        configButton.setOnAction(e -> {
            ConfigDialog configDialog = new ConfigDialog((Stage) mainBorderPane.getScene().getWindow());
            configDialog.showAndWait();
        });

        // Set tooltips programmatically for all buttons
        loadFolderButton.setTooltip(new Tooltip(messages.getString("tooltip.loadFolder")));
        askButton.setTooltip(new Tooltip(messages.getString("tooltip.ask")));
        exportChatButton.setTooltip(new Tooltip(messages.getString("tooltip.exportChat")));
        resetChatButton.setTooltip(new Tooltip(messages.getString("tooltip.resetChat")));
        configButton.setTooltip(new Tooltip(messages.getString("tooltip.config")));

        // TableView setup for document list (now using LoadedDocument directly)
        scoreColumn.setCellValueFactory(cellData -> {
            Double score = cellData.getValue().getScore();
            if (score == null) return new javafx.beans.property.SimpleStringProperty("");
            return new javafx.beans.property.SimpleStringProperty(String.format("%.2f", score));
        });
        // Set cell factory to gray out empty scores
        scoreColumn.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            {
                setAlignment(javafx.geometry.Pos.CENTER);
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
        fileNameColumn.setCellFactory(col -> new javafx.scene.control.TableCell<>() {
            {
                setAlignment(javafx.geometry.Pos.CENTER);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
            }
        });
        openColumn.setCellFactory(col -> new TableCell<>() {
            private final Button openButton = new Button(messages.getString("button.openFile"));
            {
                openButton.setOnAction(e -> {
                    DocumentLoader.LoadedDocument doc = getTableView().getItems().get(getIndex());
                    openDocument(doc);
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
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

        if (deleteIndexCheckBox != null) {
            deleteIndexCheckBox.setVisible(false);
            deleteIndexCheckBox.setManaged(false);
        }
    }

    // Called from LogMessages to update the message bar
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
        chooser.setTitle(messages.getString("dialog.selectFolder.title"));
        File folder = chooser.showDialog(mainBorderPane.getScene().getWindow());
        if (folder == null) {
            setMessageBar(messages.getString("status.folderLoadCancelled"));
            return;
        }
        selectedFolder = folder.getAbsolutePath(); //TODO: Change to path.
        // Delete index folder if checkbox is selected
        if (deleteIndexCheckBox != null && deleteIndexCheckBox.isSelected()) {
            File indexFolder = new File(messages.getString("status.indexFolder"));
            if (indexFolder.exists()) {
                deleteDirectoryRecursively(indexFolder);
            }
        }
        vectorStore = new VectorStore(selectedFolder);
        ProgressDialog progressDialog = new ProgressDialog((Stage) mainBorderPane.getScene().getWindow(), messages.getString("progress.indexingDocuments"));
        progressDialog.show();
        mainBorderPane.setCursor(Cursor.WAIT);
        javafx.concurrent.Task<Void> loadTask = new IndexingTask( vectorStore, progressDialog );
        loadTask.setOnSucceeded(e -> {
            // UI  Updates are run on Platform thread
            loadedDocuments = ((IndexingTask)loadTask).getLoadedDocuments();
            Platform.runLater(() -> {
                progressDialog.close();
                mainBorderPane.setCursor(Cursor.DEFAULT);
                folderLabel.setText(messages.getString("label.selectedFolder") + ": " + selectedFolder);
                setMessageBar(messages.getString("status.indexLoaded"));
                indexStatusTable.getItems().clear();
                indexStatusTable.getItems().addAll(loadedDocuments);
                askButton.setDisable(false);
                updateIndexLoadedIndicator(IndexStatus.LOADED);
            });
        });
        loadTask.setOnCancelled(e -> Platform.runLater(() -> {
            progressDialog.close();
            mainBorderPane.setCursor(Cursor.DEFAULT);
            setMessageBar(messages.getString("status.folderLoadCancelled"));
            updateIndexLoadedIndicator(IndexStatus.NOT_SELECTED);
        }));
        loadTask.setOnFailed(e -> Platform.runLater(() -> {
            progressDialog.close();
            mainBorderPane.setCursor(Cursor.DEFAULT);
            setMessageBar(messages.getString("status.indexSaveFailed"));
            updateIndexLoadedIndicator(IndexStatus.FAILED);
        }));
        futures.add(executorService.submit(loadTask));
    }

    public void handleReset() {
        chatListView.getItems().clear();
        indexStatusTable.getItems().clear();
        indexStatusTable.getItems().addAll(loadedDocuments);
        setMessageBar(messages.getString("status.chatReset"));
    }

    public void shutdown() {
        for (Future<?> future : futures) future.cancel(true);
        executorService.shutdownNow();
    }

    private void handleAskQuestion() {
        final String question = questionField.getText();
        if (question == null || question.isBlank()) {
            return;
        }
        mainBorderPane.setCursor(Cursor.WAIT);
        LogMessages.log(messages.getString("QUESTION").replace("{0}", question));
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
            LogMessages.log("Responded" + answer);
            chatListView.getItems().add(messages.getString("chat.question") + ": " + question);
            chatListView.getItems().add(messages.getString("chat.answer") + ": " + answer);
            questionField.clear();
            mainBorderPane.setCursor(Cursor.DEFAULT);
        });
        askTask.setOnFailed(e -> {
            mainBorderPane.setCursor(Cursor.DEFAULT);
            LogMessages.log("Failed");
        });
        askTask.setOnCancelled(e -> {
            mainBorderPane.setCursor(Cursor.DEFAULT);
            LogMessages.log("Canceled");
        });
        mainBorderPane.setCursor(Cursor.WAIT);
        futures.add(executorService.submit(askTask));
    }

    private void openDocument(DocumentLoader.LoadedDocument doc) {
        setMessageBar(messages.getString("status.openingDocument").replace("{0}", doc.getFileName()));
        if (Desktop.isDesktopSupported())
        {
            try{
                Desktop.getDesktop().open( doc.absolutePath.toFile());
                LogMessages.log(messages.getString("status.openedDocument").replace("{0}", doc.getFileName()));
            }catch(Exception ex){
                LogMessages.log(messages.getString("status.failedToOpenDocument").replace("{0}", ex.getMessage()));
            }
        }
        setMessageBar("");
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
                System.out.println("Updating doc ID " + entry.getKey() + " with score " + entry.getValue());
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
        switch (status) {
            case LOADED:
                indexLoadedIndicator.setText(messages.getString("indicator.loaded"));
                indexLoadedIndicator.setStyle("-fx-font-size: 18; -fx-text-fill: green;");
                indexLoadedIndicator.setVisible(true);
                break;
            case FAILED:
                indexLoadedIndicator.setText(messages.getString("indicator.failed"));
                indexLoadedIndicator.setStyle("-fx-font-size: 18; -fx-text-fill: red;");
                indexLoadedIndicator.setVisible(true);
                break;
            case NOT_SELECTED:
                indexLoadedIndicator.setText(messages.getString("indicator.notSelected"));
                indexLoadedIndicator.setStyle("-fx-font-size: 18; -fx-text-fill: orange;");
                indexLoadedIndicator.setVisible(true);
                break;
        }
    }

    private void exportChatHistory() {
        java.util.List<String> chat = chatListView.getItems();
        if (chat == null || chat.isEmpty()) {
            setMessageBar(messages.getString("status.noChatHistoryToExport"));
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle(messages.getString("dialog.exportChatHistory.title"));
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showSaveDialog(new Stage());
        if (file != null) {
            try (java.io.PrintWriter out = new java.io.PrintWriter(file)) {
                for (String line : chat) {
                    out.println(line);
                }
                setMessageBar(messages.getString("status.chatHistoryExported").replace("{0}", file.getAbsolutePath()));
            } catch (Exception e) {
                setMessageBar(messages.getString("error.failedToExportChat").replace("{0}", e.getMessage()));
            }
        }
    }

    private void handleToggleFiles() {
        if (showingScoredFiles) {
            // Show all loaded files
            indexStatusTable.getItems().setAll(loadedDocuments);
            toggleFilesButton.setText("Show Scored Files");
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
            toggleFilesButton.setText("Show All Files");
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
