package org.dirchat.llm;

import org.dirchat.controller.UIController;
import org.dirchat.embedding.EmbeddingService; //todo: just use vector store directly
import org.dirchat.embedding.VectorStore;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.ollama.OllamaLanguageModel;
import org.dirchat.util.ConfigManager;
import org.dirchat.util.LogMessages;
import org.dirchat.document.DocumentLoader.LoadedDocument;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.dirchat.util.ConfigManager;

public class ChatService implements Closeable {

    private EmbeddingModel embeddingModel;
    private final LanguageModel languageModel;
    private final VectorStore vectorStore;
    private final List<LoadedDocument> documents;
    public final UIController uiController;// WARNING
    private List<org.dirchat.document.TopChunkResult> topChunks;


    public ChatService(VectorStore vectorStore, List<LoadedDocument> documents, UIController uiController) {
        this.vectorStore = vectorStore;
        this.documents = documents;
        this.uiController = uiController;

        // Todo> Initialize the embedding model based on configuration and store here.
        String indexingMethod = ConfigManager.getIndexingMethod();
        if (indexingMethod.equalsIgnoreCase("sbert")) {
            this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(ConfigManager.getOllamaBaseUrl())
                .modelName("all-minilm:33m") // or other SBERT model, tinyllama
                .build();
        } else {
            this.embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(ConfigManager.getOllamaBaseUrl())
                    .modelName("tinyllama") // or mistral, llama2, gemma, etc.
                    .build();
        }

        // Initialize the language model
        this.languageModel = OllamaLanguageModel.builder()
                    .baseUrl(ConfigManager.getOllamaBaseUrl())
                    .modelName("mistral") // or [mistral, tinyllama] llama2, gemma, etc.
                    .timeout(Duration.ofMinutes(15))  // or even longer
                    .build();

        verify();
    }

    private void verify() {
        if (vectorStore == null) {
            throw new IllegalStateException("VectorStore is not initialized.");
        }
        if (embeddingModel == null) {
            throw new IllegalStateException("EmbeddingModel is not initialized.");
        }
        if (languageModel == null) {
            throw new IllegalStateException("LanguageModel is not initialized.");
        }

        // Attemp connection to vector store
        if (!vectorStore.indexExists()) {
            throw new IllegalStateException("Problem connecting to DB");
        }

        // Attempt tp cpnnect to the embedding model
        try {
            float[] testEmbedding = embeddingModel.embed("dirchat").content().vector();
            if (testEmbedding == null || testEmbedding.length == 0) {
                throw new IllegalStateException("Failed to get embedding from EmbeddingModel.");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to connect to EmbeddingModel: " + e.getMessage(), e);
        }

        // Attempt to connect to the language model
        try {
            dev.langchain4j.model.output.Response<String> testResponse = languageModel.generate("dirchat test");
            if (testResponse == null || testResponse.content() == null || testResponse.content().isEmpty()) {
                throw new IllegalStateException("Failed to get response from LanguageModel.");
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to connect to LanguageModel: " + e.getMessage(), e);
        }

        LogMessages.log("ChatService initialized with embedding model: " +
                embeddingModel.getClass().getSimpleName() +
                " and language model: " +
                languageModel.getClass().getSimpleName());
    }

    // RAG
    public List<org.dirchat.document.TopChunkResult> getTopDocumentChunks(String question) {
        float[] vector = embeddingModel.embed(question).content().vector();
        List<Double> queryVector = new java.util.ArrayList<>(vector.length);
        for (float v : vector)
        {
            queryVector.add((double) v);
        }
        List<org.dirchat.document.TopChunkResult> results = null;
        try {
            results = vectorStore.compoundSearchWithScores(queryVector, ConfigManager.getTopK());
        } catch (IOException e) {
            LogMessages.logWarning("Failed to search vector store (I/O): " + e.getMessage());
            throw new RuntimeException(e);
        } catch (SQLException e) {
            LogMessages.logWarning("Failed to search vector store (SQL): " + e.getMessage());
            throw new RuntimeException(e);
        }
        return results;
    }

    public void findTopDocuments(String query) {
        topChunks = getTopDocumentChunks(query);
        if (uiController != null && topChunks != null) {
            // Build a LinkedHashMap of documentId to score for UI
            java.util.LinkedHashMap<Integer, Double> topDocuments = new java.util.LinkedHashMap<>();
            for (org.dirchat.document.TopChunkResult tcr : topChunks) {
                topDocuments.put(tcr.getDocumentId(), tcr.getScore());
            }
            uiController.updateTopDocuments(topDocuments);
        }
    }

    public String answerTopDocuments(String question) {
        // Build context from top chunks using chunkId
        List<String> topContent = new ArrayList<>();
        for (org.dirchat.document.TopChunkResult tcr : topChunks) {
            String content = getChunkContentById(tcr.getChunkId());
            if (content != null) {
                topContent.add(content);
            }
        }
        String stringPrompt = "You are to answer the question based on the following file contents:\n";
        String stringDocumentList = IntStream.range(0, topContent.size()).mapToObj(i -> { return "`" + topContent.get(i) + "`";}).collect(Collectors.joining("\n"));
        stringPrompt += "\n" + stringDocumentList;
        stringPrompt += "\n\nThe question is:\n" + question;


        LogMessages.log("Q:" + stringPrompt);
        dev.langchain4j.model.output.Response<String> response = languageModel.generate(stringPrompt);
        // Obtain response
        String answer = response.content();
        LogMessages.log("RE:" + answer);
        return answer;
    }

    @Override
    public void close() throws IOException {
        LogMessages.log("ChatService closed.");
    }

    // Helper to retrieve chunk cursorPointer by chunkId from the database via VectorStore
    private String getChunkContentById(int chunkId) {
        long cursorPointer = vectorStore.getChunkCursorPointer(chunkId);
        int documentId = vectorStore.getChunkDocumentId(chunkId);
        String sourceFilePath = vectorStore.getDocumentPathById(documentId);
        if (sourceFilePath == null || cursorPointer < 0) {
            return null;
        }
        try {
            org.apache.tika.parser.AutoDetectParser parser = new org.apache.tika.parser.AutoDetectParser();
            org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
            org.apache.tika.parser.ParseContext context = new org.apache.tika.parser.ParseContext();
            org.dirchat.document.StreamSplitter splitter = new org.dirchat.document.StreamSplitter((int)cursorPointer);
            try (java.io.InputStream stream = new java.io.FileInputStream(sourceFilePath)) {
                parser.parse(stream, splitter, metadata, context);
            }
            return splitter.getExtractedChunk();
        } catch (Exception e) {
            org.dirchat.util.LogMessages.log("Error extracting chunk content for chunkId " + chunkId + ": " + e.getMessage());
            e.printStackTrace(); // Log full
        }
        return null;
    }
    // Optionally, provide a getter for the start prompt
    // TODO Move to chatService
    private static final String PROMPT_FILE = "prompt.txt";
    public static String getStartPrompt() {
        String startPromptCache = null;
        if (startPromptCache != null) {
            return startPromptCache;
        }
        try {
            // Read prompt.txt from resources
            ClassLoader classLoader = EmbeddingService.class.getClassLoader();
            try (java.io.InputStream is = classLoader.getResourceAsStream(PROMPT_FILE)) {
                if (is == null) {
                    throw new RuntimeException("prompt.txt not found in resources");
                }
                startPromptCache = new String(is.readAllBytes()).trim();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to read prompt file from resources", e);
        }
        return startPromptCache;
    }

    public String getTopDocumentCitations() {
        List<String> filePaths = new ArrayList<>();
        for (org.dirchat.document.TopChunkResult tcr : topChunks) {
            int documentId = tcr.getDocumentId();
            String sourceFilePath = vectorStore.getDocumentPathById(documentId);
            if (sourceFilePath != null) {
                filePaths.add( Paths.get(vectorStore.getDocumentPathById(documentId)).getFileName().toString());
            }
        }

        String citations = IntStream.range(1, filePaths.size() + 1)
                .mapToObj(i -> "[" + i + "] " + filePaths.get(i - 1))
                .collect(Collectors.joining("\n"));


        return citations.toString();
    }
}
