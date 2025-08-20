package org.dirchat.embedding;

import dev.langchain4j.model.huggingface.HuggingFaceEmbeddingModel;
import org.dirchat.model.Chunk;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
//import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;

import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import org.dirchat.util.LogMessages;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.dirchat.document.TextChunker;
import org.dirchat.util.ConfigManager;

public class EmbeddingService {

    public static final String BASE_URL = ConfigManager.getOllamaBaseUrl();
    public static final int MAX_CHUNKS = 10;
    public static final String START_PROMPT = "You analyse questions and search for results in the context provided.";
    private static final String PROMPT_FILE = "prompt.txt";
    private static final ExecutorService SERVICE_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()));
    private static String startPromptCache = null;
    private final String baseUrl;
    private EmbeddingModel embeddingModel;
    public final TextChunker textChunker;
    private final String embeddingMethod;

    private ResourceBundle messages = ResourceBundle.getBundle("messages");


    // Allows the UI to register a callback for message bar updates.
    // This decouples backend logic from the UI, so EmbeddingService can notify the user without knowing about UI classes.
    private static java.util.function.Consumer<String> messageBarSetter;
    public static void setMessageBarSetter(java.util.function.Consumer<String> setter) {
        messageBarSetter = setter;
    }
    // Use this to send a message to the UI message bar (if registered)
    public void setMessageBar(String msg) {
        if (messageBarSetter != null) messageBarSetter.accept(msg);
    }


    // Provides a tokenizer and the embedding model based on the embedding method
    public EmbeddingService() {
        this(BASE_URL, ConfigManager.getIndexingMethod());
    }
    private EmbeddingService(String baseUrl, String embeddingMethod) {
        this.baseUrl = baseUrl;
        this.embeddingMethod = embeddingMethod;
        this.embeddingModel = getMethodEmbeddingModel(this.embeddingMethod); //TODO: messages.properties

        // Initialize TextChunker with paragraphsPerChunk and overlap from config
        int paragraphsPerChunk = ConfigManager.getParagraphsPerChunk();
        int overlap = ConfigManager.getOverlap();
        this.textChunker = new TextChunker(paragraphsPerChunk, overlap);
    }

    // Optionally, provide a getter for the start prompt
    // TODO Move to chatService
    public static String getStartPrompt() {
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

    /**
     * Generates embeddings for a list of chunks. No document/file handling.
     * @param chunks List of Chunk objects to embed
     * @return Map of Chunk to List<Double> embeddings
     */
    public Map<Chunk, List<Double>> embedChunks(List<Chunk> chunks) {
        Map<Chunk, List<Double>> embeddings = new HashMap<>();
        int total = Math.min(chunks.size(), MAX_CHUNKS);
        for (int i = 0; i < total; i++) {
            Chunk chunk = chunks.get(i);
            String content = chunk.getContent();
            if (content == null || content.isBlank()) {
                LogMessages.logWarning("Skipping chunk with null or blank content: index=" + chunk.getIndex() + ", docId=" + chunk.getDocumentId());
                continue;
            }
            if ( !(chunk.getChunkType().equalsIgnoreCase("metadata") )) {
                float[] vector = embeddingModel.embed(content).content().vector();
                List<Double> vectorList = floatArrayToDoubleList(vector);
                embeddings.put(chunk, vectorList);
            }

            // Progress bar update per chunk
            double percent = (((double) (i + 1)) / total) * 100.0;
            String bar = "[" + "=".repeat((int) (percent / 2)) + " ".repeat(50 - (int) (percent / 2)) + "]";
            String percentString = String.format("%.2f", percent);
            setMessageBar(bar + " " + percentString + "% for chunk " + (i + 1) + "/" + total);
            System.out.print("\r" + bar + " " + percentString + "% for chunk " + (i + 1) + "/" + total);
            if (i + 1 == total) System.out.println();
        }
        return embeddings;
    }

    public Map<Chunk, List<Double>> embedChunks(List<Chunk> chunks, java.util.function.BiConsumer<String, Double> progressCallback) {
        Map<Chunk, List<Double>> embeddings = new HashMap<>();
        int total = Math.min(chunks.size(), MAX_CHUNKS);
        for (int i = 0; i < total; i++) {
            Chunk chunk = chunks.get(i);
            String content = chunk.getContent();
            if (content == null || content.isBlank()) {
                LogMessages.logWarning("Skipping chunk with null or blank content: index=" + chunk.getIndex() + ", docId=" + chunk.getDocumentId());
                continue;
            }
            if (!(chunk.getChunkType().equalsIgnoreCase("metadata"))) {
                float[] vector = embeddingModel.embed(content).content().vector();
                List<Double> vectorList = floatArrayToDoubleList(vector);
                embeddings.put(chunk, vectorList);
            }
            double percent = ((double) (i + 1)) / total;
            String msg = "Embedding chunk " + (i + 1) + "/" + total;
            if (progressCallback != null) progressCallback.accept(msg, percent);
        }
        return embeddings;
    }

    public List<Double> embedChunk(Chunk chunk, String docFolderPath, String docFileName) {
        float[] vector = embeddingModel.embed(chunk.getContent()).content().vector();
        return floatArrayToDoubleList(vector);
    }

    private List<Double> floatArrayToDoubleList(float[] array) {
        return java.util.stream.IntStream.range(0, array.length)
                .mapToDouble(i -> array[i])
                .boxed()
                .collect(Collectors.toList());
    }


    public String getEmbeddingMethod() {
        return embeddingMethod;
    }

    /**
     * Returns a new EmbeddingModel instance based on the current embeddingMethod.
     */
    public EmbeddingModel getMethodEmbeddingModel(String embeddingMethod) {
        EmbeddingModel methodEmbeddingModel = null;
        if (embeddingMethod.equalsIgnoreCase("default")) {
            // Not implemented: return null or throw exception
            LogMessages.log("Not implemented yet");
            setMessageBar("Embedding method 'default' is not implemented yet. Please select 'sbert' or 'ollama'."); //TODO: messages.properties
            return null;
        } else if (embeddingMethod.equalsIgnoreCase("sbert")) {
            methodEmbeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(EmbeddingService.BASE_URL)
                    .modelName("all-minilm:33m")
                    .build();
            //TODO: messages.properties
        } else if (embeddingMethod.equalsIgnoreCase("langchainALLMINI")) {
            EmbeddingModel eM = null; //new AllMiniLmL6V2EmbeddingModel();
            methodEmbeddingModel = eM;
            //TODO: messages.properties
        } else if (embeddingMethod.equalsIgnoreCase("tinyllama")) {
            methodEmbeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(EmbeddingService.BASE_URL)
                    .modelName("tinyllama")
                    .build();
            //TODO: messages.properties
        }
        return methodEmbeddingModel;
    }
}