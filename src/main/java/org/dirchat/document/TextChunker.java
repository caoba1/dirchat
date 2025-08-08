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
}
