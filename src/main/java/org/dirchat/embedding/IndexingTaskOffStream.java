package org.dirchat.embedding;

import javafx.application.Platform;
import javafx.concurrent.Task;
import org.dirchat.controller.ProgressDialog;
import org.dirchat.document.Chunk;
import org.dirchat.document.DocumentLoader;
import org.dirchat.document.StreamSplitter;
import org.dirchat.util.Dirchat;
import org.dirchat.util.LogMessages;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicInteger;

public class IndexingTaskOffStream extends Task<Void> {
 /*
    private final File folder;
    private final VectorStore vectorStore;
    private final ProgressDialog progressDialog;
    private List<DocumentLoader.LoadedDocument> loadedDocuments;
    private final ResourceBundle messages = ResourceBundle.getBundle("messages");
    public IndexingTask( VectorStore vectorStore, ProgressDialog progressDialog) {
        this.folder = vectorStore.getFolder().toFile();
        this.vectorStore = vectorStore;
        this.progressDialog = progressDialog;
    }

    @Override
    protected Void call() {
        // Move your indexing logic here (from handleLoadFolder)
        // Update progressDialog and loadedDocuments as needed
        try {
            List<DocumentLoader.LoadedDocument> docs = new java.util.ArrayList<>();
            LogMessages.log("[Indexing] Task started");
            if (vectorStore.indexExists()) {
                LogMessages.log("[Indexing] Index exists, loading from DB");
                docs = vectorStore.loadIndexing();
                updateMessage(messages.getString("status.indexLoaded"));
            } else {
                LogMessages.log("[Indexing] Index does not exist or is not complete. Processing files");
                File[] files = folder.listFiles();
                final int total = files != null ? files.length : 0;
                AtomicInteger count = new AtomicInteger(0);
                EmbeddingService embeddingService = new EmbeddingService();
                for (File file : files) {
                    if (progressDialog.isCancelled() || isCancelled()) {
                        LogMessages.log("[Indexing] Cancelled by user (file loop)");
                        updateMessage(messages.getString("status.folderLoadCancelled"));
                        break;
                    }

                    if (file.isFile() && Dirchat.isSupported(file.toPath())) {
                        count.incrementAndGet();
                        LogMessages.log("[Indexing] Processing file: " + file.getName());
                        updateMessage(messages.getString("progress.loadingFile").replace("{0}", file.getName()));
                        Platform.runLater(() -> {
                            progressDialog.setFileName(file.getName());
                            progressDialog.setProgress((double) count.get() / total);
                        });

                        // The documentation says one should add metadata, the stream, and a handler.
                        // I am using then a custom handler to fetch the content in document.StreamSplitter
                        org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
                        org.apache.tika.parser.AutoDetectParser parser = new org.apache.tika.parser.AutoDetectParser();
                        // TODO: Add OCR, optical character recognition, for images and scanned PDFs
                        org.apache.tika.parser.ParseContext context = new org.apache.tika.parser.ParseContext();
                        org.xml.sax.ContentHandler handler = new StreamSplitter();

                        try ( java.io.FileInputStream stream = new java.io.FileInputStream(file)) {
                            parser.parse(stream, handler, metadata);
                        } catch (java.io.IOException e) {
                            // Handle I/O errors (e.g., file not found)
                            e.printStackTrace();
                        } catch (org.apache.tika.exception.TikaException e) {
                            // Handle Tika parsing errors
                            e.printStackTrace();
                        } catch (org.xml.sax.SAXException e) {
                            // Handle SAX/ContentHandler errors
                            e.printStackTrace();
                        }

                        int documentId = file.getAbsolutePath().hashCode();
                        DocumentLoader.LoadedDocument doc = new DocumentLoader.LoadedDocument(documentId, file.getName(), content, file.getAbsolutePath());
                        doc.setMetadata(metadata);

                        // Chunk and embed
                        List<Chunk> docChunks = embeddingService.textChunker.chunkText(content, file.getName(), documentId);

                        Map<Chunk, List<Double>> docEmbeddings = new java.util.HashMap<>();
                        final int chunkCount = docChunks.size();
                        AtomicInteger chunkIdx = new AtomicInteger(0);
                        for (Chunk chunk : docChunks) {
                            if (progressDialog.isCancelled() || isCancelled()) {
                                LogMessages.log("[Indexing] Cancelled by user (chunk loop)");
                                updateMessage(messages.getString("status.folderLoadCancelled"));
                                break;
                            }
                            LogMessages.log("[Indexing] Embedding chunk " + chunkIdx.get() + "/" + chunkCount + " for file: " + file.getName());

                            // Cal6l to generate embedding
                            /// //
                            List<Double> embedding = embeddingService.embedChunk(chunk);

                            // Call to
                            docEmbeddings.put(chunk, embedding);
                            chunkIdx.incrementAndGet();
                            Platform.runLater(() -> progressDialog.setFileProgress((double) chunkIdx.get() / chunkCount));
                            if (progressDialog.isCancelled() || isCancelled()) {
                                LogMessages.log("[Indexing] Cancelled by user (after embedding chunk)");
                                updateMessage(messages.getString("status.folderLoadCancelled"));
                                break;
                            }
                        }
                        // Save embeddings to DB
                        vectorStore.saveDocumentEmbeddingsToSqlite(doc, docEmbeddings);
                        // Save document metadata (including TOC)
                        vectorStore.extractAndSaveDocumentMetadata(doc);
                        docs.add(doc);
                        Platform.runLater(() -> progressDialog.setFileProgress(0)); // Reset chunk progress bar
                    }
                }
                updateMessage(messages.getString("status.indexLoaded"));
            }
            loadedDocuments = docs;
            LogMessages.log("[Indexing] Task completed");
        } catch (Exception e) {
            if (e instanceof java.net.ConnectException ||
                    e.getCause() instanceof java.net.ConnectException) {
                LogMessages.logWarning("[Indexing] Connection error: " + e.getMessage());
                //updateMessage(messages.getString("error.connectionError") + ": " + e.getMessage());
            } else {
                LogMessages.logWarning("[Indexing] Exception: " + e.getMessage());
                //updateMessage(messages.getString("status.folderLoadError") + ": " + e.getMessage());
            }
            this.cancel(); // TODO -> Override call(), succeded() and call cancel() on failed.
        }
        return null;
    }

    public List<DocumentLoader.LoadedDocument> getLoadedDocuments() {
        return loadedDocuments;
    }

  */
}
