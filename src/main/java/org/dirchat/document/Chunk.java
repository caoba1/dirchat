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
package org.dirchat.document;
import javafx.util.converter.LocalDateTimeStringConverter;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.sax.BodyContentHandler;
import org.dirchat.util.ConfigManager;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;

// TODO: Make chunk a record
// TODO: NOT IN USE?
public class Chunk {

    private final String sourceFile;
    private final String documentType;
    private final int documentId; //TODO: DocumentID is hash of file path?
    private LocalDateTime timeStamp;

    // Pointer-based fields
    private int chunkIndex;
    private final int cursorPointer;
    private final int length = ConfigManager.getChunkCharacterLimit();
    private final String embeddingSource = ConfigManager.getIndexingMethod();
    private int pageNumber; // Page number in the source document

    public Chunk(String sourceFile,
                 String documentType,
                 int documentId,
                 int chunkIndex,
                 int cursorPointer,
                 int pageNumber) {
        this.sourceFile = sourceFile;
        this.documentType = documentType;
        this.documentId = documentId;
        this.chunkIndex = chunkIndex;
        this.cursorPointer = cursorPointer;
        this.pageNumber = pageNumber;
        this.timeStamp = LocalDateTime.now();
    }

    // Copy constructor
    public Chunk(Chunk other) {
        this.sourceFile = other.sourceFile;
        this.documentType = other.documentType;
        this.documentId = other.documentId;
        this.chunkIndex = other.chunkIndex;
        this.cursorPointer = other.cursorPointer;
        this.pageNumber = other.pageNumber;
        this.timeStamp = other.timeStamp;
    }

    // Public no-argument constructor for Jackson deserialization
    public Chunk() {
        this.sourceFile = null;
        this.documentType = null;
        this.documentId = 0;
        this.chunkIndex = 0;
        this.cursorPointer = 0;
        this.pageNumber = 0;
        this.timeStamp = LocalDateTime.now();
    }

    /**
     * Dynamically retrieves the content from the PDF file at the specified page and cursor position using Apache Tika.
     * @return The extracted text, or an empty string if not found or error occurs.
     */
    public String getContent() {
        if (sourceFile == null) {
            return "";
        }
        File file = new File(sourceFile);
        if (!file.exists()) {
            return "";
        }
        try (java.io.FileInputStream input = new java.io.FileInputStream(file)) {
            BodyContentHandler handler = new BodyContentHandler(-1); // unlimited
            Metadata metadata = new Metadata();
            ParseContext context = new ParseContext();
            PDFParser pdfParser = new PDFParser();
            pdfParser.parse(input, handler, metadata, context);
            String allText = handler.toString(); // TODO. optimize for large files
            int start = Math.max(0, cursorPointer);
            int end = length > 0 ? Math.min(start + length, allText.length()) : allText.length();
            return allText.substring(start, end);
        } catch (IOException | TikaException | SAXException e) {
            return "";
        }
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public int getChunkIndex() {
        return chunkIndex;
    }

    public int getCursorPointer() {
        return cursorPointer;
    }

    public int getDocumentId() {
        return documentId;
    }

    public int getLength() {
        return length;
    }

    public String getDocumentType() {
        return documentType;
    }

    public LocalDateTime getTimeStamp() {
        return timeStamp;
    }

    public String getEmbeddingSource() {
        return embeddingSource;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    @Override
    public String toString() {
        return "Chunk from " + sourceFile + " [#" + chunkIndex + "]$" ;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Chunk chunk = (Chunk) o;
        return chunkIndex == chunk.chunkIndex &&
                sourceFile.equals(chunk.sourceFile) &&
                documentType.equals(chunk.documentType);
        // TODO: Consider adding documentId?
    }

    @Override
    public int hashCode() {
        int result = sourceFile.hashCode(); //TODO: This should be equivalent to documentId.
        result = 31 * result + chunkIndex; //int is his own hashcode. 31 is prime and == (value << 5) - value
        return result;
    }
}
