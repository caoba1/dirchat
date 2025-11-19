package org.dirchat.embedding;

import javafx.concurrent.Task;
import org.dirchat.document.DocumentLoader;
import org.dirchat.document.StreamSplitter;
import org.dirchat.util.ConfigManager;
import org.dirchat.util.Dirchat;
import javafx.application.Platform;
import java.io.File;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.dirchat.controller.ProgressDialog;
import org.dirchat.util.LogMessages;
import java.util.stream.Collectors;

public class IndexingTask extends Task<Void> {
    private final File folder; //DB fil
    //TODO: see implementation in DocumentLoader -> list files instead of directory. a list of paths!!! use walk!
    private final VectorStore vectorStore;
    private final ProgressDialog progressDialog;
    private List<DocumentLoader.LoadedDocument> loadedDocuments;
    private final Dirchat.ResourceBundleWrapper messages = new Dirchat.ResourceBundleWrapper("messages");

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

                // loading document ids
                List<DocumentLoader.LoadedDocument> loadedDocs = vectorStore.loadIndexing();
                TreeSet<Integer> documentIds = loadedDocs
                        .stream()
                        .map( doc -> doc.getDocumentId() )
                        .collect( Collectors.toCollection( TreeSet::new ) );

                Path folderPath = folder.getParentFile().toPath();
                try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(folderPath)) {
                    for (Path path : dirStream) {
                        if (Dirchat.safeIsRegularFile(path) &&
                            Dirchat.isSupported(path))
                        {
                            DocumentLoader.LoadedDocument doc = new DocumentLoader.LoadedDocument(
                                    path.toAbsolutePath().toString(),
                                    false);
                            doc.setIntexed(documentIds.contains(doc.getDocumentId())); // non-indexed documents may be updated later
                            docs.add(doc);
                        }
                    }
                }

                updateMessage(messages.getString("status.indexLoaded"));
            } else {
                LogMessages.log("[Indexing] Index does not exist or is not complete. Processing files");

                File folderFileInstance = folder.getParentFile();
                File[] files = folderFileInstance.listFiles();
                final long total = Arrays.stream(files)
                        .filter( (File f) -> f.isFile() )
                        .count();
                AtomicInteger count = new AtomicInteger(0);
                EmbeddingService embeddingService = new EmbeddingService(
                        ConfigManager.getOllamaBaseUrl(),
                        ConfigManager.getIndexingMethod()
                );
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

                        DocumentLoader.LoadedDocument doc = new DocumentLoader.LoadedDocument(
                                file.getAbsolutePath(),
                                true);
                        vectorStore.upsertLoadedDocument(doc); // Save in table_document

                        // Our custom ContentHandler that splits the stream into chunks and embeds them
                        org.xml.sax.ContentHandler handler = new StreamSplitter( embeddingService,
                                vectorStore,
                                doc,
                                progressDialog);

                        // Parse the file synchronously, waiting for handler to complete
                        try (java.io.InputStream stream = new java.io.FileInputStream(file)) {
                            parser.parse(stream, handler, metadata, context);
                        }

                        doc.setIntexed(true);
                        docs.add(doc);
                        Platform.runLater(() -> progressDialog.setFileProgress(1));
                    }
                }
                updateMessage(messages.getString("status.indexLoaded"));
            }
            loadedDocuments = docs; // Todo: load at step
            LogMessages.log("[Indexing] Task completed");
        } catch (Exception e) {
            if (e instanceof java.net.ConnectException ||
                    e.getCause() instanceof java.net.ConnectException) {
                    LogMessages.logWarning("[Indexing] Connection error: " + e.getMessage());
                //updateMessage(messages.getString("error.connectionError") + ": " + e.getMessage());
                } else if (e instanceof org.apache.tika.exception.TikaException) {
                    LogMessages.logWarning("[Indexing] Tika Exception: " + e.getMessage());
                //updateMessage(messages.getString("status.folderLoadError") + ": " + e.getMessage());
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
}