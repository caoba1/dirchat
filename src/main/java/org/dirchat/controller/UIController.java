package org.dirchat.controller;

// Handles JavaFX UI interactions

import javafx.application.Platform;
import javafx.scene.Cursor;
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
import java.io.File;
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
    private ProgressBar folderProgressBar;
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

        // Register messageBar setters so backend/services can update the UI message bar
        org.dirchat.embedding.EmbeddingService.setMessageBarSetter(this::setMessageBar); // decoupled callback
        org.dirchat.document.DocumentLoader.setMessageBarSetter(this::setMessageBar);    // decoupled callback
        org.dirchat.llm.ChatService.setMessageBarSetter(this::setMessageBar);            // decoupled callback

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
    }

    public void setMessageBar(String message) {
        if (messageBar != null) {
            messageBar.setText(message);
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
            folderProgressBar.setProgress(0.0);
            indexLoadedIndicator.setVisible(false);
            ProgressDialog progressDialog = new ProgressDialog((Stage) mainBorderPane.getScene().getWindow(), "Indexing documents...");
            progressDialog.show();

            javafx.concurrent.Task<Void> loadTask = new javafx.concurrent.Task<>() {
                @Override
                protected Void call() {
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
                            double docProgress = ((double) currentDocIdx + progress) / totalDocs;
                            Platform.runLater(() -> {
                                progressDialog.setMessage("Indexing: " + doc.fileName + "\n" + message);
                                progressDialog.setProgress(docProgress);
                                folderProgressBar.setProgress(docProgress);
                            });
                        });
                    }
                    Platform.runLater(() -> {
                        progressDialog.close();
                        mainBorderPane.setCursor(Cursor.DEFAULT);
                        folderProgressBar.setProgress(1.0);
                        indexLoadedIndicator.setVisible(true);
                        setMessageBar("Index loaded successfully!");
                    });
                    return null;
                }
            };
            new Thread(loadTask).start();
        }
    }

    public void onFolderSelected(String folderPath) {
        this.selectedFolder = folderPath;
        this.vectorStore = new VectorStore(folderPath);
        // Optionally, re-initialize chatService or other components here
        // ...other logic for folder selection...
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
            LogMessages.log("Failed to answer question: " + ex.getMessage());
            setMessageBar("Error: " + ex.getMessage());
            answer = messages.getString("error.failedToAnswer");
        }
        LogMessages.log("Answer: " + answer);
        setMessageBar("Answer: " + answer);
        chatListView.getItems().add(messages.getString("chat.question") + ": " + question);
        chatListView.getItems().add(messages.getString("chat.answer") + ": " + answer);
        questionField.clear();
        mainBorderPane.setCursor(Cursor.DEFAULT);
    }

    // private void openDocument(int documentId, String fileName) {
    //     // Implement logic to open the document (e.g., show in viewer, open file, etc.)
    //     setMessageBar("Opening document: " + fileName);
    //     // TODO: Add actual document opening logic here
    // }

    private void openDocument(org.dirchat.document.DocumentLoader.LoadedDocument doc) {
        setMessageBar("Opening document: " + doc.fileName);
        // TODO: Add actual document opening logic here, e.g. open file at doc.absolutePath
    }

    // Helper to reconstruct chunk content from document and chunk index
    private String getChunkContent(Chunk chunk, List<DocumentLoader.LoadedDocument> documents, TextChunker textChunker) {
        for (DocumentLoader.LoadedDocument doc : documents) {
            if (doc.documentId == chunk.getDocumentId()) {
                List<Chunk> chunks = textChunker.chunkText(doc.content, doc.fileName, doc.documentId);
                for (Chunk c : chunks) {
                    if (c.getIndex() == chunk.getIndex()) {
                        return c.getContent();
                    }
                }
            }
        }
        return "";
    }

    public void setIndexStatusListItems(List<org.dirchat.document.DocumentLoader.LoadedDocument> docs) {
        if (indexStatusList != null) {
            indexStatusList.getItems().setAll(docs);
        }
    }
}
