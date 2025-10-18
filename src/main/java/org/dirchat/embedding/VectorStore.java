/*
=====================================
SQLite Database Architecture Diagram
=====================================

+-------------------+      +-------------------+      +-------------------+
|   table_index     |      |   table_document  |      |    table_chunk    |
+-------------------+      +-------------------+      +-------------------+
| id (PK)           |      | id (PK)           |      | id (PK)           |
| property_key (U)  |      | filename (U)      |      | document_id (FK)  |
| property_value    |      | location          |      | chunk_index       |
+-------------------+      | number_pages      |      | page_number       |
                          | filetype          |      | cursor_pointer    |
                          | toc               |      | embedding_vector  |
                          | metadata          |      | metadata          |
                          +-------------------+      +-------------------+

+-------------------+
|     settings      |
+-------------------+
| key (PK)          |
| value             |
+-------------------+

Legend:
- PK: Primary Key
- FK: Foreign Key
- U: Unique

Relationships:
- table_chunk.document_id references table_document.id

Table Descriptions:
- table_index: Stores global index properties (e.g., version, settings).
- table_document: Stores metadata for each document (filename, location, pages, type, TOC, etc.).
- table_chunk: Stores chunk embeddings and pointers for each document chunk. Each chunk is linked to a document via document_id.
- settings: Stores global settings (e.g., embedding method).

This diagram is intended to help you quickly understand the database schema and relationships used by VectorStore.
*/

package org.dirchat.embedding;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.dirchat.document.DocumentLoader;
import org.dirchat.document.Chunk;
import org.dirchat.document.ScoredChunk;
import org.dirchat.util.Dirchat;
import org.dirchat.util.LogMessages;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class VectorStore {

    private final ObjectMapper mapper = new ObjectMapper();

    public static enum TableDocumentCol {
            DOCUMENT_ID("document_id"),
            FILENAME("filename"),
            PAGE_NUMBER("pages_number"),
            FILETYPE("filetype"),
            TOC("toc"),
            METADATA("metadata");

            private final String stringValue;
            TableDocumentCol(String stringValue) {
                this.stringValue = stringValue;
            }
            public String getStringValue() {
                return stringValue;
            }
    }

    public static enum TableChunkCol {
            ID("id"),
            DOCUMENT_ID("document_id"),
            CHUNK_INDEX("chunk_index"),
            PAGE_NUMBER("page_number"),
            CURSOR_POINTER("cursor_pointer"),
            EMBEDDING_VECTOR("embedding_vector"),
            EMBEDDING_SOURCE("embedding_source");

            private final String stringValue;
            TableChunkCol(String stringValue) {
                this.stringValue = stringValue;
            }
            public String getStringValue() {
                return stringValue;
            }
    }

    // SQLite database file name
    private Path sqliteDbPath;

    Path getFolder() {
        return sqliteDbPath.getParent().toAbsolutePath();
    }

    // TODO: This is not a STORE!
    // Only this constructor is used to initialize the VectorStore with a specific folder
    public VectorStore(String selectedFolder) {
        // Ensure .dirchat directory exists
        Path dirPath = Path.of(selectedFolder, Dirchat.DIRECTORY);
        this.sqliteDbPath = dirPath.resolve("vectorstore.db").toAbsolutePath();

        try {
            Files.createDirectories(dirPath);
            initSqliteDb();
        } catch (IOException e) {
            throw new RuntimeException("Failed to create" + Dirchat.DIRECTORY + ": "+ dirPath, e);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to initialize SQLite database: " + sqliteDbPath, e);
        }
    }

    // Initialize SQLite DB and create tables if not exists
    private void initSqliteDb() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath.toString())) {
            createTableDocument(conn);
            createTableChunk(conn);
        }
    }

    private void createTableDocument(Connection conn) throws SQLException {
        // TABLE_DOCUMENT: stores document metadata
        String sql = "CREATE TABLE IF NOT EXISTS table_document ("
                + "document_id INTEGER PRIMARY KEY,"
                + "filename TEXT UNIQUE,"
                + "number_pages INTEGER,"
                + "filetype TEXT,"
                + "toc TEXT,"
                + "metadata TEXT"
                + ")";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.executeUpdate();
        }
    }

    private void createTableChunk(Connection conn) throws SQLException {
        // TABLE_CHUNK: stores chunk embeddings and pointers
        String sql = "CREATE TABLE IF NOT EXISTS table_chunk ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "document_id INTEGER,"
                + "chunk_index INTEGER,"
                + "cursor_pointer INTEGER,"
                + "source_file TEXT,"
                + "document_type TEXT,"
                + "time_stamp TEXT,"
                + "length INTEGER,"
                + "embedding_vector TEXT NOT NULL,"
                + "embedding_source TEXT,"
                + "FOREIGN KEY(document_id) REFERENCES table_document(document_id)"
                + ")";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.executeUpdate();
        }
    }

    protected void setEmbeddingMethod(int id, String method) throws SQLException {
        String sql = "UPDATE table_chunk SET embedding_source = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath.toString())) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, method);
                pstmt.setInt(2, id);
                pstmt.executeUpdate();
            }
        }
    }

    // Get indexing method from settings table
    public String getEmbeddingMethod(int id) throws SQLException {
        String sql = "SELECT embedding_source FROM table_chunk WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath.toString())) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("embedding_source");
                    }
                }
            }
        }
        return null;
    }

    protected void setColToTableChunk(int id, TableChunkCol chunkColumn, String value) throws SQLException {
        String sql = "UPDATE table_chunk SET "
         + chunkColumn.getStringValue()
        + " = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath.toString())) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, value);
                pstmt.setInt(2, id);
                pstmt.executeUpdate();
            }
        }
    }

    public String getColFromTableChunk(int id, TableChunkCol chunkColumn) throws SQLException {
        String sql = "SELECT "
                + chunkColumn.getStringValue()
                + " FROM table_chunk WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath.toString())) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(chunkColumn.getStringValue());
                    }
                }
            }
        }
        return null;
    }

    protected void setColToTableDocument(int documentId, TableDocumentCol documentColumn, String value) throws SQLException {
        String sql = "UPDATE table_chunk SET "
                + documentColumn.getStringValue()
                + " = ? WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath.toString())) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, value);
                pstmt.setInt(2, documentId);
                pstmt.executeUpdate();
            }
        }
    }

    public String getColFromTableDocument(int id,
                                          TableDocumentCol documentColumn)
            throws SQLException {
        String sql = "SELECT "
                + documentColumn.getStringValue()
                + " FROM table_chunk WHERE id = ?";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath.toString())) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, id);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString(documentColumn.getStringValue());
                    }
                }
            }
        }
        return null;
    }

    /**
     * Compound search: search for topK similar chunks across ALL documents (SQLite-backed, loads chunks in batches)
     */
    public List<ScoredChunk> compoundSearchWithScores(List<Double> queryVector, int topK) throws SQLException, IOException {
        List<ScoredChunk> allScoredChunks = new ArrayList<>();
        String sql = "SELECT chunk_id, vector FROM table_chunk";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                ObjectMapper mapper = new ObjectMapper();
                while (rs.next()) {
                    Chunk chunk = mapper.readValue(rs.getString(""), Chunk.class);
                    List<Double> vector = mapper.readValue(rs.getString("vector"), new TypeReference<List<Double>>() {});
                    double score = cosineSimilarity(queryVector, vector);
                    allScoredChunks.add(new ScoredChunk(chunk, score));
                }
            }
        }
        return allScoredChunks.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += a.get(i) * a.get(i);
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    // Checks if the index exists for a given docFileName, based on the configured database type
    public boolean indexExists(DocumentLoader.LoadedDocument document) {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "SELECT 1 FROM table_document WHERE document_id = ? LIMIT 1";
            try (java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, document.documentId);
                try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (Exception e) {
            LogMessages.logWarning(e.getMessage());
            return false;
        }
    }

    public boolean indexExists() {
        if (Files.exists(sqliteDbPath)) {
            String cntSql = "SELECT COUNT(*) FROM table_document";
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath.toString())) {
                try (PreparedStatement pstmt = conn.prepareStatement(cntSql);
                     ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        int count = rs.getInt(1);
                        return count > 0;
                    }
                }
            } catch (SQLException e) {

                // Table does not exist or other SQL error
                LogMessages.logWarning("DB file exists but no connection possible. Reason: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    public List<DocumentLoader.LoadedDocument> loadIndexing() throws IOException {
        List<DocumentLoader.LoadedDocument> loadedDocs = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath.toString())) {
            String sql = "SELECT id, filename, location, number_pages, filetype, metadata FROM table_document";
            try (PreparedStatement pstmt = conn.prepareStatement(sql); ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    int documentId = rs.getInt("id");
                    String filename = rs.getString("filename");
                    String location = rs.getString("location");
                    int numberPages = rs.getInt("number_pages");
                    String filetype = rs.getString("filetype");
                    String metadataJson = rs.getString("metadata");
                    // Load content and absolutePath (assuming location is the path)
                    String absolutePath = location;
                    String content = "";
                    org.apache.tika.metadata.Metadata metadata = new org.apache.tika.metadata.Metadata();
                    try {
                        content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(location)));
                        // Extract metadata using Tika
                        org.apache.tika.parser.AutoDetectParser parser = new org.apache.tika.parser.AutoDetectParser();
                        org.apache.tika.sax.BodyContentHandler handler = new org.apache.tika.sax.BodyContentHandler(-1);
                        try (java.io.FileInputStream stream = new java.io.FileInputStream(location)) {
                            parser.parse(stream, handler, metadata);
                        }
                    } catch (Exception ignore) {}
                    DocumentLoader.LoadedDocument doc = new DocumentLoader.LoadedDocument(documentId, filename, content, absolutePath);
                    doc.setMetadata(metadata);
                    loadedDocs.add(doc);
                }
            }
        } catch (Exception e) {
            throw new IOException("Failed to load indexed documents from SQLite: " + e.getMessage(), e);
        }
        return loadedDocs;
    }

    public void saveChunkEmbeddingToSqlite( Chunk chunk, List<Double> embedding) throws SQLException, IOException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "INSERT INTO table_chunk (document_id," +
                    " chunk_index," +
                    " cursor_pointer," +
                    " source_file," +
                    " document_type," +
                    " time_stamp," +
                    " length," +
                    " embedding_source," +
                    " embedding_vector) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {

                pstmt.setInt(1, chunk.getDocumentId());
                pstmt.setInt(2, chunk.getChunkIndex());
                pstmt.setInt(3, chunk.getCursorPointer());
                pstmt.setString(4, chunk.getSourceFile());
                pstmt.setString(5, chunk.getDocumentType());
                pstmt.setString(6, chunk.getTimeStamp().toString());
                pstmt.setInt(7, chunk.getLength());
                pstmt.setString(8, chunk.getEmbeddingSource());
                pstmt.setString(9, mapper.writeValueAsString(embedding));

                pstmt.executeUpdate();

            }
        }
    }

    // Save embeddings for a document (SQLite)
    public void saveDocumentEmbeddingsToSqlite(DocumentLoader.LoadedDocument loadedDocument, Map<Chunk, List<Double>> chunkEmbeddings) throws SQLException, IOException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sqlDocument = "INSERT OR REPLACE INTO table_document (id, filename, location, number_pages, filetype, toc, metadata) VALUES (?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmtDoc = conn.prepareStatement(sqlDocument)) {
                ObjectMapper mapper = new ObjectMapper();
                pstmtDoc.setInt(1, loadedDocument.getDocumentId());
                pstmtDoc.setString(2, loadedDocument.getFileName());
                pstmtDoc.setString(3, loadedDocument.getAbsolutePath());
                pstmtDoc.setInt(4, loadedDocument.getNumberPages());
                pstmtDoc.setString(5, loadedDocument.getFileType());
                pstmtDoc.setString(6, mapper.writeValueAsString(loadedDocument.getToc()));
                pstmtDoc.setString(7, mapper.writeValueAsString(loadedDocument.getMetadataMap()));
                pstmtDoc.executeUpdate();
            }
        }
    }





    /**
     * Insert or update document metadata in DOCUMENT table
     */
    public void upsertDocumentMetadata(DocumentLoader.LoadedDocument doc, String metadataJson) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "UPDATE table_document SET toc = ?, metadata = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, doc.toc);
                pstmt.setString(2, metadataJson);
                pstmt.setInt(3, doc.documentId);
                pstmt.executeUpdate();
            }
        }
    }

    // Retrieve chunk embeddings and pointers from TABLE_CHUNK
    public List<ScoredChunk> getChunkEmbeddingsWithScores(List<Double> queryVector, int topK) throws SQLException, IOException {
        List<ScoredChunk> allScoredChunks = new ArrayList<>();




        String sql = "SELECT document_id, chunk_index, page_number, cursor_pointer, embedding_vector FROM table_chunk";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                ObjectMapper mapper = new ObjectMapper();
                while (rs.next()) {
                    int documentId = rs.getInt("document_id");
                    int chunkIndex = rs.getInt("chunk_index");
                    int pageNumber = rs.getInt("page_number");
                    int cursorPointer = rs.getInt("cursor_pointer");
                    String embeddingJson = rs.getString("embedding_vector");
                    List<Double> vector = mapper.readValue(embeddingJson, new TypeReference<List<Double>>() {});
                    // Reconstruct Chunk (set sourceFile, index, chunkType to null/defaults)
                    Chunk chunk = new Chunk();//(documentId, 0, chunkIndex, pageNumber, cursorPointer, documentId, null);
                    double score = cosineSimilarity(queryVector, vector);
                    allScoredChunks.add(new ScoredChunk(chunk, score));
                }
            }
        }
        return allScoredChunks.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }


    // Main function to run indexing on a folder. Data is loaded by each document.
    public void runIndexing(EmbeddingService embeddingService) throws SQLException, IOException {

        IndexingTask task = new IndexingTask(this,null);
        Thread thread = new Thread(task);
        thread.start();
    }


    /**
     * Returns the number of documents loaded in table_document.
     */
    public int getDocumentCount() {
        String sql = "SELECT COUNT(*) FROM table_document";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath.toString())) {
            try (PreparedStatement pstmt = conn.prepareStatement(sql);
                 ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
            }
        } catch (SQLException e) {
            LogMessages.logWarning("Failed to get document count: " + e.getMessage());
        }
        return 0;
    }
}
