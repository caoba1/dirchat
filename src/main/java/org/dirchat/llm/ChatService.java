package org.dirchat.llm;

import org.dirchat.document.ParsedDocument;
import org.dirchat.document.TextChunker;
import org.dirchat.embedding.EmbeddingService; //todo: just use vector store directly
import org.dirchat.embedding.VectorStore;
import org.dirchat.embedding.VectorStore.ScoredChunk;
import org.dirchat.model.Chunk;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.ollama.OllamaLanguageModel;
import org.dirchat.util.DocumentSimilarity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.stream.Collectors;

public class ChatService {

    private final EmbeddingModel embeddingModel;
    private final LanguageModel languageModel;
    private final VectorStore vectorStore;
    private final List<ParsedDocument> documents;
    private final TextChunker textChunker;

    private static java.util.function.Consumer<String> messageBarSetter;

    public static final int DOCUMENT_RANK = 5;
    public static final int MAX_SEARCH_RESULTS = 5;

    public ChatService(VectorStore vectorStore, List<ParsedDocument> documents, TextChunker textChunker) {
        this.vectorStore = vectorStore;
        this.documents = documents;
        this.textChunker = textChunker;

        this.embeddingModel = OllamaEmbeddingModel.builder()
                .baseUrl(EmbeddingService.BASE_URL)
                .modelName("tinyllama")
                .build();

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
        // Get all scored chunks
        List<ScoredChunk> allScoredChunks = vectorStore.searchWithScores(queryVector, 1000);
        // Separate metadata and content chunks
        List<ScoredChunk> metadataChunks = new java.util.ArrayList<>();
        List<ScoredChunk> contentChunks = new java.util.ArrayList<>();
        for (ScoredChunk sc : allScoredChunks) {
            if ("metadata".equals(sc.getChunk().getChunkType())) {
                metadataChunks.add(sc);
            } else {
                contentChunks.add(sc);
            }
        }
        // Prioritize content chunks, but always include the top metadata chunk if it's highly relevant
        List<ScoredChunk> topChunks = new java.util.ArrayList<>();
        // Add top content chunks first
        topChunks.addAll(contentChunks.stream().limit(MAX_SEARCH_RESULTS).collect(java.util.stream.Collectors.toList()));
        // If a metadata chunk is in the top 3 overall, add it to the front
        if (!metadataChunks.isEmpty() && (allScoredChunks.indexOf(metadataChunks.get(0)) < 3)) {
            topChunks.add(0, metadataChunks.get(0));
        }
        // Limit to MAX_SEARCH_RESULTS
        if (topChunks.size() > MAX_SEARCH_RESULTS) {
            topChunks = topChunks.subList(0, MAX_SEARCH_RESULTS);
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
            String context = topChunks.stream()
                    .map(sc -> {
                        Chunk chunk = sc.getChunk();
                        String typeLabel = "metadata".equals(chunk.getChunkType()) ? "[METADATA]\n" : "";
                        return typeLabel + reconstructChunkContent(chunk);
                    })
                    .collect(Collectors.joining("\n\n"));
            String prompt = "Answer the following question based on the context below.\n\n"
                    + "Context:\n" + context + "\n\n"
                    + "Question:\n" + question;

            return languageModel.generate(prompt).content();
        } catch (Exception ex) {
            if (messageBarSetter != null) messageBarSetter.accept("Error: Unable to reach the language server. Please check your server connection.");
            java.util.logging.Logger.getLogger(ChatService.class.getName()).warning("Failed to answer question: '" + question + "', reason: " + ex.getMessage());
            return "[Error: Unable to reach the language server. Please check your server connection.]";
        }
    }

    private String reconstructChunkContent(Chunk chunk) {
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