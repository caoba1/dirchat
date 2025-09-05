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
import org.dirchat.model.Chunk;
import org.dirchat.util.LogMessages;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class VectorStore {

    // SQLite database file name
    private String sqliteDbPath;

    // Only this constructor is used to initialize the VectorStore with a specific folder
    public VectorStore(String selectedFolder) {
        // Ensure .dirchat directory exists
        Path dirPath = Path.of(selectedFolder, ".dirchat");
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create .dirchat directory: " + dirPath, e);
        }
        // Set SQLite DB path to SelectedFolder/.dirchat/vectorstore.db
        this.sqliteDbPath = dirPath.resolve("vectorstore.db").toString();
    }

    // Initialize SQLite DB and create tables if not exists
    public void initSqliteDb() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            // TABLE_INDEX: stores global index properties
            String indexSql = "CREATE TABLE IF NOT EXISTS table_index ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "property_key TEXT UNIQUE,"
                    + "property_value TEXT NOT NULL"
                    + ")";
            try (PreparedStatement pstmt = conn.prepareStatement(indexSql)) {
                pstmt.executeUpdate();
            }

            // TABLE_DOCUMENT: stores document metadata
            String documentSql = "CREATE TABLE IF NOT EXISTS table_document ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "filename TEXT UNIQUE,"
                    + "location TEXT,"
                    + "number_pages INTEGER,"
                    + "filetype TEXT,"
                    + "toc TEXT,"
                    + "metadata TEXT"
                    + ")";
            try (PreparedStatement pstmt = conn.prepareStatement(documentSql)) {
                pstmt.executeUpdate();
            }

            // TABLE_CHUNK: stores chunk embeddings and pointers
            String chunkSql = "CREATE TABLE IF NOT EXISTS table_chunk ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "document_id INTEGER,"
                    + "chunk_index INTEGER,"
                    + "page_number INTEGER,"
                    + "cursor_pointer INTEGER,"
                    + "embedding_vector TEXT NOT NULL,"
                    + "metadata TEXT,"
                    + "FOREIGN KEY(document_id) REFERENCES table_document(id)"
                    + ")";
            try (PreparedStatement pstmt = conn.prepareStatement(chunkSql)) {
                pstmt.executeUpdate();
            }

            // TABLE_SETTINGS: stores embedding method and other global settings
            String settingsSql = "CREATE TABLE IF NOT EXISTS settings ("
                    + "key TEXT PRIMARY KEY,"
                    + "value TEXT NOT NULL"
                    + ")";
            try (PreparedStatement pstmt = conn.prepareStatement(settingsSql)) {
                pstmt.executeUpdate();
            }
        }
    }

    // TODO: Throw a runtime exception instead???
    // Set indexing method in settings table
    public void setIndexingMethod(String method) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, "indexing_method");
                pstmt.setString(2, method);
                pstmt.executeUpdate();
            }
        }
    }

    // Get indexing method from settings table
    public String getIndexingMethod() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "SELECT value FROM settings WHERE key = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, "indexing_method");
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("value");
                    }
                }
            }
        }
        return null;
    }

    /**
     * Search for topK similar chunks within a single document (SQLite-backed)
     */
    public List<Chunk> search(String docFileName, List<Double> queryVector, int topK) throws SQLException, IOException {
        Map<Chunk, List<Double>> chunkMap = loadDocumentEmbeddingsFromSqlite(docFileName);
        return chunkMap.entrySet().stream()
                .map(entry -> new ScoredChunk(entry.getKey(), cosineSimilarity(queryVector, entry.getValue())))
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .limit(topK)
                .map(ScoredChunk::getChunk)
                .collect(Collectors.toList());
    }

    /**
     * Search for topK similar chunks with scores within a single document (SQLite-backed)
     */
    public List<ScoredChunk> searchWithScores(String docFileName, List<Double> queryVector, int topK) throws SQLException, IOException {
        Map<Chunk, List<Double>> chunkMap = loadDocumentEmbeddingsFromSqlite(docFileName);
        return chunkMap.entrySet().stream()
                .map(entry -> new ScoredChunk(entry.getKey(), cosineSimilarity(queryVector, entry.getValue())))
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    /**
     * Compound search: search for topK similar chunks across ALL documents (SQLite-backed, loads chunks in batches)
     */
    public List<ScoredChunk> compoundSearchWithScores(List<Double> queryVector, int topK) throws SQLException, IOException {
        List<ScoredChunk> allScoredChunks = new ArrayList<>();
        String sql = "SELECT chunk_id, vector, metadata FROM vectors";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                ObjectMapper mapper = new ObjectMapper();
                while (rs.next()) {
                    Chunk chunk = mapper.readValue(rs.getString("metadata"), Chunk.class);
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

    public void save(Map<Chunk, List<Double>> embeddings, String selectedFolder) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<Double>> stringKeyMap = embeddings.entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().toStorageString(),
                Map.Entry::getValue
            ));
        String filePath = selectedFolder + "/.dirchat/vectorstore.json";
        mapper.writeValue(new File(filePath), stringKeyMap);
    }


    // Save embeddings and metadata to SQLite
    public void saveToSqlite(Map<Chunk, List<Double>> embeddings) throws SQLException, IOException {
        // Load embedding method from config.properties
        Properties props = new Properties();
        try (java.io.InputStream input = getClass().getClassLoader().getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
            }
        }
        String embeddingMethod = props.getProperty("indexing.method", "unknown");
        // Check if 'vectors' table exists, and initialize DB if missing
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String checkTableSql = "SELECT name FROM sqlite_master WHERE type='table' AND name='vectors'";
            try (PreparedStatement stmt = conn.prepareStatement(checkTableSql);
                 ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    // Table does not exist, initialize DB
                    initSqliteDb();
                    LogMessages.log("Call to saveToSqlite() failed. I have initialized the SQLite database and created the 'vectors' table.");
                }
            }
        }
        // Now proceed with saving
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "INSERT OR REPLACE INTO vectors (chunk_id, vector, metadata) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                ObjectMapper mapper = new ObjectMapper(); // ObjectMapper is included in the Jackson library
                for (Map.Entry<Chunk, List<Double>> entry : embeddings.entrySet()) {
                    pstmt.setString(1, entry.getKey().toStorageString());
                    pstmt.setString(2, mapper.writeValueAsString(entry.getValue()));
                    pstmt.setString(3, mapper.writeValueAsString(entry.getKey())); // store only chunk metadata
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
        }
    }

    // Load embeddings and metadata from SQLite
    public Map<Chunk, List<Double>> loadFromSqlite() throws SQLException, IOException {
        Map<Chunk, List<Double>> chunkMap = new HashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "SELECT chunk_id, vector, metadata FROM vectors";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                ObjectMapper mapper = new ObjectMapper();
                while (rs.next()) {
                    String metadataJson = rs.getString("metadata");
                    Chunk chunk = mapper.readValue(metadataJson, Chunk.class);
                    List<Double> vector = mapper.readValue(rs.getString("vector"), new TypeReference<List<Double>>() {});
                    chunkMap.put(chunk, vector);
                }
            }
        }
        return chunkMap;
    }


    // Insert or update a vector and metadata
    public void insertOrUpdateVector(Chunk chunk, List<Double> vector) throws SQLException, IOException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "INSERT OR REPLACE INTO vectors (chunk_id, vector, metadata) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                ObjectMapper mapper = new ObjectMapper();
                pstmt.setString(1, chunk.toStorageString());
                pstmt.setString(2, mapper.writeValueAsString(vector));
                pstmt.setString(3, mapper.writeValueAsString(chunk));
                pstmt.executeUpdate();
            }
        }
    }

    // Get a vector and metadata by chunk_id
    public Map.Entry<Chunk, List<Double>> getVector(String chunkId) throws SQLException, IOException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "SELECT vector, metadata FROM vectors WHERE chunk_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, chunkId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        ObjectMapper mapper = new ObjectMapper();
                        Chunk chunk = mapper.readValue(rs.getString("metadata"), Chunk.class);
                        List<Double> vector = mapper.readValue(rs.getString("vector"), new TypeReference<List<Double>>() {});
                        return new AbstractMap.SimpleEntry<>(chunk, vector);
                    }
                }
            }
        }
        return null;
    }

    // Delete a vector by chunk_id
    public void deleteVector(String chunkId) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "DELETE FROM vectors WHERE chunk_id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, chunkId);
                pstmt.executeUpdate();
            }
        }
    }

    // Get all vectors and metadata
    public Map<Chunk, List<Double>> getAllVectors() throws SQLException, IOException {
        Map<Chunk, List<Double>> chunkMap = new HashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "SELECT chunk_id, vector, metadata FROM vectors";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                ObjectMapper mapper = new ObjectMapper();
                while (rs.next()) {
                    Chunk chunk = mapper.readValue(rs.getString("metadata"), Chunk.class);
                    List<Double> vector = mapper.readValue(rs.getString("vector"), new TypeReference<List<Double>>() {});
                    chunkMap.put(chunk, vector);
                }
            }
        }
        return chunkMap;
    }

    // Returns the embedding method used for the current SQLite database
    public String getSqliteEmbeddingMethod() {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "SELECT value FROM settings WHERE key = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, "indexing_method");
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getString("value");
                    }
                }
            }
        } catch (Exception e) {
            // Could not determine embedding method
        }
        return "unknown";
    }

    // Checks if the index exists for a given docFileName, based on the configured database type
    public boolean indexExists(String docFileName) {
        try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "SELECT 1 FROM vectors WHERE metadata LIKE ? LIMIT 1";
            try (java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, "%\"sourceFile\":\"" + docFileName + "\"%");
                try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (Exception e) {
            LogMessages.logWarning(e.getMessage());
            return false;
        }
    }


    // Returns the file path for a document's index (file-based)
    public String getIndexPath(String docFileName) {
        return this.sqliteDbPath.replace("vectorstore.db", "vectors/" + docFileName + ".index");
    }

    // Loads embeddings for a document (file-based)
    public Map<Chunk, List<Double>> load(String docFileName) throws IOException {
        String indexPath = getIndexPath(docFileName);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<Double>> stringKeyMap = mapper.readValue(new File(indexPath), new TypeReference<Map<String, List<Double>>>() {});
        Map<Chunk, List<Double>> chunkMap = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : stringKeyMap.entrySet()) {
            chunkMap.put(Chunk.fromStorageString(entry.getKey()), entry.getValue());
        }
        return chunkMap;
    }

    // Saves embeddings for a document (file-based)
    public void save(String docFileName, Map<Chunk, List<Double>> embeddings) throws IOException {
        String indexPath = getIndexPath(docFileName);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<Double>> stringKeyMap = embeddings.entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().toStorageString(),
                Map.Entry::getValue
            ));
        mapper.writeValue(new File(indexPath), stringKeyMap);
    }

    // Save embeddings for a document (file-based)
    public void saveDocumentEmbeddings(String docFileName, Map<Chunk, List<Double>> embeddings) throws IOException {
        String indexPath = getIndexPath(docFileName);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<Double>> stringKeyMap = embeddings.entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().toStorageString(),
                Map.Entry::getValue
            ));
        mapper.writeValue(new File(indexPath), stringKeyMap);
    }

    // Load embeddings for a document (file-based)
    public Map<Chunk, List<Double>> loadDocumentEmbeddings(String docFileName) throws IOException {
        String indexPath = getIndexPath(docFileName);
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<Double>> stringKeyMap = mapper.readValue(new File(indexPath), new TypeReference<Map<String, List<Double>>>() {});
        Map<Chunk, List<Double>> chunkMap = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : stringKeyMap.entrySet()) {
            chunkMap.put(Chunk.fromStorageString(entry.getKey()), entry.getValue());
        }
        return chunkMap;
    }

    // Save embeddings for a document (SQLite)
    public void saveDocumentEmbeddingsToSqlite(String docFileName, Map<Chunk, List<Double>> embeddings) throws SQLException, IOException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "INSERT OR REPLACE INTO vectors (chunk_id, vector, metadata) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                ObjectMapper mapper = new ObjectMapper();
                for (Map.Entry<Chunk, List<Double>> entry : embeddings.entrySet()) {
                    pstmt.setString(1, entry.getKey().toStorageString());
                    pstmt.setString(2, mapper.writeValueAsString(entry.getValue()));
                    pstmt.setString(3, mapper.writeValueAsString(entry.getKey())); // store only chunk metadata
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
        }
    }

    // Load embeddings for a document (SQLite)
    public Map<Chunk, List<Double>> loadDocumentEmbeddingsFromSqlite(String docFileName) throws SQLException, IOException {
        Map<Chunk, List<Double>> chunkMap = new HashMap<>();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "SELECT chunk_id, vector, metadata FROM vectors WHERE metadata LIKE ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, "%\"sourceFile\":\"" + docFileName + "\"%");
                try (ResultSet rs = pstmt.executeQuery()) {
                    ObjectMapper mapper = new ObjectMapper();
                    while (rs.next()) {
                        Chunk chunk = mapper.readValue(rs.getString("metadata"), Chunk.class);
                        List<Double> vector = mapper.readValue(rs.getString("vector"), new TypeReference<List<Double>>() {});
                        chunkMap.put(chunk, vector);
                    }
                }
            }
        }
        return chunkMap;
    }

    // Check if index exists for a document (file-based or SQLite)
    public boolean documentIndexExists(String docFileName) {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "SELECT 1 FROM vectors WHERE metadata LIKE ? LIMIT 1";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, "%\"sourceFile\":\"" + docFileName + "\"%");
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (Exception e) {
            LogMessages.logWarning(e.getMessage());
            return false;
        }
    }

    /**
     * Indexes all documents in a folder: loads, chunks, embeds, and saves vectors for each document.
     * Handles both file-based and SQLite storage.
     */
    public void createIndex(String folderPath, EmbeddingService embeddingService, java.util.function.BiConsumer<String, Double> progressCallback) {
        try {
            if (progressCallback != null) progressCallback.accept("Starting indexing for folder: " + folderPath, 0.0);
            org.dirchat.document.DocumentLoader loader = new org.dirchat.document.DocumentLoader();
            List<DocumentLoader.LoadedDocument> documents = loader.loadDocumentsFromFolder(folderPath);
            int totalDocs = documents.size();
            for (int i = 0; i < totalDocs; i++) {
                DocumentLoader.LoadedDocument doc = documents.get(i);
                double percent = ((double) (i + 1)) / totalDocs;
                String barMsg = "Indexing document " + (i + 1) + "/" + totalDocs + ": " + doc.fileName;
                if (progressCallback != null) progressCallback.accept(barMsg, percent);
                List<org.dirchat.model.Chunk> docChunks = embeddingService.textChunker.chunkText(doc.content, doc.fileName, doc.documentId);
                Map<org.dirchat.model.Chunk, List<Double>> embeddings = embeddingService.embedChunks(docChunks, progressCallback);
                saveDocumentEmbeddingsToSqlite(doc.fileName, embeddings);
            }
            if (progressCallback != null) progressCallback.accept("Indexing complete for folder: " + folderPath, 1.0);
        } catch (Exception e) {
            LogMessages.logWarning(e.getMessage());
            if (progressCallback != null) progressCallback.accept("Indexing failed: " + e.getMessage(), 1.0);
        }
    }

    /**
     * Extract and save document metadata using Apache Tika before saving embeddings
     */
    private void extractAndSaveDocumentMetadata(DocumentLoader.LoadedDocument doc) {
        org.apache.tika.Tika tika = new org.apache.tika.Tika();
        org.apache.tika.metadata.Metadata tikaMetadata = new org.apache.tika.metadata.Metadata();
        int numberPages = 0;
        String filetype = "unknown";
        String toc = "";
        String metadataJson = "";
        try {
            tika.parse(new java.io.ByteArrayInputStream(doc.content.getBytes()), tikaMetadata);
            filetype = tikaMetadata.get(org.apache.tika.metadata.Metadata.CONTENT_TYPE);
            String pagesStr = tikaMetadata.get("xmpTPg:NPages");
            if (pagesStr != null) {
                numberPages = Integer.parseInt(pagesStr);
            }
            toc = tikaMetadata.get("toc");
            metadataJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(tikaMetadata);
        } catch (Exception e) {
            LogMessages.logWarning("Failed to extract metadata for " + doc.fileName + ": " + e.getMessage());
        }
        try {
            upsertDocumentMetadata(doc.fileName, toc, numberPages, filetype, metadataJson);
        } catch (Exception e) {
            LogMessages.logWarning("Failed to upsert document metadata for " + doc.fileName + ": " + e.getMessage());
        }
    }

    /**
     * Insert or update document metadata in DOCUMENT table
     */
    public void upsertDocumentMetadata(String filename, String toc, int numberPages, String filetype, String metadataJson) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "INSERT OR REPLACE INTO document (filename, toc, number_pages, filetype, metadata) VALUES (?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setString(1, filename);
                pstmt.setString(2, toc);
                pstmt.setInt(3, numberPages);
                pstmt.setString(4, filetype);
                pstmt.setString(5, metadataJson);
                pstmt.executeUpdate();
            }
        }
    }

    // Save index properties to TABLE_INDEX
    public void saveIndexProperties(Map<String, String> properties) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "INSERT OR REPLACE INTO table_index (property_key, property_value) VALUES (?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                for (Map.Entry<String, String> entry : properties.entrySet()) {
                    pstmt.setString(1, entry.getKey());
                    pstmt.setString(2, entry.getValue());
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
        }
    }

    // Save document metadata to TABLE_DOCUMENT, returns document_id
    public int saveDocumentMetadata(String filename, String location, int numberPages, String filetype, String toc, String metadataJson) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "INSERT OR REPLACE INTO table_document (filename, location, number_pages, filetype, toc, metadata) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setString(1, filename);
                pstmt.setString(2, location);
                pstmt.setInt(3, numberPages);
                pstmt.setString(4, filetype);
                pstmt.setString(5, toc);
                pstmt.setString(6, metadataJson);
                pstmt.executeUpdate();
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
            }
            // If document already exists, fetch its id
            String selectSql = "SELECT id FROM table_document WHERE filename = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(selectSql)) {
                pstmt.setString(1, filename);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("id");
                    }
                }
            }
        }
        throw new SQLException("Failed to save or retrieve document id for " + filename);
    }

    // Save chunk embeddings to TABLE_CHUNK (pointer-based)
    public void saveChunkEmbeddings(int documentId, List<Chunk> chunks, Map<Chunk, List<Double>> embeddings) throws SQLException, IOException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "INSERT INTO table_chunk (document_id, chunk_index, page_number, cursor_pointer, embedding_vector, metadata) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                ObjectMapper mapper = new ObjectMapper();
                for (Chunk chunk : chunks) {
                    List<Double> vector = embeddings.get(chunk);
                    pstmt.setInt(1, chunk.getDocumentId());
                    pstmt.setInt(2, chunk.getChunkIndex());
                    pstmt.setInt(3, chunk.getPageNumber());
                    pstmt.setInt(4, chunk.getCursorPointer());
                    pstmt.setString(5, mapper.writeValueAsString(vector));
                    pstmt.setString(6, mapper.writeValueAsString(chunk)); // metadata as JSON
                    pstmt.addBatch();
                }
                pstmt.executeBatch();
            }
        }
    }

    // Retrieve chunk embeddings and pointers from TABLE_CHUNK
    public List<ScoredChunk> getChunkEmbeddingsWithScores(List<Double> queryVector, int topK) throws SQLException, IOException {
        List<ScoredChunk> allScoredChunks = new ArrayList<>();
        String sql = "SELECT document_id, chunk_index, page_number, cursor_pointer, embedding_vector, metadata FROM table_chunk";
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            try (PreparedStatement stmt = conn.prepareStatement(sql);
                 ResultSet rs = stmt.executeQuery()) {
                ObjectMapper mapper = new ObjectMapper();
                while (rs.next()) {
                    int documentId = rs.getInt("document_id");
                    int chunkIndex = rs.getInt("chunk_index");
                    int pageNumber = rs.getInt("page_number");
                    int cursorPointer = rs.getInt("cursor_pointer");
                    String metadataJson = rs.getString("metadata");
                    Chunk chunk = mapper.readValue(metadataJson, Chunk.class);
                    List<Double> vector = mapper.readValue(rs.getString("embedding_vector"), new TypeReference<List<Double>>() {});
                    double score = cosineSimilarity(queryVector, vector);
                    // Set pointer fields
                    chunk.setContent(null); // content will be reconstructed at runtime
                    allScoredChunks.add(new ScoredChunk(chunk, score));
                }
            }
        }
        return allScoredChunks.stream()
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    public static class ScoredChunk {
        private final Chunk chunk;
        private final double score;
        public ScoredChunk(Chunk chunk, double score) {
            this.chunk = chunk;
            this.score = score;
        }
        public Chunk getChunk() { return chunk; }
        public double getScore() { return score; }
    }

    /*
    =====================================
    Table Management Reference
    =====================================

    - table_index
        - Initialized in: initSqliteDb()
        - Managed by: saveIndexProperties(Map<String, String> properties)

    - table_document
        - Initialized in: initSqliteDb()
        - Managed by: saveDocumentMetadata(), upsertDocumentMetadata()
        - Used as FK in: table_chunk

    - table_chunk
        - Initialized in: initSqliteDb()
        - Managed by: saveChunkEmbeddings(), getChunkEmbeddingsWithScores()
        - Used for: chunk search/retrieval

    - settings
        - Initialized in: initSqliteDb()
        - Managed by: setIndexingMethod(), getIndexingMethod(), getSqliteEmbeddingMethod()

    - vectors (used in code, not in diagram)
        - Managed by: saveToSqlite(), loadFromSqlite(), getVector(), deleteVector(), getAllVectors(), compoundSearchWithScores()
        - Used for: chunk vector storage and search

    All tables are created in initSqliteDb(), which is called during VectorStore construction or when saving to SQLite and the table does not exist.
    */

    public void runIndexing() throws SQLException, IOException
    {
        initSqliteDb();
        setIndexingMethod("sbert"); // Ensure embedding method is set in settings table
        for (int i = 0; i < documents.size(); i++) {
            DocumentLoader.LoadedDocument doc = documents.get(i);
            String docFileName = doc.fileName;
            List<Chunk> docChunks = allDocChunks.get(i);
            Map<Chunk, List<Double>> docEmbeddings = new java.util.HashMap<>();
            for (Chunk chunk : docChunks) {
                if (embeddings.containsKey(chunk)) {
                    docEmbeddings.put(chunk, embeddings.get(chunk));
                }
            }
            // Always use SQLite
            store.saveDocumentEmbeddingsToSqlite(docFileName, docEmbeddings);
            System.out.println("✅ Stored embeddings for " + docFileName + " in SQLite vector store.");
        }
        // Check if index does not exist for the selected folder
        // Load the documentLoader
        // For each document in the documentLoader load the document
        // Chunk the document "Preparing document"
        // Embed the chunks in a thread pool with progress bar


        // Save the document metadata to the index (file-based or SQLite)
        // Save the embeddings to the index (file-based or SQLite)
        // Save the document metadata to the index (SQLite)
        // Save the index properties to the index (SQLite)
        // Update progress bar after each document is processed

        // Else
        // Load the documentLoader first saved in createIndex()
        // Also, if index is created load the documentLoader first and check which documents are missing from the index
        // And throw exception if documents are missing


    }
}
