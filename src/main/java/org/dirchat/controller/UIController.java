package org.dirchat.controller;

// Handles JavaFX UI interactions

import javafx.scene.Cursor;
import javafx.scene.layout.BorderPane;
import org.dirchat.document.DocumentLoader;
import org.dirchat.document.TextChunker;
import org.dirchat.embedding.EmbeddingService;
import org.dirchat.embedding.VectorStore;
import org.dirchat.llm.ChatService;
import org.dirchat.model.Chunk;
import org.dirchat.util.LogMessages;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.RandomAccessFile;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

public class UIController {

    private final DocumentLoader documentLoader = new DocumentLoader();
    private final TextChunker textChunker = new TextChunker(6); // 6 paragraphs per chunk
    private final EmbeddingService embeddingService = new EmbeddingService();
    private VectorStore vectorStore;
    private ChatService chatService;
    private String selectedFolder;
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
    private ProgressBar progressBar;
    @FXML
    private ProgressBar folderProgressBar;
    @FXML
    private ListView<String> chatListView;
    @FXML
    private Label messageBar;
    @FXML
    private BorderPane mainBorderPane;
    @FXML
    private Button configButton;

    private ResourceBundle messages = ResourceBundle.getBundle("messages");

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
        indexStatusList.setCellFactory(listView -> new ListCell<org.dirchat.document.DocumentLoader.LoadedDocument>() {
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

    private void setMessageBar(String message) {
        // This method is passed as a callback to backend/services
        // It updates the message bar in the UI for this controller instance
        if (messageBar != null) {
            messageBar.setText(message);
        }
    }

    private void handleLoadFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(messages.getString("button.loadFolder"));
        File selectedFolder = chooser.showDialog(new Stage());

        if (selectedFolder != null) {
            String msg = MessageFormat.format(org.dirchat.util.LogMessages.LOADING_FOLDER, selectedFolder.getAbsolutePath());
            LogMessages.logger.info(msg);
            setMessageBar(msg);
            folderLabel.setText(MessageFormat.format(messages.getString("label.selectedFolder"), selectedFolder.getAbsolutePath()));
            // Centralized: call EmbeddingService.createIndex to handle all indexing logic
            mainBorderPane.setCursor(Cursor.WAIT); // Set cursor to hourglass

            // Create index loader
            VectorStore vectorStore = new VectorStore(selectedFolder.getAbsolutePath());

            // Create embedding service and index the documents
            EmbeddingService embeddingService = new EmbeddingService();
            vectorStore.createIndex(selectedFolder.getAbsolutePath(), embeddingService);

            mainBorderPane.setCursor(Cursor.DEFAULT); // Reset cursor
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
        // Set cursor to WAIT: Asking question
        mainBorderPane.setCursor(Cursor.WAIT);

        // Log the question and show it in the message bar
        String msgQ = MessageFormat.format(org.dirchat.util.LogMessages.QUESTION, question);
        LogMessages.logger.info(msgQ);
        setMessageBar(msgQ);

        // Clear chatListView before showing new Q&A
        chatListView.getItems().clear();

        // Show top answers in chatListView (append question and answer)
        // RUN THIS IN A BACKGROUND THREAD
        String answer;
        try {
            answer = chatService.answerQuestion(question, ChatService.MAX_SEARCH_RESULTS);
        } catch (Exception ex) {
            LogMessages.logger.warning("Failed to answer question: " + ex.getMessage());
            setMessageBar("Error: " + ex.getMessage());
            answer = messages.getString("error.failedToAnswer");
        }

        String msgA = MessageFormat.format(org.dirchat.util.LogMessages.ANSWER, answer);
        LogMessages.logger.info(msgA);
        setMessageBar(msgA);

        chatListView.getItems().add(MessageFormat.format(messages.getString("chat.question"), question));
        chatListView.getItems().add(MessageFormat.format(messages.getString("chat.answer"), answer));
        questionField.clear();

        // Set cursor back to DEFAULT
        mainBorderPane.setCursor(Cursor.DEFAULT); //TODO: Run this in a background thread to avoid blocking the UI
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
