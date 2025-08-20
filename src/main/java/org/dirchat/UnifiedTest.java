package org.dirchat;

import org.dirchat.document.DocumentLoader;
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

        System.out.println("Starting unified test pipeline...");

        // Step 1: Load documents
        final String folderPath = "C:/temp/TestDocs/"; // Update this path
        DocumentLoader loader = new DocumentLoader();
        List<DocumentLoader.LoadedDocument> documents = loader.loadDocumentsFromFolder(folderPath);
        System.out.println("✅ Loaded " + documents.size() + " documents.");

        // Step 2: Chunk documents
        TextChunker chunker = new TextChunker(6); // 6 paragraphs per chunk
        List<List<Chunk>> allDocChunks = new ArrayList<>();
        int documentCount = 0;
        for (DocumentLoader.LoadedDocument doc : documents) {
            documentCount++;
            List<Chunk> docChunks = chunker.chunkText(doc.content, doc.fileName, doc.documentId);
            System.out.println("✅ Created " + docChunks.size() + " chunks for document " + documentCount + " of " + documents.size() + " :" + doc.fileName);
            allDocChunks.add(docChunks);
        }

        // Step 3: Embed chunks
        EmbeddingService embeddingService = new EmbeddingService();
        System.out.println("Embedding method: " + embeddingService.getEmbeddingMethod());

        Map<Chunk, List<Double>> embeddings = new java.util.HashMap<>();
        documentCount = 0;
        for (List<Chunk> docChunks: allDocChunks) {
            documentCount++;
            Map<Chunk, List<Double>> docEmbeddings = embeddingService.embedChunks(docChunks);
            embeddings.putAll(docEmbeddings);
            System.out.println("✅ Embedded " + docEmbeddings.size() + " chunks for document: " + documents.get(documentCount - 1).fileName);
        }

        // Step 4: Store embeddings and manage folder via VectorStore
        VectorStore store = new VectorStore(folderPath);
        try {
            store.initSqliteDb();
            store.setIndexingMethod("sbert"); // Ensure embedding method is set in settings table
            for (int i = 0; i < documents.size(); i++) {
                DocumentLoader.LoadedDocument doc = documents.get(i);
                String docFileName = doc.fileName;
                List<Chunk> docChunks = allDocChunks.get(i);
                Map<Chunk, List<Double>> docEmbeddings = new java.util.HashMap<>();
                for (Chunk chunk : docChunks) {
                    if (embeddings.containsKey(chunk)) {
                        docEmbeddings.put(chunk, embeddings.get(chunk));
                    }
                }
                // Always use SQLite
                store.saveDocumentEmbeddingsToSqlite(docFileName, docEmbeddings);
                System.out.println("✅ Stored embeddings for " + docFileName + " in SQLite vector store.");
                Map<Chunk, List<Double>> loaded = store.loadDocumentEmbeddingsFromSqlite(docFileName);
                System.out.println("✅ Loaded " + loaded.size() + " embeddings from SQLite for " + docFileName);
            }
            System.out.println("Embedding method in SQLite: " + store.getSqliteEmbeddingMethod());
        } catch (Exception e) {
            System.err.println("❌ Error saving embeddings: " + e.getMessage());
            e.printStackTrace();
        }

        // Step 5: Ask a question
        if (store != null) {
            ChatService chatService = new ChatService(store, documents, chunker, null);
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
