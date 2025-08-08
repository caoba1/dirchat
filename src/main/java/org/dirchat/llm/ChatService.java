package org.dirchat.llm;

import org.dirchat.document.ParsedDocument;
import org.dirchat.document.TextChunker;
import org.dirchat.embedding.EmbeddingService; //todo: just use vector store directly
import org.dirchat.embedding.VectorStore;
import org.dirchat.model.Chunk;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.language.LanguageModel;
import dev.langchain4j.model.ollama.OllamaLanguageModel;

import java.util.List;
import java.util.stream.Collectors;

public class ChatService {

    private final EmbeddingModel embeddingModel;
    private final LanguageModel languageModel;
    private final VectorStore vectorStore;
    private final List<ParsedDocument> documents;
    private final TextChunker textChunker;

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

    // RAG
    public String answerQuestion(String question, int topK) {
        float[] vector = embeddingModel.embed(question).content().vector();
        List<Double> queryVector = new java.util.ArrayList<Double>(vector.length);
        for (float v : vector) {
            queryVector.add((double) v);
        }
        List<Chunk> relevantChunks = vectorStore.search(queryVector, topK);

        String context = relevantChunks.stream()
                .map(chunk -> reconstructChunkContent(chunk))
                .collect(Collectors.joining("\n\n"));

        String prompt = "Answer the following question based on the context below.\n\n"
                + "Context:\n" + context + "\n\n"
                + "Question:\n" + question;

        return languageModel.generate(prompt).content();
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