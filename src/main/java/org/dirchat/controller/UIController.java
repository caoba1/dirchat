package org.dirchat.controller;

// Handles JavaFX UI interactions

import javafx.application.Platform;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
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
import javafx.stage.FileChooser;
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
        loadFolderButton.setText(messages.getString("button.loadFolder"));
        askButton.setText(messages.getString("button.ask"));
        folderLabel.setText(messages.getString("label.noFolderSelected"));
        questionField.setPromptText(messages.getString("prompt.typeQuestion"));
        loadFolderButton.setOnAction(e -> handleLoadFolder());
        configButton.setOnAction(e -> {
            Stage stage = (Stage) mainBorderPane.getScene().getWindow();
            new org.dirchat.controller.ConfigDialog(stage).showAndWait();
            updateOutputModeIndicator(); // Update after config dialog closes
        });
        askButton.setOnAction(e -> handleAskQuestion());
        chatListView.getItems().clear();
        askButton.setDisable(true); // Disable until folder is loaded

        // Sort options
        sortComboBox.getItems().addAll("Name", "Score", "Date");
        sortComboBox.setOnAction(e -> applySortAndFilter());
        // Filter options
        filterComboBox.getItems().addAll("All", "> 0.5", "> 0.7", "> 0.9");
        filterComboBox.setOnAction(e -> applySortAndFilter());
        // Export chat
        exportChatButton.setOnAction(e -> exportChatHistory());
        // Custom cell factory for indexStatusList (highlight selection)
        indexStatusList.setCellFactory(listView -> new ListCell<>() {
            private final Button openButton = new Button("Open");
            private final Label scoreLabel = new Label();
            private final HBox hbox = new HBox(8);
            {
                openButton.setTooltip(new Tooltip("Open this file"));
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
                    String scoreText = "Score: N/A";
                    if (item.metadata != null && item.metadata.get("score") != null) {
                        scoreText = "Score: " + item.metadata.get("score");
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
        // Custom cell factory for chatListView (user/AI distinction, copy button)
        chatListView.setCellFactory(listView -> new ListCell<>() {
            private final Button copyButton = new Button("Copy");
            private final HBox hbox = new HBox(8);
            private final Label iconLabel = new Label();
            {
                copyButton.setTooltip(new Tooltip("Copy this message"));
                copyButton.setOnAction(e -> {
                    String text = getItem();
                    if (text != null) {
                        final java.awt.datatransfer.StringSelection selection = new java.awt.datatransfer.StringSelection(text);
                        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
                        setMessageBar("Copied to clipboard");
                    }
                });
                hbox.getChildren().addAll(iconLabel, copyButton);
            }
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    if (item.startsWith(messages.getString("chat.question"))) {
                        iconLabel.setText("👤");
                        setStyle("-fx-text-fill: #0077cc;");
                    } else if (item.startsWith(messages.getString("chat.answer"))) {
                        iconLabel.setText("🤖");
                        setStyle("-fx-text-fill: #228B22;");
                    } else {
                        iconLabel.setText("");
                        setStyle("");
                    }
                    setText(item);
                    setGraphic(hbox);
                }
            }
        });

        // Initialize message bar
        setMessageBar("Welcome to dirchat! Please load a folder to start.", "black");

        // Initialize index loaded indicator
        updateIndexLoadedIndicator(IndexStatus.NOT_SELECTED);
        // Set initial folder label
        folderLabel.setText(messages.getString("label.noFolderSelected"));
        updateOutputModeIndicator();
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
            java.io.File indexDbFile = new java.io.File(selectedFolder + "/.dirchat/vectorstore.db");
            boolean indexExists = indexDbFile.exists() && indexDbFile.length() > 0;
            if (!indexExists) {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.CONFIRMATION);
                alert.setTitle("No Index Found");
                alert.setHeaderText(null);
                alert.setContentText("No index found for this folder. Do you want to generate a new index? Press Yes to generate, or Cancel to abort.");
                javafx.scene.control.ButtonType yesBtn = new javafx.scene.control.ButtonType("Yes", javafx.scene.control.ButtonBar.ButtonData.YES);
                javafx.scene.control.ButtonType cancelBtn = new javafx.scene.control.ButtonType("Cancel", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE);
                alert.getButtonTypes().setAll(yesBtn, cancelBtn);
                javafx.scene.control.ButtonType result = alert.showAndWait().orElse(cancelBtn);
                if (result != yesBtn) {
                    setMessageBar("Folder loading cancelled. No index generated.");
                    return;
                }
            }
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
                            askButton.setDisable(false); // Enable after folder is loaded
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
