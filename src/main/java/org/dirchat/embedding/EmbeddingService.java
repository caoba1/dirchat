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
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;
import org.dirchat.document.TextChunker;
import org.dirchat.util.ConfigManager;

public class EmbeddingService {

    public static final String BASE_URL = "http://192.168.10.60:11434";
    public static final int MAX_CHUNKS = 500;
    public static final String START_PROMPT = "You analyse questions and search for results in the context provided.";
    private static final String PROMPT_FILE = "prompt.txt";
    private static final ExecutorService SERVICE_EXECUTOR = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()));
    private static String startPromptCache = null;
    private final String baseUrl;
    private EmbeddingModel embeddingModel;
    private final TextChunker textChunker;
    private final String embeddingMethod;

    private ResourceBundle messages = ResourceBundle.getBundle("messages");

    private static final Logger logger = Logger.getLogger(EmbeddingService.class.getName());
    static {
        try {
            FileHandler fh = new FileHandler("dirchat.log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setUseParentHandlers(true);
        } catch (Exception e) {
            System.err.println("Failed to set up logger: " + e.getMessage());
        }
    }

    // Allows the UI to register a callback for message bar updates.
    // This decouples backend logic from the UI, so EmbeddingService can notify the user without knowing about UI classes.
    private static java.util.function.Consumer<String> messageBarSetter;
    public static void setMessageBarSetter(java.util.function.Consumer<String> setter) {
        messageBarSetter = setter;
    }
    // Use this to send a message to the UI message bar (if registered)
    private void setMessageBar(String msg) {
        if (messageBarSetter != null) messageBarSetter.accept(msg);
    }

    public EmbeddingService() {
        this(BASE_URL, ConfigManager.getIndexingMethod());
    }

    public EmbeddingService(String baseUrl) {
        this(baseUrl, ConfigManager.getIndexingMethod());
    }

    // Provides a tokenizer and the embedding model based on the embedding method
    public EmbeddingService(String baseUrl, String embeddingMethod) {
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
            logger.warning("Failed to create vectors directory: " + e.getMessage());
            throw new RuntimeException("Failed to create vectors directory", e);
        }
        // File for this document's embeddings
        File indexFile = vectorsDir.resolve(docFileName + ".index").toFile();
        VectorStore vectorStore = new VectorStore(docFolderPath); // Ensure VectorStore is instantiated
        Map<Chunk, List<Double>> embeddings;
        if (vectorStore.indexExists(docFileName)) {
            try {
                String dbType = org.dirchat.util.ConfigManager.getDatabaseType();
                if (dbType.equalsIgnoreCase("file")) {
                    embeddings = vectorStore.load(docFileName);
                } else {
                    embeddings = vectorStore.loadFromSqlite();
                }
                System.out.println(MessageFormat.format(messages.getString("log.loadedEmbeddings"), docFileName));
                String loadedMsg = MessageFormat.format(LogMessages.LOADED_EMBEDDINGS, docFileName);
                logger.info(loadedMsg);
                setMessageBar(loadedMsg);
                return embeddings;
            } catch (Exception e) {
                logger.warning("Failed to load index for " + docFileName + ", reason: " + e.getMessage());
                throw new RuntimeException(MessageFormat.format(messages.getString("log.failedToLoadIndex"), docFileName, e.getMessage()), e);
            }
        } else {
            embeddings = new HashMap<>();
            int total = chunks.size();
            for (int i = 0; i < total; i++) {
                Chunk chunk = chunks.get(i);
                float[] vector = embeddingModel.embed(chunk.getContent()).content().vector();
                List<Double> vectorList = floatArrayToDoubleList(vector);
                embeddings.put(chunk, vectorList);
                // Progress bar (console only)
                double progress = ((((double) i + 1) / (double) total) * 50.0);
                String bar = "[" + "=".repeat((int) progress) + " ".repeat(50 - (int) progress) + "]";
                double percent = ((((double) i + 1) / (double) total) * 100.0);
                String percentString = String.format("%.4f", percent);
                String logMsg = bar + " " + percentString + "% for " + chunk.getSourceFile() + " [chunk " + (i+1) + "/" + total + "]";
                System.out.print("\r" + logMsg);
            }
            System.out.println();
            try {
                String dbType = org.dirchat.util.ConfigManager.getDatabaseType();
                if (dbType.equalsIgnoreCase("file")) {
                    vectorStore.save(docFileName, embeddings);
                } else {
                    vectorStore.saveToSqlite(embeddings);
                }
                System.out.println(MessageFormat.format(messages.getString("log.generatedIndex"), docFileName));
                String generatedMsg = MessageFormat.format(LogMessages.GENERATED_INDEX, docFileName);
                logger.info(generatedMsg);
                setMessageBar(generatedMsg);
            } catch (Exception e) {
                logger.warning("Failed to save index for " + docFileName + ", reason: " + e.getMessage());
                throw new RuntimeException(MessageFormat.format(messages.getString("log.failedToSaveIndex"), docFileName, e.getMessage()), e);
            }
            return embeddings;
        }
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

    // Centralized method to index all documents in a folder
    // Loads, chunks, embeds, and saves vectors for each document
    // Updates the message bar and logs progress
    public void createIndex(String folderPath) {
        try {

            setMessageBar(MessageFormat.format(messages.getString("status.startIndexing"), folderPath));
            logger.info(MessageFormat.format(messages.getString("status.startIndexing"), folderPath));
            // Load all documents in the folder
            org.dirchat.document.DocumentLoader loader = new org.dirchat.document.DocumentLoader();
            List<org.dirchat.document.ParsedDocument> documents = loader.loadDocumentsFromFolder(folderPath);
            int totalDocs = documents.size();
            int currentDoc = 0;
            for (org.dirchat.document.ParsedDocument doc : documents) {
                currentDoc++;
                setMessageBar("Indexing document [" + currentDoc + "/" + totalDocs + "]: " + doc.getFileName());
                logger.info("Indexing document: " + doc.getFileName());
                List<org.dirchat.model.Chunk> docChunks = textChunker.chunkText(doc.getContent(), doc.getFileName());
                // Embed and save
                this.embedChunks(docChunks, folderPath, doc.getFileName());
            }
            setMessageBar("Indexing complete for folder: " + folderPath);
            logger.info("Indexing complete for folder: " + folderPath);
        } catch (Exception e) {
            setMessageBar("Indexing failed: " + e.getMessage());
            logger.warning("Indexing failed: " + e.getMessage());
        }
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
            logger.info("Not implemented yet");
            setMessageBar("Embedding method 'default' is not implemented yet. Please select 'sbert' or 'ollama'."); //TODO: messages.properties
            return null;
        } else if (embeddingMethod.equalsIgnoreCase("sbert")) {
            methodEmbeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(EmbeddingService.BASE_URL)
                    .modelName("all-minilm:33m")
                    .build();
            //TODO: messages.properties
        } else if (embeddingMethod.equalsIgnoreCase("langchainALLMINI")) {
            methodEmbeddingModel = new AllMiniLmL6V2EmbeddingModel();
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