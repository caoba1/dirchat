// Represents a chunk of text with metadata
//
// A Chunk is a segment of text from a document, along with metadata about its origin and position.
//
// Fields:
//   - sourceFile: The name of the source file this chunk comes from.
//   - index: A global, linear identifier for the chunk within the document or dataset.
//   - chunkIndex: A pointer-based identifier, used for reconstructing the chunk's position within the document (e.g., within a page or section).
//   - chunkType: The type of chunk (e.g., "content" or "metadata").
//   - pageNumber: The page number in the source document where this chunk is located.
//   - cursorPointer: A pointer for locating the chunk within the document.
//   - documentId: The unique ID of the document this chunk belongs to.
//   - content: The actual text content of the chunk (used at runtime, not stored in DB).
//
// Why both index and chunkIndex?
//   - index is for global, linear identification (across all chunks in a document).
//   - chunkIndex is for local, pointer-based identification (within a page, section, or other logical unit).
//   This dual system allows efficient processing and accurate reconstruction of chunk positions in complex documents.
//
// Chunks are used for document processing, embedding, search, and retrieval throughout the application.
package org.dirchat.model;

public class Chunk {
    private final String sourceFile;
    private final int index;
    private final String chunkType; // e.g., "content" or "metadata"
    // Pointer-based fields
    private final int chunkIndex;
    private final int pageNumber;
    private final int cursorPointer;
    private final int documentId;
    // Content is only used at runtime, not stored in DB
    private String content;

    public Chunk(String sourceFile, int index, int chunkIndex, int pageNumber, int cursorPointer, int documentId, String chunkType) {
        this.sourceFile = sourceFile;
        this.index = index;
        this.chunkIndex = chunkIndex;
        this.pageNumber = pageNumber;
        this.cursorPointer = cursorPointer;
        this.documentId = documentId;
        this.chunkType = chunkType;
        this.content = null;
    }

    // Public no-argument constructor for Jackson deserialization
    public Chunk() {
        this.sourceFile = null;
        this.index = 0;
        this.chunkIndex = 0;
        this.pageNumber = 0;
        this.cursorPointer = 0;
        this.documentId = 0;
        this.chunkType = "content";
        this.content = null;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public int getIndex() {
        return index;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getCursorPointer() {
        return cursorPointer;
    }

    public int getDocumentId() {
        return documentId;
    }

    public String getChunkType() {
        return chunkType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
        return new Chunk(parts[0], Integer.parseInt(parts[1]), 0, 0, 0, 0, chunkType);
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
