// Represents a chunk of text with metadata
package org.dirchat.model;

public class Chunk {
    private final String sourceFile;
    private final int index;
    private final String content;
    private final String chunkType; // e.g., "content" or "metadata"

    public Chunk(String sourceFile, int index, String content) {
        this(sourceFile, index, content, "content");
    }

    public Chunk(String sourceFile, int index, String content, String chunkType) {
        this.sourceFile = sourceFile;
        this.index = index;
        this.content = content;
        this.chunkType = chunkType;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public int getIndex() {
        return index;
    }

    public String getContent() {
        return content;
    }

    public String getChunkType() {
        return chunkType;
    }

    @Override
    public String toString() {
        return "Chunk from " + sourceFile + " [#" + index + "]: " + content;
    }

    public String toStorageString() {
        // Save sourceFile, index, and chunkType
        return sourceFile + "::" + index + "::" + chunkType;
    }

    public static Chunk fromStorageString(String s) {
        String[] parts = s.split("::", 3);
        if (parts.length < 2) throw new IllegalArgumentException("Invalid storage string for Chunk: " + s);
        String chunkType = parts.length == 3 ? parts[2] : "content";
        return new Chunk(parts[0], Integer.parseInt(parts[1]), "", chunkType);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chunk chunk = (Chunk) o;
        return index == chunk.index && sourceFile.equals(chunk.sourceFile) && chunkType.equals(chunk.chunkType);
    }

    @Override
    public int hashCode() {
        int result = sourceFile.hashCode();
        result = 31 * result + index;
        result = 31 * result + chunkType.hashCode();
        return result;
    }
}
