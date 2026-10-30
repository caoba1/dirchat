/*
 * dirchat — Java directory chat application.
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 * License: GNU AGPL v3 or later. See LICENSE for details.
 */

package org.dirchat.embedding;

import javafx.concurrent.Task;
import org.dirchat.document.DocumentLoader;
import org.dirchat.document.StreamSplitter;
import org.dirchat.util.ConfigManager;
import org.dirchat.util.Dirchat;
import org.dirchat.util.ErrorMessages;
import org.dirchat.util.Messages;
import javafx.application.Platform;
import java.nio.file.Path;
import java.util.*;
import java.io.IOException;
import org.dirchat.controller.ProgressDialog;
import org.dirchat.util.LogMessages;

public class IndexingTask extends Task<Void> {
    private final List<Path> files;
    private final VectorStore vectorStore;
    private final ProgressDialog progressDialog;
    private List<DocumentLoader.LoadedDocument> loadedDocuments;

    public IndexingTask(VectorStore vectorStore, ProgressDialog progressDialog) {
        this(vectorStore, progressDialog, scanFiles(vectorStore));
    }

    public IndexingTask(
            VectorStore vectorStore,
            ProgressDialog progressDialog,
            List<Path> files) {
        this.vectorStore = vectorStore;
        this.progressDialog = progressDialog;
        this.files = List.copyOf(files);
        LogMessages.debug("[Indexing] Task init: " + this.files.size() + " files");
    }

    private static List<Path> scanFiles(VectorStore vectorStore) {
        try {
            return DocumentLoader.scanDirectory(vectorStore.getFolder().getParent()).supportedFiles();
        } catch (IOException e) {
            throw new IllegalStateException("Could not scan the directory for indexing.", e);
        }
    }

    @Override
    protected Void call() throws Exception {
        LogMessages.debug("[Indexing] startingIndexing task");
        try {
            List<DocumentLoader.LoadedDocument> docs = new ArrayList<>();
            LogMessages.debug("[Indexing] Task started");
            if (vectorStore.indexExists()) {
                LogMessages.debug("[Indexing] Index exists, loading from DB");

                List<DocumentLoader.LoadedDocument> loadedDocs = vectorStore.loadIndexing();
                TreeSet<Integer> documentIds = loadedDocs
                        .stream()
                        .map(DocumentLoader.LoadedDocument::getDocumentId)
                        .collect(java.util.stream.Collectors.toCollection(TreeSet::new));

                for (Path path : files) {
                    DocumentLoader.LoadedDocument doc = new DocumentLoader.LoadedDocument(
                            path.toAbsolutePath().toString(),
                            false);
                    doc.setIntexed(documentIds.contains(doc.getDocumentId()));
                    docs.add(doc);
                }

                updateMessage(Messages.get("indexing.status.loaded"));
            } else {
                LogMessages.debug("[Indexing] Index does not exist. Processing files");
                int total = files.size();
                int count = 0;
                EmbeddingService embeddingService = new EmbeddingService(
                        ConfigManager.getOllamaBaseUrl(),
                        ConfigManager.getIndexingMethod()
                );
                for (Path path : files) {
                    if ((progressDialog != null && progressDialog.isCancelled()) || isCancelled()) {
                        LogMessages.debug("[Indexing] Cancelled by user (file loop)");
                        cancel(true);
                        break;
                    }

                    count++;
                    String fileName = path.getFileName().toString();
                    LogMessages.debug("[Indexing] Processing file: " + path);
                    updateMessage(Messages.format("indexing.progress.loading-file", fileName));
                    if (progressDialog != null) {
                        progressDialog.setFileName(path.toString());
                        progressDialog.setProgress((double) count / total);
                    }

                    DocumentLoader.LoadedDocument doc = new DocumentLoader.LoadedDocument(
                            path.toAbsolutePath().toString(),
                            true);
                    vectorStore.upsertLoadedDocument(doc);

                    StreamSplitter handler = new StreamSplitter(
                            embeddingService,
                            vectorStore,
                            doc,
                            progressDialog);
                    handler.splitDocument();
                    vectorStore.markIndexed(doc.getDocumentId());
                    doc.setIntexed(true);
                    docs.add(doc);
                    if (progressDialog != null) {
                        progressDialog.setFileProgress(1);
                    }
                }
                updateMessage(Messages.get("indexing.status.loaded"));
            }
            loadedDocuments = docs;
            LogMessages.debug("[Indexing] Task completed");
        } catch (Exception e) {
            if (progressDialog != null) {
                progressDialog.setMessage(ErrorMessages.describe(e, "indexing.status.failed"));
            }
            LogMessages.logWarning("Indexing task failed.", e);
            throw e;
        }
        return null;
    }

    public List<DocumentLoader.LoadedDocument> getLoadedDocuments() {
        return loadedDocuments;
    }
}