package org.dirchat.controller;

// Handles JavaFX UI interactions

import org.dirchat.document.DocumentLoader;
import org.dirchat.document.ParsedDocument;
import org.dirchat.document.TextChunker;
import org.dirchat.embedding.EmbeddingService;
import org.dirchat.embedding.VectorStore;
import org.dirchat.llm.ChatService;
import org.dirchat.model.Chunk;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UIController {

    private final DocumentLoader documentLoader = new DocumentLoader();
    private final TextChunker textChunker = new TextChunker(6); // 6 paragraphs per chunk
    private final EmbeddingService embeddingService = new EmbeddingService();
    private final VectorStore vectorStore = new VectorStore();
    private ChatService chatService;
    @FXML
    private TextArea chatArea;
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
    public void initialize() {
        loadFolderButton.setOnAction(e -> handleLoadFolder());
        askButton.setOnAction(e -> handleAskQuestion());
    }

    private void handleLoadFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Document Folder");
        File selectedFolder = chooser.showDialog(new Stage());

        if (selectedFolder != null) {
            folderLabel.setText("Selected folder: " + selectedFolder.getAbsolutePath());
            List<ParsedDocument> documents = documentLoader.loadDocumentsFromFolder(selectedFolder.getAbsolutePath());
            List<Chunk> allChunks = new ArrayList<>();
            indexStatusList.getItems().clear();
            progressBar.setProgress(0);

            File vectorsDir = new File(selectedFolder, "vectorstore/vectors");
            if (!vectorsDir.exists()) {
                vectorsDir.mkdirs();
            }

            int total = documents.size();
            int current = 0;
            for (ParsedDocument doc : documents) {
                File indexFile = new File(vectorsDir, doc.getFileName() + ".index");
                Map<Chunk, List<Double>> docEmbeddings;
                if (indexFile.exists()) {
                    try {
                        docEmbeddings = vectorStore.load(indexFile.getAbsolutePath());
                        indexStatusList.getItems().add(doc.getFileName() + " - Index loaded");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        chatArea.appendText("❌ Failed to load index for " + doc.getFileName() + ": " + ex.getMessage() + "\n");
                        indexStatusList.getItems().add(doc.getFileName() + " - Index load failed");
                        continue;
                    }
                } else {
                    List<Chunk> docChunks = textChunker.chunkText(doc.getContent(), doc.getFileName());
                    docEmbeddings = embeddingService.embedChunks(docChunks, selectedFolder.getAbsolutePath(), doc.getFileName());
                    try {
                        vectorStore.save(docEmbeddings, indexFile.getAbsolutePath());
                        indexStatusList.getItems().add(doc.getFileName() + " - Index generated");
                    } catch (Exception ex) {
                        chatArea.appendText("❌ Failed to save index for " + doc.getFileName() + ": " + ex.getMessage() + "\n");
                        indexStatusList.getItems().add(doc.getFileName() + " - Index save failed");
                    }
                }
                vectorStore.addAll(docEmbeddings);
                current++;
                progressBar.setProgress((double) current / total);
            }

            chatService = new ChatService(vectorStore, documents, textChunker);
            chatArea.appendText("✅ Documents loaded and indexed.\n");
            progressBar.setProgress(1.0);
        }
    }

    private void handleAskQuestion() {
        String question = questionField.getText();
        if (question == null || question.isBlank()) return;

        // Generate a question if the field is empty (for demonstration, you can use a static or random question)
        if (question.isBlank()) {
            question = "What is this document about?";
        }

        String answer = chatService.answerQuestion(question, 5);
        chatArea.appendText("🧠 Q: " + question + "\n");
        chatArea.appendText("💬 A: " + answer + "\n\n");
        questionField.clear();
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
