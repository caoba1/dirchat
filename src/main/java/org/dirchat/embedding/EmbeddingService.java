package org.dirchat.embedding;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
//import dev.langchain4j.model.embedding.DimensionAwareEmbeddingModel;

import org.dirchat.util.LogMessages;

import java.util.List;
import java.util.ResourceBundle;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.dirchat.util.ConfigManager;
import org.dirchat.util.Dirchat;

public class EmbeddingService {

    public static final String BASE_URL = ConfigManager.getOllamaBaseUrl();
    public static final int MAX_CHUNKS = 10;
    // TODO
    private static final ExecutorService SERVICE_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()));
    private final String baseUrl;
    private EmbeddingModel embeddingModel;
    private final String embeddingMethod;

    private ResourceBundle messages = ResourceBundle.getBundle("messages");

    // Allows the UI to register a callback for message bar updates.
    private static java.util.function.Consumer<String> messageBarSetter;
    public static void setMessageBarSetter(java.util.function.Consumer<String> setter) {
        messageBarSetter = setter;
    }

    // Use this to send a message to the UI message bar (if registered)
    public void setMessageBar(String msg) {
        if (messageBarSetter != null)
            messageBarSetter.accept(msg);
    }

    // Provides a tokenizer and the embedding model based on the embedding method
    public EmbeddingService() {
        this(BASE_URL, ConfigManager.getIndexingMethod());
    }
    private EmbeddingService(String baseUrl, String embeddingMethod) {
        this.baseUrl = baseUrl;
        this.embeddingMethod = embeddingMethod;
        this.embeddingModel = getMethodEmbeddingModel(this.embeddingMethod); //TODO: messages.properties
    }

    // Generates embeddings for a single text chunk
    public List<Double> embedText(String text) {
        float[] vector = embeddingModel.embed(text).content().vector();
        return Dirchat.floatArrayToDoubleList(vector);
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