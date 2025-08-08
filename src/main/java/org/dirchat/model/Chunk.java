// Represents a chunk of text with metadata
package org.dirchat.model;

public class Chunk {
    private final String sourceFile;
    private final int index;
    private final String content;

    public Chunk(String sourceFile, int index, String content) {
        this.sourceFile = sourceFile;
        this.index = index;
        this.content = content;
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

    @Override
    public String toString() {
        return "Chunk from " + sourceFile + " [#" + index + "]: " + content;
    }

    public String toStorageString() {
        // Save only sourceFile and index, not content
        return sourceFile + "::" + index;
    }

    public static Chunk fromStorageString(String s) {
        String[] parts = s.split("::", 2);
        if (parts.length != 2) throw new IllegalArgumentException("Invalid storage string for Chunk: " + s);
        // Content is not stored in the key, so use empty string
        return new Chunk(parts[0], Integer.parseInt(parts[1]), "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Chunk chunk = (Chunk) o;
        return index == chunk.index && sourceFile.equals(chunk.sourceFile);
    }

    @Override
    public int hashCode() {
        int result = sourceFile.hashCode();
        result = 31 * result + index;
        return result;
    }
}
