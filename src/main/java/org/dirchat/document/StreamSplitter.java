package org.dirchat.document;

//import org.apache.tika.sax.ContentHandler;
import javafx.application.Platform;
import org.dirchat.controller.ProgressDialog;
import org.dirchat.embedding.VectorStore;
import org.dirchat.embedding.EmbeddingService;

import org.dirchat.util.ConfigManager;
import org.dirchat.util.LogMessages;
import org.xml.sax.SAXException;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.Attributes;

import org.dirchat.controller.ProgressDialog;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StreamSplitter implements ContentHandler {
    private static final long CHUNK_SIZE_LIMIT = 1024*1024; // 1 MB
    private static final int CHUNK_CHAR_LIMIT = ConfigManager.getChunkCharacterLimit(); // 10,000 characters
    private static final int CHUNK_OVERLAP = ConfigManager.getOverlapCharacters(); // 200 characters

    private final AtomicInteger chunkIndex = new AtomicInteger(0);
    private final AtomicInteger cursorPointer = new AtomicInteger(0);

    private final EmbeddingService embeddingService;
    private final VectorStore vectorStore;
    private final DocumentLoader.LoadedDocument document;
    private final ProgressDialog progressDialog;
    // For chunk extraction mode
    private final Integer targetCursorPointer;
    private final Integer targetChunkIndex;
    private String extractedChunk = null;
    private boolean extractionMode = false;

    public StreamSplitter(EmbeddingService embeddingService,
                          VectorStore vectorStore,
                          DocumentLoader.LoadedDocument document,
                          ProgressDialog progressDialog) {
        this.embeddingService = embeddingService;
        this.vectorStore = vectorStore;
        this.document = document;
        this.progressDialog = progressDialog;
        this.targetCursorPointer = null;
        this.targetChunkIndex = null;
        this.extractionMode = false;
    }

    // Constructor for extraction by cursorPointer
    public StreamSplitter(int targetCursorPointer) {
        this.embeddingService = null;
        this.vectorStore = null;
        this.document = null;
        this.progressDialog = null;
        this.targetCursorPointer = targetCursorPointer;
        this.targetChunkIndex = null;
        this.extractionMode = true;
    }
    // Constructor for extraction by chunkIndex
    public StreamSplitter(int targetChunkIndex, boolean byIndex) {
        this.embeddingService = null;
        this.vectorStore = null;
        this.document = null;
        this.progressDialog = null;
        this.targetCursorPointer = null;
        this.targetChunkIndex = byIndex ? targetChunkIndex : null;
        this.extractionMode = true;
    }
    public String getExtractedChunk() {
        return extractedChunk;
    }

    private final StringBuilder currentChunk = new StringBuilder();
    private String lastChunk = "";
    private String overlapBuffer = "";

    private void processChunk(String thisChunk) {
        lastChunk = thisChunk;

        // Update overlapBuffer with the last CHUNK_OVERLAP characters
        if (thisChunk.length() >= CHUNK_OVERLAP) {
            overlapBuffer = thisChunk.substring(thisChunk.length() - CHUNK_OVERLAP);
        } else {
            overlapBuffer = thisChunk;
        }

        if (extractionMode) {
            // Extraction by cursorPointer
            if (targetCursorPointer != null && cursorPointer.get() == targetCursorPointer) {
                extractedChunk = thisChunk;
            }
            // Extraction by chunkIndex
            if (targetChunkIndex != null && chunkIndex.get() == targetChunkIndex) {
                extractedChunk = thisChunk;
            }
            // Only update pointers, do not process or store chunk
            cursorPointer.addAndGet(thisChunk.length() - CHUNK_OVERLAP);
            chunkIndex.incrementAndGet();
            return;
        }

        // Indexing mode: process and store chunk
        dirchatStoreChunk(thisChunk);
        cursorPointer.addAndGet(thisChunk.length() - CHUNK_OVERLAP);
        // For demonstration, we just print the chunk size
        System.out.println("Processed chunk of size: " + thisChunk.length());
    }

    private boolean dirchatStoreChunk(String chunkText) {
        Chunk chunk = new Chunk(
            this.document.getAbsolutePath(),
            this.document.getFileType(),
            this.document.getDocumentId(),
            this.chunkIndex.getAndIncrement(),
            this.cursorPointer.get()
        );

        if ( progressDialog.isCancelled() ) {
            throw new RuntimeException("Cancelado por el usuario");
        }

        List<Double> embedding = embeddingService.embedText(chunkText); //magic
        try {
            vectorStore.saveChunkEmbeddingToSqlite(chunk, embedding);
        } catch (SQLException | IOException e) {
            throw new RuntimeException(e);
        }

        // Call to
        double totalChunks = 10_000;
        System.out.println("DEBUG Total chunks: " + totalChunks);
        double progress = Math.min(chunkIndex.doubleValue() / (totalChunks), 1.0);
        Platform.runLater(() -> progressDialog.setFileProgress(progress));
        if (progressDialog.isCancelled() ) {
            LogMessages.logWarning("[Indexing] Cancelled by user (after embedding chunk)");
        }

        return true;
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        // Append the new incoming text to the current chunk buffer
        currentChunk.append(ch, start, length);

        // Check if the current chunk exceeds the maximum size
        while (currentChunk.length() >= CHUNK_CHAR_LIMIT) {

            // 1. Get the segment that fits the chunk size (1024)
            String chunkToProcess = currentChunk.substring(0, CHUNK_CHAR_LIMIT);

            // 2. Prepend the overlap from the *previous* chunk (if it exists)
            String finalChunk = overlapBuffer + chunkToProcess;

            // 3. Process the full chunk, which also updates the overlapBuffer
            processChunk(finalChunk);

            // 4. Remove the processed segment (up to CHUNK_SIZE minus the overlap)
            //    from the front of the currentChunk buffer to start the next chunk.
            currentChunk.delete(0, CHUNK_CHAR_LIMIT - CHUNK_OVERLAP);
            // The remaining text in currentChunk is the start of the next one.
        }
    }

    @Override
    public void endDocument() throws SAXException {
        // Process any remaining text in the buffer as the final chunk
        if (currentChunk.length() > 0) {
            String finalChunk = overlapBuffer + currentChunk.toString();
            processChunk(finalChunk);
        }
    }

    // Other ContentHandler methods (startDocument, startElement, etc.) are usually left empty or handle metadata
    @Override public void startDocument() throws SAXException {}
    @Override public void endElement(String uri, String localName, String qName) throws SAXException {}
    @Override public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {}
    @Override public void endPrefixMapping(String prefix) throws SAXException {}
    @Override public void skippedEntity(String name) throws SAXException {}
    @Override public void setDocumentLocator(Locator locator) {}
    @Override public void processingInstruction(String target, String data) throws SAXException {}
    @Override public void startPrefixMapping(String prefix, String uri) throws SAXException {}
    @Override public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {}
}
