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

    public List<Chunk> chunkText(String text, String sourceFileName) {
        List<Chunk> chunks = new ArrayList<>();
        // --- Add metadata chunk (e.g., TOC or chapter info) ---
        String metadataContent = extractMetadata(text); // You can improve this extraction as needed
        if (metadataContent != null && !metadataContent.isBlank()) {
            chunks.add(new Chunk(sourceFileName, -1, metadataContent, "metadata"));
        }
        String[] paragraphs = text.split("\\r?\\n\\r?\\n");
        int chunkIndex = 0;
        int step = paragraphsPerChunk - overlap;
        for (int i = 0; i < paragraphs.length; i += step) {
            StringBuilder chunkContent = new StringBuilder();
            for (int j = 0; j < paragraphsPerChunk && (i + j) < paragraphs.length; j++) {
                String trimmed = paragraphs[i + j].trim();
                if (!trimmed.isEmpty()) {
                    if (chunkContent.length() > 0) chunkContent.append("\n\n");
                    chunkContent.append(trimmed);
                }
            }
            if (chunkContent.length() > 0) {
                chunks.add(new Chunk(sourceFileName, chunkIndex++, chunkContent.toString()));
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
}
