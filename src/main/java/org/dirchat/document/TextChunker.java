// Splits text into chunks
package org.dirchat.document;

import org.dirchat.model.Chunk;

import java.util.ArrayList;
import java.util.List;

public class TextChunker {
    private final int paragraphsPerChunk;
    private final int overlap;

    public TextChunker(int paragraphsPerChunk, int overlap) {
        this.paragraphsPerChunk = Math.max(1, paragraphsPerChunk);
        this.overlap = Math.max(0, Math.min(overlap, paragraphsPerChunk - 1));
    }

    public TextChunker(int paragraphsPerChunk) {
        this(paragraphsPerChunk, 0);
    }

    public List<Chunk> chunkText(String text, String sourceFileName, int documentId) {
        List<Chunk> chunks = new ArrayList<>();
        String[] paragraphs = text.split("\r?\n\r?\n");
        int chunkIndex = 0;
        int step = paragraphsPerChunk - overlap;
        for (int i = 0; i < paragraphs.length; i += step) {
            StringBuilder chunkContent = new StringBuilder();
            for (int j = 0; j < paragraphsPerChunk && (i + j) < paragraphs.length; j++) {
                String trimmed = paragraphs[i + j].trim();
                if (!trimmed.isEmpty()) {
                    if (!chunkContent.isEmpty()) chunkContent.append("\n\n");
                    chunkContent.append(trimmed);
                }
            }
            String chunkText = chunkContent.toString();
            if (!chunkText.isEmpty()) {
                Chunk chunk = new Chunk(sourceFileName, chunkIndex, chunkIndex, 0, 0, documentId, "content");
                chunk.setContent(chunkText);
                chunks.add(chunk);
                chunkIndex++;
            }
        }
        return chunks;
    }

    // Simple metadata extraction: first non-empty paragraph, or customize as needed
    private String extractMetadata(String text) {
        String[] paragraphs = text.split("\\r?\\n\\r?\\n");
        for (String para : paragraphs) {
            String trimmed = para.trim();
            if (!trimmed.isEmpty()) {
                // ToDo: add more logic to detect TOC, chapter, etc.
                return trimmed;
            }
        }
        return null;
    }

    /**
     * Reconstruct chunk content from pointer fields using DocumentLoader.LoadedDocument.
     * This assumes chunkIndex maps to a chunk in the chunked paragraphs,
     * and cursorPointer is the character offset in the document content.
     * You can adjust logic as needed for your chunking strategy.
     */
    public String getChunkContent(DocumentLoader.LoadedDocument doc, int chunkIndex, int pageNumber, int cursorPointer) {
        String[] paragraphs = doc.content.split("\r?\n\r?\n");
        int start = chunkIndex * paragraphsPerChunk;
        int end = Math.min(start + paragraphsPerChunk, paragraphs.length);
        StringBuilder chunkContent = new StringBuilder();
        for (int i = start; i < end; i++) {
            if (chunkContent.length() > 0) chunkContent.append("\n\n");
            chunkContent.append(paragraphs[i].trim());
        }
        // Optionally, use cursorPointer to further slice the chunk
        if (cursorPointer > 0 && cursorPointer < chunkContent.length()) {
            return chunkContent.substring(cursorPointer);
        }
        return chunkContent.toString();
    }
}
