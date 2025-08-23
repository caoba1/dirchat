package org.dirchat.controller;

// Handles JavaFX UI interactions

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import org.dirchat.document.DocumentLoader;
import org.dirchat.document.TextChunker;
import org.dirchat.embedding.EmbeddingService;
import org.dirchat.embedding.VectorStore;
import org.dirchat.llm.ChatService;
import org.dirchat.model.Chunk;
import org.dirchat.util.LogMessages;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.awt.*;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.ResourceBundle;

public class UIController {
    private final DocumentLoader documentLoader = new DocumentLoader();
    @FXML
    private TextField questionField;
    @FXML
    private Button loadFolderButton;
    @FXML
    private Button askButton;
    @FXML
    private Label folderLabel;
    @FXML
    private ListView<org.dirchat.document.DocumentLoader.LoadedDocument> indexStatusList;
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
    private final ResourceBundle messages = ResourceBundle.getBundle("messages");

    private String selectedFolder;
    private VectorStore vectorStore;
    private List<org.dirchat.document.DocumentLoader.LoadedDocument> loadedDocuments;

    @FXML
    public void initialize() {
        loadFolderButton.setText(messages.getString("button.loadFolder"));
        askButton.setText(messages.getString("button.ask"));
        folderLabel.setText(messages.getString("label.noFolderSelected"));
        questionField.setPromptText(messages.getString("prompt.typeQuestion"));
        loadFolderButton.setOnAction(e -> handleLoadFolder());
        configButton.setOnAction(e -> {
            Stage stage = (Stage) mainBorderPane.getScene().getWindow();
            new org.dirchat.controller.ConfigDialog(stage).showAndWait(); // Show config dialog and wait for it to close
        });
        askButton.setOnAction(e -> handleAskQuestion());
        chatListView.getItems().clear();


        // Set custom cell factory for indexStatusList
        indexStatusList.setCellFactory(listView -> new ListCell<>() {
            private final Button openButton = new Button("Open");
            {
                openButton.setOnAction(e -> {
                    org.dirchat.document.DocumentLoader.LoadedDocument doc = getItem();
                    if (doc != null) {
                        openDocument(doc);
                    }
                });
            }
            @Override
            protected void updateItem(org.dirchat.document.DocumentLoader.LoadedDocument item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(item.fileName + " (ID: " + item.documentId + ")");
                    setGraphic(openButton);
                }
            }
        });

        // Initialize message bar
        setMessageBar("Welcome to dirchat! Please load a folder to start.", "black");

        // Initialize index loaded indicator
        updateIndexLoadedIndicator(IndexStatus.NOT_SELECTED);
        // Set initial folder label
        folderLabel.setText(messages.getString("label.noFolderSelected"));
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

    private void handleLoadFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(messages.getString("button.loadFolder"));
        File selectedFolderFile = chooser.showDialog(new Stage());
        if (selectedFolderFile != null) {
            selectedFolder = selectedFolderFile.getAbsolutePath();
            vectorStore = new VectorStore(selectedFolder);
            LogMessages.log("Loading folder: " + selectedFolder);
            setMessageBar("Loading folder: " + selectedFolder);
            folderLabel.setText(messages.getString("label.selectedFolder") + ": " + selectedFolder);
            mainBorderPane.setCursor(Cursor.WAIT);
            ProgressDialog progressDialog = new ProgressDialog((Stage) mainBorderPane.getScene().getWindow(), "Indexing documents...");
            progressDialog.show();

            javafx.concurrent.Task<Void> loadTask = new javafx.concurrent.Task<>() {
                @Override
                protected Void call() {
                    try {
                        loadedDocuments = documentLoader.loadDocumentsFromFolder(selectedFolder);
                        Platform.runLater(() -> setIndexStatusListItems(loadedDocuments));
                        EmbeddingService embeddingService = new EmbeddingService();
                        TextChunker textChunker = new TextChunker(6);
                        int totalDocs = loadedDocuments.size();
                        for (int docIdx = 0; docIdx < totalDocs; docIdx++) {
                            org.dirchat.document.DocumentLoader.LoadedDocument doc = loadedDocuments.get(docIdx);
                            final int currentDocIdx = docIdx;
                            List<Chunk> chunks = textChunker.chunkText(doc.content, doc.fileName, doc.documentId);
                            embeddingService.embedChunks(chunks, (message, progress) -> {
                                double docProgress = progress; // Per-file progress only
                                double overallProgress = ((double) currentDocIdx + progress) / totalDocs;
                                Platform.runLater(() -> {
                                    progressDialog.setMessage(message);
                                    progressDialog.setFileName("Indexing: " + doc.fileName);
                                    progressDialog.setFileProgress(docProgress);
                                    progressDialog.setProgress(overallProgress); // Only overall progress in main bar
                                });
                            });
                        }
                        Platform.runLater(() -> {
                            progressDialog.close();
                            mainBorderPane.setCursor(Cursor.DEFAULT);
                            updateIndexLoadedIndicator(IndexStatus.LOADED);
                            setMessageBar("Index loaded successfully!");
                        });
                    } catch (Exception ex) {
                        LogMessages.logWarning(ex.getMessage());
                        Platform.runLater(() -> {
                            progressDialog.close();
                            mainBorderPane.setCursor(Cursor.DEFAULT);
                            updateIndexLoadedIndicator(IndexStatus.FAILED);
                            setMessageBar("Error loading folder: " + ex.getMessage(), "red");
                        });
                    }
                    return null;
                }
            };
            new Thread(loadTask).start();
        }
    }

    private void handleAskQuestion() {
        String question = questionField.getText();
        if (question == null || question.isBlank()) {
            return;
        }
        mainBorderPane.setCursor(Cursor.WAIT);
        LogMessages.log("Question: " + question);
        setMessageBar("Question: " + question);
        chatListView.getItems().clear();
        String answer;
        try {
            ChatService chatService = new ChatService(vectorStore, loadedDocuments, new TextChunker(6), this);
            answer = chatService.answerQuestion(question, ChatService.MAX_SEARCH_RESULTS);
        } catch (Exception ex) {
            LogMessages.logWarning(ex.getMessage());
            answer = messages.getString("error.failedToAnswer");
            setMessageBar("Error: " + ex.getMessage(), "red");
        }
        LogMessages.log("Answer: " + answer); //Also setsMessageBar
        chatListView.getItems().add(messages.getString("chat.question") + ": " + question);
        chatListView.getItems().add(messages.getString("chat.answer") + ": " + answer);
        questionField.clear();
        mainBorderPane.setCursor(Cursor.DEFAULT);
    }

    private void openDocument(org.dirchat.document.DocumentLoader.LoadedDocument doc) {
        setMessageBar("Opening document: " + doc.fileName);
        // TODO: Add actual document opening logic here, e.g. open file at doc.absolutePath
        if (Desktop.isDesktopSupported())
        {
            try{
                Desktop.getDesktop().open(new File(doc.absolutePath));
                LogMessages.log("Opened document: " + doc.fileName);
            }catch(Exception ex){
                LogMessages.log("Failed to open document: " + ex.getMessage());
            }
        }
        setMessageBar("");
    }

    public void setIndexStatusListItems(List<org.dirchat.document.DocumentLoader.LoadedDocument> docs) {
        if (indexStatusList != null) {
            indexStatusList.getItems().setAll(docs);
        }
    }

    // In UIController.java
    public enum IndexStatus {
        LOADED, FAILED, NOT_SELECTED
    }

    public void updateIndexLoadedIndicator(IndexStatus status) {
        if (indexLoadedIndicator == null) return;
        switch (status) {
            case LOADED:
                indexLoadedIndicator.setText("✅");
                indexLoadedIndicator.setStyle("-fx-font-size: 18; -fx-text-fill: green;");
                indexLoadedIndicator.setVisible(true);
                break;
            case FAILED:
                indexLoadedIndicator.setText("❌");
                indexLoadedIndicator.setStyle("-fx-font-size: 18; -fx-text-fill: red;");
                indexLoadedIndicator.setVisible(true);
                break;
            case NOT_SELECTED:
                indexLoadedIndicator.setText("❓");
                indexLoadedIndicator.setStyle("-fx-font-size: 18; -fx-text-fill: orange;");
                indexLoadedIndicator.setVisible(true);
                break;
        }
    }
}
