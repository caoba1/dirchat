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
import org.dirchat.embedding.*;
import org.dirchat.llm.ChatService;

import java.util.concurrent.Future;
import java.util.LinkedHashMap;

import org.dirchat.util.LogMessages;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;

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


    private final java.util.concurrent.ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final List<Future<?>> futures = new java.util.ArrayList<>();


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
                    if (item.getScore() > 0.0) {
                        scoreText = messages.getString("label.score") + " " + String.format("%.3f", item.getScore());
                    }
                    scoreLabel.setText(item.getFileName() + " | " + scoreText);
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

        javafx.concurrent.Task<Void> loadTask = new IndexingTask( vectorStore, progressDialog );
        loadTask.setOnSucceeded(e -> {
            // UI  Updates are run on Platform thread
            loadedDocuments = ((IndexingTask) loadTask).getLoadedDocuments();
            Platform.runLater(() -> {
                progressDialog.close();
                mainBorderPane.setCursor(Cursor.DEFAULT);
                folderLabel.setText(messages.getString("label.selectedFolder") + ": " + selectedFolder);
                setMessageBar(messages.getString("status.indexLoaded"));
                indexStatusList.getItems().setAll(loadedDocuments);
                askButton.setDisable(false);
                updateIndexLoadedIndicator(UIController.IndexStatus.LOADED);
            });
        });
        loadTask.setOnCancelled(e -> {
            Platform.runLater(() -> {
                progressDialog.close();
                mainBorderPane.setCursor(Cursor.DEFAULT);
                setMessageBar(messages.getString("status.folderLoadCancelled"));
                updateIndexLoadedIndicator(UIController.IndexStatus.NOT_SELECTED);

            });
        });
        loadTask.setOnFailed(e -> {
            Platform.runLater(() -> {
                progressDialog.close();
                mainBorderPane.setCursor(Cursor.DEFAULT);
                setMessageBar(messages.getString("status.indexSaveFailed"));
                updateIndexLoadedIndicator(UIController.IndexStatus.FAILED);
            });
        });

        futures.add(executorService.submit(loadTask));
    }

    public void shutdown() {
        for (Future<?> future : futures) {
            future.cancel(true);
        }
        executorService.shutdownNow();
    }

    private void handleAskQuestion() {
        String question = questionField.getText();
        if (question == null || question.isBlank()) {
            return;
        }
        mainBorderPane.setCursor(Cursor.WAIT);

        LogMessages.log(messages.getString("QUESTION").replace("{0}", question));
        chatListView.getItems().clear();

        // TODO> Refactor to separate class. This may be a task.

        String answer;
        try {
            ChatService chatService = new ChatService(vectorStore,
                    loadedDocuments,
                    this);

            // Find documents and update their order
            chatService.findTopDocuments(question); // Saves top documents

            // Previous call calls setIndexStatusListItems internally on this UIController
            answer = chatService.answerTopDocuments(question);

            //answer = chatService.answerQuestion(question, ChatService.MAX_SEARCH_RESULTS);
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

    public void setIndexStatusListItems(List<DocumentLoader.LoadedDocument> docs) {
        if (indexStatusList != null) {
            indexStatusList.getItems().setAll(docs);
        }
    }

    /**
     * Updates loadedDocuments and indexStatusList to reflect top scored documents.
     * @param topScores LinkedHashMap of documentId to score, sorted by score descending
     */
    public void updateTopDocuments(LinkedHashMap<Integer, Double> topScores) {
        if (loadedDocuments == null || topScores == null || topScores.isEmpty()) return;
        // Map documentId to LoadedDocument for fast lookup
        java.util.Map<Integer, org.dirchat.document.DocumentLoader.LoadedDocument> docMap = new java.util.HashMap<>();
        for (org.dirchat.document.DocumentLoader.LoadedDocument doc : loadedDocuments) {
            docMap.put(doc.getDocumentId(), doc);
        }
        // Build new ordered list
        java.util.List<org.dirchat.document.DocumentLoader.LoadedDocument> orderedDocs = new java.util.ArrayList<>();
        for (java.util.Map.Entry<Integer, Double> entry : topScores.entrySet()) {
            org.dirchat.document.DocumentLoader.LoadedDocument doc = docMap.get(entry.getKey());
            if (doc != null) {
                doc.setScore(entry.getValue());
                orderedDocs.add(doc);
            }
        }
        // Update loadedDocuments and UI
        //loadedDocuments = orderedDocs;
        setIndexStatusListItems(orderedDocs);
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
                case "Name" -> docs.sort(java.util.Comparator.comparing(d -> d.getFileName()));
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
