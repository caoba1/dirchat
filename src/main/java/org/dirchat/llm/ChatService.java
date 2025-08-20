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
import java.sql.SQLException;
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

        String indexingMethod = "tinyllama";
        try {
            String method = vectorStore.getIndexingMethod();
            if (method != null && !method.isEmpty()) {
                indexingMethod = method;
            }
        } catch (Exception e) {
            LogMessages.logger.warning("Could not retrieve indexing method, defaulting to tinyllama: " + e.getMessage());
        }

        if (indexingMethod.equalsIgnoreCase("default")) {
            /* TODO
            this.embeddingModel = dev.langchain4j.model.embedding.SentenceTransformerEmbeddingModel.builder()
                .modelName("sentence-transformers/all-MiniLM-L6-v2")
                .build();

             */
            LogMessages.logger.info("Not implemented");
        } else if (indexingMethod.equalsIgnoreCase("sbert")) {
            this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(EmbeddingService.BASE_URL)
                .modelName("all-minilm:33m") // or other SBERT model
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
                    .modelName("tinyllama") // or mistral, llama2, gemma, etc.
                    .build();
    }

    public static void setMessageBarSetter(java.util.function.Consumer<String> setter) {
        messageBarSetter = setter;
    }

    private void setMessageBar(String msg) {
        if (messageBarSetter != null) messageBarSetter.accept(msg);
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
            LogMessages.logger.warning("Failed to search vector store (I/O): " + e.getMessage()); //TODO: messages.properties
            throw new RuntimeException(e);
        } catch (SQLException e) {
            LogMessages.logger.warning("Failed to search vector store (SQL): " + e.getMessage()); //TODO: messages.properties
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

            String prompt = "Answer the following question based on the context below.\n\n"
                    + "Context:\n" + context + "\n\n"
                    + "Documents Metadata:\n" + documentsMetadata + "\n\n"
                    + "Question:\n" + question;
            return languageModel.generate(prompt).content();
        } catch (Exception ex) {
            if (messageBarSetter != null) messageBarSetter.accept("Error: Unable to reach the language server. Please check your server connection.");
            LogMessages.logger.warning("Failed to answer question: '" + question + "', reason: " + ex.getMessage());
            return "[Error: Unable to reach the language server. Please check your server connection.]";
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
            LogMessages.logger.warning("Failed to search vector store: " + e.getMessage());
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
}