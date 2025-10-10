package org.dirchat.controller;

// Handles JavaFX UI interactions

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import org.dirchat.document.DocumentLoader;
import org.dirchat.document.TextChunker;
import org.dirchat.embedding.EmbeddingService;
import org.dirchat.embedding.VectorStore;
import org.dirchat.llm.ChatService;
import org.dirchat.model.Chunk;
import org.dirchat.util.Dirchat;
import org.dirchat.util.LogMessages;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.*;
import java.io.File;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

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
    @FXML
    private Label outputModeIndicator;
    @FXML
    private ComboBox<String> sortComboBox;
    @FXML
    private ComboBox<String> filterComboBox;
    @FXML
    private Button exportChatButton;
    private final ResourceBundle messages = ResourceBundle.getBundle("messages");

    private String selectedFolder;
    private VectorStore vectorStore;
    private List<org.dirchat.document.DocumentLoader.LoadedDocument> loadedDocuments;

    @FXML
    public void initialize() {
        // Initial UI setup - arranged for clarity
        loadFolderButton.setText(messages.getString("button.loadFolder"));
        loadFolderButton.setTooltip(new Tooltip(messages.getString("tooltip.loadFolder"))); // change to button.loadFolder.tooltip
        loadFolderButton.setOnAction(e -> handleLoadFolder());
        folderLabel.setText(messages.getString("label.noFolderSelected"));

        questionField.setPromptText(messages.getString("prompt.typeQuestion"));
        askButton.setText(messages.getString("button.ask"));
        askButton.setTooltip(new Tooltip(messages.getString("tooltip.ask"))); // change to button.ask.tooltip
        askButton.setOnAction(e -> handleAskQuestion());

        exportChatButton.setTooltip(new Tooltip(messages.getString("tooltip.exportChat"))); // change to button.exportChat.tooltip
        exportChatButton.setText(messages.getString("button.exportChat"));
        exportChatButton.setOnAction(e -> exportChatHistory());

        configButton.setText(messages.getString("button.config"));
        configButton.setTooltip(new Tooltip(messages.getString("tooltip.config")));
        configButton.setOnAction(e -> {
            ConfigDialog configDialog = new ConfigDialog((Stage) mainBorderPane.getScene().getWindow());
            configDialog.showAndWait();
            updateOutputModeIndicator();
        });
        updateOutputModeIndicator();

        // Localized sort options
        sortComboBox.getItems().addAll(
            messages.getString("sort.name"),
            messages.getString("sort.score"),
            messages.getString("sort.date")
        );
        sortComboBox.setPromptText(messages.getString("prompt.sortBy"));
        sortComboBox.setOnAction(e -> applySortAndFilter());

        // Localized filter options
        filterComboBox.getItems().addAll(
            messages.getString("filter.all"),
            messages.getString("filter.05"),
            messages.getString("filter.07"),
            messages.getString("filter.09")
        );
        filterComboBox.setPromptText(messages.getString("prompt.filterByScore"));
        filterComboBox.setOnAction(e -> applySortAndFilter());


        // Custom cell factory for indexStatusList (highlight selection)
        indexStatusList.setCellFactory(listView -> new ListCell<>() {
            private final Button openButton = new Button(messages.getString("button.openFile"));
            private final Label scoreLabel = new Label();
            private final HBox hbox = new HBox(8);
            {
                openButton.setTooltip(new Tooltip(messages.getString("button.openFile.tooltip")));
                openButton.setOnAction(e -> {
                    org.dirchat.document.DocumentLoader.LoadedDocument doc = getItem();
                    if (doc != null) {
                        openDocument(doc);
                    }
                });
                hbox.getChildren().addAll(scoreLabel, openButton);
            }
            @Override
            protected void updateItem(org.dirchat.document.DocumentLoader.LoadedDocument item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                } else {
                    String scoreText = messages.getString("label.scoreNA");
                    if (item.metadata != null && item.metadata.get("score") != null) {
                        scoreText = messages.getString("label.score") + " " + item.metadata.get("score");
                    }
                    scoreLabel.setText(item.fileName + " | " + scoreText);
                    setGraphic(hbox);
                    if (isSelected()) {
                        setStyle("-fx-background-color: #cce6ff;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        chatListView.getItems().clear();
        askButton.setDisable(true); // Disable until folder is loaded
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
        vectorStore = new VectorStore(selectedFolder);

        ProgressDialog progressDialog = new ProgressDialog((Stage) mainBorderPane.getScene().getWindow(), messages.getString("progress.indexingDocuments"));
        progressDialog.show();
        mainBorderPane.setCursor(Cursor.WAIT);
        javafx.concurrent.Task<Void> loadTask = new javafx.concurrent.Task<>() {
            @Override
            protected Void call() {
                try {
                    List<DocumentLoader.LoadedDocument> docs = new java.util.ArrayList<>();
                    LogMessages.log("[Indexing] Task started");
                    if (vectorStore.indexExists()) {
                        LogMessages.log("[Indexing] Index exists, loading from DB");
                        docs = vectorStore.loadIndexing();
                        updateMessage(messages.getString("status.indexLoaded"));
                    } else {
                        LogMessages.log("[Indexing] Index does not exist or is not complete. Processing files");

                        //org.dirchat.document.DocumentLoader loader = new org.dirchat.document.DocumentLoader();
                        // TODO: Use DocumentLoader to get files.
                        //List<org.dirchat.document.DocumentLoader.LoadedDocument> docs = loader.loadDocument

                        File[] files = folder.listFiles();
                        final int total = files != null ? files.length : 0;
                        AtomicInteger count = new AtomicInteger(0);
                        EmbeddingService embeddingService = new EmbeddingService();
                        for (File file : files) {
                            if (progressDialog.isCancelled() || isCancelled()) {
                                LogMessages.log("[Indexing] Cancelled by user (file loop)");
                                updateMessage(messages.getString("status.folderLoadCancelled"));
                                break;
                            }
                            if (file.isFile() && Dirchat.isSupported(file.toPath())) {
                                count.incrementAndGet();
                                LogMessages.log("[Indexing] Processing file: " + file.getName());
                                updateMessage(messages.getString("progress.loadingFile").replace("{0}", file.getName()));
                                Platform.runLater(() -> {
                                    progressDialog.setFileName(file.getName());
                                    progressDialog.setProgress((double) count.get() / total);
                                });
                                org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
                                String content = "";
                                try {
                                    content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
                                    org.apache.tika.parser.AutoDetectParser parser = new org.apache.tika.parser.AutoDetectParser();
                                    org.apache.tika.sax.BodyContentHandler handler = new org.apache.tika.sax.BodyContentHandler(-1);
                                    try (java.io.FileInputStream stream = new java.io.FileInputStream(file)) {
                                        parser.parse(stream, handler, metadata);
                                    }
                                } catch (Exception ex) {
                                    LogMessages.logWarning("[Indexing] Error reading file: " + file.getName() + " - " + ex.getMessage());
                                }
                                int documentId = file.getAbsolutePath().hashCode();
                                DocumentLoader.LoadedDocument doc = new DocumentLoader.LoadedDocument(documentId, file.getName(), content, file.getAbsolutePath());
                                doc.setMetadata(metadata);
                                // Chunk and embed
                                List<Chunk> docChunks = embeddingService.textChunker.chunkText(content, file.getName(), documentId);
                                Map<Chunk, List<Double>> docEmbeddings = new java.util.HashMap<>();
                                final int chunkCount = docChunks.size();
                                AtomicInteger chunkIdx = new AtomicInteger(0);
                                for (Chunk chunk : docChunks) {
                                    if (progressDialog.isCancelled() || isCancelled()) {
                                        LogMessages.log("[Indexing] Cancelled by user (chunk loop)");
                                        updateMessage(messages.getString("status.folderLoadCancelled"));
                                        break;
                                    }
                                    LogMessages.log("[Indexing] Embedding chunk " + chunkIdx.get() + "/" + chunkCount + " for file: " + file.getName());
                                    List<Double> embedding = embeddingService.embedChunk(chunk, selectedFolder, file.getName());
                                    docEmbeddings.put(chunk, embedding);
                                    chunkIdx.incrementAndGet();
                                    Platform.runLater(() -> progressDialog.setFileProgress((double) chunkIdx.get() / chunkCount));
                                    if (progressDialog.isCancelled() || isCancelled()) {
                                        LogMessages.log("[Indexing] Cancelled by user (after embedding chunk)");
                                        updateMessage(messages.getString("status.folderLoadCancelled"));
                                        break;
                                    }
                                }
                                // Save embeddings to DB
                                vectorStore.saveDocumentEmbeddingsToSqlite(doc, docEmbeddings);
                                // Save document metadata (including TOC)
                                vectorStore.extractAndSaveDocumentMetadata(doc);
                                docs.add(doc);
                                Platform.runLater(() -> progressDialog.setFileProgress(0)); // Reset chunk progress bar
                            }
                        }
                        updateMessage(messages.getString("status.indexLoaded"));
                    }
                    loadedDocuments = docs;
                    LogMessages.log("[Indexing] Task completed");
                } catch (Exception e) {
                    if (e instanceof java.net.ConnectException ||
                    e.getCause() instanceof java.net.ConnectException) {
                        LogMessages.logWarning("[Indexing] Connection error: " + e.getMessage());
                        //updateMessage(messages.getString("error.connectionError") + ": " + e.getMessage());
                        updateIndexLoadedIndicator(IndexStatus.FAILED);
                    } else {
                        LogMessages.logWarning("[Indexing] Exception: " + e.getMessage());
                        //updateMessage(messages.getString("status.folderLoadError") + ": " + e.getMessage());
                    }
                    this.cancel(); // TODO -> Override call(), succeded() and call cancel() on failed.
                }
                return null;
            }
        };
        loadTask.setOnSucceeded(e -> {
            Platform.runLater(() -> {
                progressDialog.close();
                mainBorderPane.setCursor(Cursor.DEFAULT);
                folderLabel.setText(messages.getString("label.selectedFolder") + ": " + selectedFolder);
                setMessageBar(messages.getString("status.indexLoaded"));
                indexStatusList.getItems().setAll(loadedDocuments);
                askButton.setDisable(false);
            });
        });
        loadTask.setOnCancelled(e -> {
            Platform.runLater(() -> {
                progressDialog.close();
                mainBorderPane.setCursor(Cursor.DEFAULT);
                setMessageBar(messages.getString("status.folderLoadCancelled"));
            });
        });
        loadTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                progressDialog.close();
                mainBorderPane.setCursor(Cursor.DEFAULT);
                setMessageBar(messages.getString("status.indexSaveFailed"));
            });
        });

        new Thread(loadTask).start();
    }

    private void handleAskQuestion() {
        String question = questionField.getText();
        if (question == null || question.isBlank()) {
            return;
        }
        mainBorderPane.setCursor(Cursor.WAIT);
        LogMessages.log(messages.getString("QUESTION").replace("{0}", question));
        setMessageBar(messages.getString("QUESTION").replace("{0}", question));
        chatListView.getItems().clear();
        String answer;
        try {
            ChatService chatService = new ChatService(vectorStore, loadedDocuments, new TextChunker(6), this);
            answer = chatService.answerQuestion(question, ChatService.MAX_SEARCH_RESULTS);
        } catch (Exception ex) {
            LogMessages.logWarning(ex.getMessage());
            answer = messages.getString("error.failedToAnswer");
            setMessageBar(messages.getString("error.failedToAnswer") + ": " + ex.getMessage(), "red");
        }
        LogMessages.log(messages.getString("ANSWER").replace("{0}", answer));
        chatListView.getItems().add(messages.getString("chat.question") + ": " + question);
        chatListView.getItems().add(messages.getString("chat.answer") + ": " + answer);
        questionField.clear();
        mainBorderPane.setCursor(Cursor.DEFAULT);
    }

    private void openDocument(org.dirchat.document.DocumentLoader.LoadedDocument doc) {
        setMessageBar(messages.getString("status.openingDocument").replace("{0}", doc.fileName));
        if (Desktop.isDesktopSupported())
        {
            try{
                Desktop.getDesktop().open(new File(doc.absolutePath));
                LogMessages.log(messages.getString("status.openedDocument").replace("{0}", doc.fileName));
            }catch(Exception ex){
                LogMessages.log(messages.getString("status.failedToOpenDocument").replace("{0}", ex.getMessage()));
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

    private void updateOutputModeIndicator() {
        String mode = org.dirchat.util.ConfigManager.getOutputMode();
        String localized;
        switch (mode) {
            case "truncate":
                localized = messages.getString("output.mode.truncate");
                break;
            case "summarize":
                localized = messages.getString("output.mode.summarize");
                break;
            default:
                localized = messages.getString("output.mode.none");
        }
        if (outputModeIndicator != null) {
            outputModeIndicator.setText(messages.getString("output.mode") + ": " + localized);
        }
    }

    private void applySortAndFilter() {
        if (loadedDocuments == null) return;
        final List<org.dirchat.document.DocumentLoader.LoadedDocument> docs = new java.util.ArrayList<>(loadedDocuments);
        // Filter
        String filter = filterComboBox.getValue();
        if (filter != null && !filter.equals("All")) {
            final double threshold;
            if (filter.equals("> 0.5")) threshold = 0.5;
            else if (filter.equals("> 0.7")) threshold = 0.7;
            else if (filter.equals("> 0.9")) threshold = 0.9;
            else threshold = 0.0;
            docs.removeIf(doc -> {
                try {
                    return Double.parseDouble(doc.metadata.get("score")) <= threshold;
                } catch (Exception e) {
                    return true;
                }
            });
        }
        // Sort
        String sort = sortComboBox.getValue();
        if (sort != null) {
            switch (sort) {
                case "Name" -> docs.sort(java.util.Comparator.comparing(d -> d.fileName));
                case "Score" -> docs.sort((a, b) -> {
                    try {
                        double sa = Double.parseDouble(a.metadata.get("score"));
                        double sb = Double.parseDouble(b.metadata.get("score"));
                        return Double.compare(sb, sa);
                    } catch (Exception e) {
                        return 0;
                    }
                });
                case "Date" -> docs.sort(java.util.Comparator.comparing(d -> d.metadata.get("date"), java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
            }
        }
        setIndexStatusListItems(docs);
    }

    private void exportChatHistory() {
        java.util.List<String> chat = chatListView.getItems();
        if (chat == null || chat.isEmpty()) {
            setMessageBar("No chat history to export.");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Chat History");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Text Files", "*.txt"));
        File file = fileChooser.showSaveDialog(new Stage());
        if (file != null) {
            try (java.io.PrintWriter out = new java.io.PrintWriter(file)) {
                for (String line : chat) {
                    out.println(line);
                }
                setMessageBar("Chat history exported to " + file.getAbsolutePath());
            } catch (Exception e) {
                setMessageBar("Failed to export chat: " + e.getMessage());
            }
        }
    }
}
