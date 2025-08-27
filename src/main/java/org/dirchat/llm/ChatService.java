package org.dirchat.llm;

import org.dirchat.controller.UIController;
import org.dirchat.document.TextChunker;
import org.dirchat.embedding.EmbeddingService; //todo: just use vector store directly
import org.dirchat.embedding.VectorStore;
import org.dirchat.embedding.VectorStore.ScoredChunk;
import org.dirchat.model.Chunk;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.ollama.OllamaLanguageModel;
import org.dirchat.util.LogMessages;
import org.dirchat.document.DocumentLoader.LoadedDocument;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ChatService {

    private EmbeddingModel embeddingModel;
    private final LanguageModel languageModel;
    private final VectorStore vectorStore;
    private final List<LoadedDocument> documents;
    private final TextChunker textChunker;
    private final UIController uiController;

    private static java.util.function.Consumer<String> messageBarSetter;

    public static final int DOCUMENT_RANK = 5;
    public static final int MAX_SEARCH_RESULTS = 5;

    public ChatService(VectorStore vectorStore, List<LoadedDocument> documents, TextChunker textChunker, UIController uiController) {
        this.vectorStore = vectorStore;
        this.documents = documents;
        this.textChunker = textChunker;
        this.uiController = uiController;

        String indexingMethod = "tinyllama"; // For the embedding model, default to tinyllama
        try {
            String method = vectorStore.getIndexingMethod();
            if (method != null && !method.isEmpty()) {
                indexingMethod = method;
            }
        } catch (Exception e) {
            LogMessages.logWarning("Could not retrieve indexing method, defaulting to tinyllama: " + e.getMessage());
        }

        if (indexingMethod.equalsIgnoreCase("default")) {
            /* TODO
            this.embeddingModel = dev.langchain4j.model.embedding.SentenceTransformerEmbeddingModel.builder()
                .modelName("sentence-transformers/all-MiniLM-L6-v2")
                .build();

             */
            LogMessages.log("Not implemented");
        } else if (indexingMethod.equalsIgnoreCase("sbert")) {
            this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(EmbeddingService.BASE_URL)
                .modelName("all-minilm:33m") // or other SBERT model, tinyllama
                .build();
        } else {
            this.embeddingModel = OllamaEmbeddingModel.builder()
                    .baseUrl(EmbeddingService.BASE_URL)
                    .modelName("tinyllama") // or mistral, llama2, gemma, etc.
                    .build();
        }

        // Initialize the language model
        this.languageModel = OllamaLanguageModel.builder()
                    .baseUrl(EmbeddingService.BASE_URL)
                    .modelName("mistral") // or [mistral, tinyllama] llama2, gemma, etc.
                    .timeout(Duration.ofMinutes(15))  // or even longer
                    .build();
    }
    // RAG
    public List<ScoredChunk> getTopDocumentChunks(String question) {
        float[] vector = embeddingModel.embed(question).content().vector();
        List<Double> queryVector = new java.util.ArrayList<>(vector.length);
        for (float v : vector) queryVector.add((double) v);
        int topK = org.dirchat.util.ConfigManager.getTopK();
        List<ScoredChunk> allScoredChunks;
        try {
            allScoredChunks = vectorStore.compoundSearchWithScores(queryVector, topK);
        } catch (IOException e) {
            LogMessages.logWarning("Failed to search vector store (I/O): " + e.getMessage());
            if (messageBarSetter != null) messageBarSetter.accept("Error: " + e.getMessage());
            throw new RuntimeException(e);
        } catch (SQLException e) {
            LogMessages.logWarning("Failed to search vector store (SQL): " + e.getMessage());
            if (messageBarSetter != null) messageBarSetter.accept("Error: " + e.getMessage());
            throw new RuntimeException(e);
        }
        List<ScoredChunk> metadataChunks = new java.util.ArrayList<>();
        List<ScoredChunk> contentChunks = new java.util.ArrayList<>();
        for (ScoredChunk sc : allScoredChunks) {
            if ("metadata".equals(sc.getChunk().getChunkType())) {
                metadataChunks.add(sc);
            } else {
                contentChunks.add(sc);
            }
        }
        List<ScoredChunk> topChunks = new java.util.ArrayList<>();
        topChunks.addAll(contentChunks.stream().limit(topK).collect(java.util.stream.Collectors.toList()));
        if (!metadataChunks.isEmpty() && (allScoredChunks.indexOf(metadataChunks.get(0)) < 3)) {
            topChunks.add(0, metadataChunks.get(0));
        }
        if (topChunks.size() > topK) {
            topChunks = topChunks.subList(0, topK);
        }
        return topChunks;
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

    public List<String> getTopDocumentSummaries(String question) {
        List<ScoredChunk> topChunks = getTopDocumentChunks(question);
        // Group by document and calculate average score
        Map<String, List<ScoredChunk>> docToChunks = new java.util.HashMap<>();
        for (ScoredChunk sc : topChunks) {
            String doc = sc.getChunk().getSourceFile();
            docToChunks.computeIfAbsent(doc, k -> new java.util.ArrayList<>()).add(sc);
        }

        List<String> summaries = new java.util.ArrayList<>();
        for (Map.Entry<String, List<ScoredChunk>> entry : docToChunks.entrySet()) {
            String doc = entry.getKey();
            List<ScoredChunk> chunks = entry.getValue();
            double avgScore = chunks.stream().mapToDouble(ScoredChunk::getScore).average().orElse(0.0);
            summaries.add(doc + " (avg score: " + String.format("%.3f", avgScore) + ")");
        }
        LogMessages.log( "Top document summaries: \n"
                + String.join("\n",summaries));
        return summaries;
    }

    public String answerQuestion(String question, int topK) {
        try {
            List<ScoredChunk> topChunks = getTopDocumentChunks(question);
            // Extract topK unique documents
            List<Integer> docIds = topChunks.stream()
                .map(sc -> sc.getChunk().getDocumentId())
                .distinct()
                .limit(topK)
                .collect(Collectors.toList());
            List<LoadedDocument> topDocuments = docIds.stream()
                .map(docId -> documents.stream().filter(d -> d.documentId == docId).findFirst().orElse(null))
                .filter(d -> d != null)
                .collect(Collectors.toList());
            // Update UIController.indexStatusList
            if (uiController != null) {
                uiController.setIndexStatusListItems(topDocuments);
            }
            // Build context from top chunks
            String context = topChunks.stream()
                .map(sc -> {
                    Chunk chunk = sc.getChunk();
                    String typeLabel = "metadata".equals(chunk.getChunkType()) ? "[METADATA]\n" : "";
                    return typeLabel + reconstructChunkContent(chunk);
                })
                .collect(Collectors.joining("\n\n"));

            // Build Documents Metadata section
            String documentsMetadata = topDocuments.stream()
                .map(doc -> {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Document: ").append(doc.fileName).append("\n");
                    String[] names = doc.metadata.names();
                    for (String name : names) {
                        sb.append("  ").append(name).append(": ").append(doc.metadata.get(name)).append("\n");
                    }
                    return sb.toString();
                })
                .collect(Collectors.joining("\n"));

            int maxContextLength = 216; //2048 //org.dirchat.util.ConfigManager.getMaxContextLength();
            context = context.substring(0, Math.min(context.length(), maxContextLength));
            documentsMetadata = documentsMetadata.substring(0, Math.min(documentsMetadata.length(), maxContextLength));
            question = question.substring(0, Math.min(question.length(), maxContextLength));

            // Now chat and wait for resutls. Using documents that appear on the left. Use a prompt template.
            // Todo: do summaries for getting context.
            // Todo: rearrange document metadata to a summary also

            // The following line does not work. Not even reducing the context length helps.
            // I am changing maxContextLength to 512.  What is the maximum context length for Mistral? For tinyllama?
            String prompt = "Answer the following question based on the context below.\n\n"
                    + "Context:\n" + context + "\n\n"
                    + "Documents Metadata:\n" + documentsMetadata + "\n\n"
                    + "Question:\n" + question;




            dev.langchain4j.model.output.Response<String> response = languageModel.generate(prompt);
            LogMessages.log("Raw response from language model: " + response);
            try {
                return response.content();
            } catch (Exception parseEx) {
                LogMessages.logWarning("Error parsing response content: " + parseEx.getMessage() + ", raw response: " + response);
                if (messageBarSetter != null) messageBarSetter.accept("Error: Malformed response from language server. Please check server logs.");
                return "[Error: Malformed response from language server. Please check server logs.]";
            }
        } catch (Exception ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof java.net.SocketTimeoutException) {
                if (messageBarSetter != null) messageBarSetter.accept("Error: Language server timed out. Please try again later.");
                LogMessages.logWarning("Socket timeout while answering question: '" + question + "', reason: " + cause.getMessage());
                return "[Error: Language server timed out. Please try again later.]";
            } else if (cause instanceof java.net.ConnectException) {
                if (messageBarSetter != null) messageBarSetter.accept("Error: Unable to connect to the language server. Please check your server status.");
                LogMessages.logWarning("Connection error while answering question: '" + question + "', reason: " + cause.getMessage());
                return "[Error: Unable to connect to the language server. Please check your server status.]";
            } else {
                if (messageBarSetter != null) messageBarSetter.accept("Error: Unable to reach the language server. Please check your server connection.");
                LogMessages.logWarning("Failed to answer question: '" + question + "', reason: " + ex.getMessage());
                return "[Error: Unable to reach the language server. Please check your server connection.]";
            }
        }
    }

    private String reconstructChunkContent(Chunk chunk) {
        for (LoadedDocument doc : documents) {
            if (doc.documentId == chunk.getDocumentId()) {
                List<Chunk> chunks = textChunker.chunkText(doc.content, doc.fileName, doc.documentId);
                for (Chunk c : chunks) {
                    if (c.getIndex() == chunk.getIndex()) {
                        return c.getContent();
                    }
                }
            }
        }
        return "";
    }

    /**
     * Retrieve chunk content using document_id, chunk_index, page_number, and cursor_pointer
     * Refactored to use DocumentLoader.LoadedDocument and Tika Metadata.
     */
    public String retrieveChunkContent(int documentId, int chunkIndex, int pageNumber, int cursorPointer) {
        LoadedDocument doc = documents.stream()
            .filter(d -> d.documentId == documentId)
            .findFirst()
            .orElse(null);
        if (doc == null) return null;
        // Use TextChunker or direct string slicing to get chunk content
        return textChunker.getChunkContent(doc, chunkIndex, pageNumber, cursorPointer);
    }

    /**
     * Get top scored chunks using pointer-based access and reconstruct their content.
     */
    public List<ScoredChunk> getTopChunksWithContent(List<Double> queryVector, int topK) {
        List<ScoredChunk> topChunks;
        try {
            topChunks = vectorStore.getChunkEmbeddingsWithScores(queryVector, topK);
        } catch (IOException | SQLException e) {
            LogMessages.logWarning("Failed to search vector store: " + e.getMessage());
            if (messageBarSetter != null) messageBarSetter.accept("Error: " + e.getMessage());
            throw new RuntimeException(e);
        }
        for (ScoredChunk sc : topChunks) {
            Chunk chunk = sc.getChunk();
            LoadedDocument doc = documents.stream()
                .filter(d -> d.documentId == chunk.getDocumentId())
                .findFirst()
                .orElse(null);
            if (doc != null) {
                String content = textChunker.getChunkContent(doc, chunk.getChunkIndex(), chunk.getPageNumber(), chunk.getCursorPointer());
                chunk.setContent(content);
            }
        }
        return topChunks;
    }

    // Apply output mode: summarize, truncate, or none
    public String processAnswer(String answer) {
        String mode = org.dirchat.util.ConfigManager.getOutputMode();
        switch (mode) {
            case "summarize":
                return summarize(answer);
            case "truncate":
                return truncate(answer);
            default:
                return answer;
        }
    }

    // Stub for truncation logic
    private String truncate(String text) {
        // TODO: Implement actual truncation logic
        if (text.length() > 500) {
            return text.substring(0, 500) + "...";
        }
        return text;
    }

    // Stub for summarization logic
    private String summarize(String text) {
        // TODO: Implement actual summarization logic
        return "[Summary] " + text;
    }
}
