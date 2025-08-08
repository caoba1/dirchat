package org.dirchat.embedding;

import org.dirchat.model.Chunk;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class EmbeddingService {

    public static final String BASE_URL = "http://192.168.10.60:11434";
    public static final int MAX_CHUNKS = 500;
    public static final String START_PROMPT = "You analyse questions and search for results in the context provided.";
    private static final String PROMPT_FILE = "prompt.txt";
    private static final ExecutorService SERVICE_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()));
    private static String startPromptCache = null;
    private final String baseUrl;
    private final EmbeddingModel embeddingModel;

    public EmbeddingService() {
        this(BASE_URL);
    }

    public EmbeddingService(String baseUrl) {
        this.baseUrl = baseUrl;
        this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(baseUrl)
                .modelName("tinyllama") // or mistral, llama2, gemma, etc.
                .build();
    }

    // Optionally, provide a getter for the start prompt
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

    public Map<Chunk, List<Double>> embedChunks(List<Chunk> chunks, String docFolderPath, String docFileName) {
        // Limit chunks to MAX_CHUNKS
        if (chunks.size() > MAX_CHUNKS) {
            chunks = chunks.subList(0, MAX_CHUNKS);
        }
        // Create vectorstore/vectors/ directory if it doesn't exist
        Path vectorsDir = Paths.get(docFolderPath, "vectorstore", "vectors");
        try {
            Files.createDirectories(vectorsDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create vectors directory", e);
        }
        // File for this document's embeddings
        File indexFile = vectorsDir.resolve(docFileName + ".index").toFile();
        VectorStore vectorStore = new VectorStore();
        Map<Chunk, List<Double>> embeddings;
        if (indexFile.exists()) {
            try {
                embeddings = vectorStore.load(indexFile.getAbsolutePath());
                System.out.println("Loaded embeddings from " + indexFile.getName());
                return embeddings;
            } catch (IOException e) {
                throw new RuntimeException("Failed to load " + indexFile.getName(), e);
            }
        } else {
            embeddings = new HashMap<>();
            int total = chunks.size();
            for (int i = 0; i < total; i++) {
                Chunk chunk = chunks.get(i);
                // Todo: Use parallel processing for better performance
                float[] vector = embeddingModel.embed(chunk.getContent()).content().vector();
                List<Double> vectorList = floatArrayToDoubleList(vector);
                embeddings.put(chunk, vectorList);

                // Progress bar
                double progress = ((((double) i + 1) / (double) total) * 50.0); // 50 chars wide
                String bar = "[" + "=".repeat((int) progress) + " ".repeat(50 - (int) progress) + "]";
                double percent = ((((double) i + 1) / (double) total) * 100.0);
                String percentString = String.format("%.4f", percent);
                System.out.print("\r" + bar + " " + percentString + "%");
            }
            System.out.println(); // Move to next line after completion
            try {
                vectorStore.save(embeddings, indexFile.getAbsolutePath());
                System.out.println("Saved embeddings to " + indexFile.getName());
            } catch (IOException e) {
                throw new RuntimeException("Failed to save " + indexFile.getName(), e);
            }
            return embeddings;
        }
    }

    private List<Double> floatArrayToDoubleList(float[] array) {
        return java.util.stream.IntStream.range(0, array.length)
                .mapToDouble(i -> array[i])
                .boxed()
                .collect(Collectors.toList());
    }
}