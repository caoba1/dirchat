package org.dirchat;

import org.dirchat.document.DocumentLoader;
import org.dirchat.document.ParsedDocument;
import org.dirchat.document.TextChunker;
import org.dirchat.embedding.EmbeddingService;
import org.dirchat.embedding.VectorStore;
import org.dirchat.llm.ChatService;
import org.dirchat.model.Chunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UnifiedTest {
    public static void main(String[] args) {

        System.out.println("Starting 4 tests");

        // Step 1: Load documents
        final String folderPath = "C:/temp/TestDocs/"; // Update this path
        DocumentLoader loader = new DocumentLoader();
        List<ParsedDocument> documents = loader.loadDocumentsFromFolder(folderPath);
        System.out.println("✅ Loaded " + documents.size() + " documents.");

        // Step 2: Chunk documents
        TextChunker chunker = new TextChunker(6); // 3 paragraphs per chunk
        List<Chunk> allChunks = new ArrayList<>();
        for (ParsedDocument doc : documents) {
            allChunks.addAll(chunker.chunkText(doc.getContent(), doc.getFileName()));
        }
        System.out.println("✅ Created " + allChunks.size() + " chunks.");

        // Step 3: Embed chunks
        EmbeddingService embeddingService = new EmbeddingService();
        System.out.println("Embedding method: " + embeddingService.getEmbeddingMethod());
        Map<Chunk, List<Double>> embeddings = new java.util.HashMap<>();
        for (ParsedDocument doc : documents) {
            List<Chunk> docChunks = chunker.chunkText(doc.getContent(), doc.getFileName());
            Map<Chunk, List<Double>> docEmbeddings = embeddingService.embedChunks(docChunks, folderPath, doc.getFileName());
            embeddings.putAll(docEmbeddings);
        }
        System.out.println("✅ Embedded " + embeddings.size() + " chunks.");

        // Step 4: Store embeddings
        String dbType = org.dirchat.util.ConfigManager.getDatabaseType();
        VectorStore store = null;
        try {
            if (dbType.equalsIgnoreCase("file")) {
                store = new VectorStore(folderPath);
                store.addAll(embeddings);
                store.save(embeddings, folderPath); // Save to file
                System.out.println("✅ Stored embeddings in file-based vector store.");
                Map<Chunk, List<Double>> loaded = store.load(folderPath);
                System.out.println("✅ Loaded " + loaded.size() + " embeddings from file.");
            } else {
                store = new VectorStore(folderPath);
                store.initSqliteDb();
                store.addAll(embeddings);
                store.saveToSqlite(embeddings); // Save to SQLite
                System.out.println("✅ Stored embeddings in SQLite vector store.");
                Map<Chunk, List<Double>> loaded = store.loadFromSqlite();
                System.out.println("✅ Loaded " + loaded.size() + " embeddings from SQLite.");
                System.out.println("Embedding method in SQLite: " + store.getSqliteEmbeddingMethod());
            }
        } catch (Exception e) {
            System.err.println("❌ Error saving/loading embeddings: " + e.getMessage());
            e.printStackTrace();
        }

        // Step 5: Ask a question
        if (store != null) {
            ChatService chatService = new ChatService(store, documents, chunker);
            String question = "How many chapters are about java in the documents?";
            String answer = chatService.answerQuestion(question, 5);
            System.out.println("🧠 Question: " + question);
            System.out.println("💬 Answer: " + answer);
        } else {
            System.err.println("❌ VectorStore was not initialized. Skipping question/answer test.");
        }
        System.out.println("Finished all tests successfully!");
    }
}
