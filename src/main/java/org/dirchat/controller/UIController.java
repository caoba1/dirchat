package org.dirchat.controller;

// Handles JavaFX UI interactions

import javafx.scene.Cursor;
import javafx.scene.layout.BorderPane;
import org.dirchat.document.DocumentLoader;
import org.dirchat.document.ParsedDocument;
import org.dirchat.document.TextChunker;
import org.dirchat.embedding.EmbeddingService;
import org.dirchat.embedding.VectorStore;
import org.dirchat.llm.ChatService;
import org.dirchat.model.Chunk;

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
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class UIController {

    private final DocumentLoader documentLoader = new DocumentLoader();
    private final TextChunker textChunker = new TextChunker(6); // 6 paragraphs per chunk
    private final EmbeddingService embeddingService = new EmbeddingService();
    private final VectorStore vectorStore = new VectorStore();
    private ChatService chatService;
    @FXML
    private TextField questionField;
    @FXML
    private Button loadFolderButton;
    @FXML
    private Button askButton;
    @FXML
    private Label folderLabel;
    @FXML
    private ListView<String> indexStatusList;
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

    private static final Logger logger = Logger.getLogger(UIController.class.getName());
    static {
        try {
            FileHandler fh = new FileHandler("dirchat.log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setUseParentHandlers(true);
        } catch (Exception e) {
            System.err.println("Failed to set up logger in UIController: " + e.getMessage());
        }
    }

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
            logger.info(msg);
            setMessageBar(msg);
            folderLabel.setText(MessageFormat.format(messages.getString("label.selectedFolder"), selectedFolder.getAbsolutePath()));
            // Centralized: call EmbeddingService.createIndex to handle all indexing logic
            mainBorderPane.setCursor(Cursor.WAIT); // Set cursor to hourglass
            embeddingService.createIndex(selectedFolder.getAbsolutePath());
            mainBorderPane.setCursor(Cursor.DEFAULT); // Reset cursor
        }
    }

    private void handleAskQuestion() {
        String question = questionField.getText();
        if (question == null || question.isBlank())
        {
            return;
        }
        // Set cursor to WAIT: Asking question
        mainBorderPane.setCursor(Cursor.WAIT);

        // Log the question and show it in the message bar
        String msgQ = MessageFormat.format(org.dirchat.util.LogMessages.QUESTION, question);
        logger.info(msgQ);
        setMessageBar(msgQ);

        // Clear previous index status
        // Show top document summaries in indexStatusList
        // RUN THIS IN A BACKGROUND THREAD
        List<String> topSummaries;
        try {
            topSummaries = chatService.getTopDocumentSummaries(question);
        } catch (Exception ex) {
            logger.warning("Failed to get top document summaries: " + ex.getMessage());
            setMessageBar("Error: " + ex.getMessage());
            topSummaries = new ArrayList<>();
        }
        indexStatusList.getItems().setAll(topSummaries);indexStatusList.getItems().setAll(topSummaries);

        // Clear chatListView before showing new Q&A
        chatListView.getItems().clear();

        // Show top answers in chatListView (append question and answer)
        // RUN THIS IN A BACKGROUND THREAD
        String answer;
        try {
            answer = chatService.answerQuestion(question, ChatService.MAX_SEARCH_RESULTS);
        } catch (Exception ex) {
            logger.warning("Failed to answer question: " + ex.getMessage());
            setMessageBar("Error: " + ex.getMessage());
            answer = messages.getString("error.failedToAnswer");
        }

        String msgA = MessageFormat.format(org.dirchat.util.LogMessages.ANSWER, answer);
        logger.info(msgA);
        setMessageBar(msgA);

        chatListView.getItems().add(MessageFormat.format(messages.getString("chat.question"), question));
        chatListView.getItems().add(MessageFormat.format(messages.getString("chat.answer"), answer));
        questionField.clear();

        // Set cursor back to DEFAULT
        mainBorderPane.setCursor(Cursor.DEFAULT); //TODO: Run this in a background thread to avoid blocking the UI
    }

    // Helper to reconstruct chunk content from document and chunk index
    private String getChunkContent(Chunk chunk, List<ParsedDocument> documents, TextChunker textChunker) {
        for (ParsedDocument doc : documents) {
            if (doc.getFileName().equals(chunk.getSourceFile())) {
                List<Chunk> chunks = textChunker.chunkText(doc.getContent(), doc.getFileName());
                for (Chunk c : chunks) {
                    if (c.getIndex() == chunk.getIndex()) {
                        return c.getContent();
                    }
                }
            }
        }
        return "";
    }
}
