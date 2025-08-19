package org.dirchat.embedding;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dirchat.model.Chunk;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class VectorStore {

    private final Map<Chunk, List<Double>> store = new HashMap<>();

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

    // Initialize SQLite DB and create table if not exists
    public void initSqliteDb() throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "CREATE TABLE IF NOT EXISTS vectors ("
                    + "chunk_id TEXT PRIMARY KEY,"
                    + "vector TEXT NOT NULL,"
                    + "metadata TEXT NOT NULL"
                    + ")";
            String settingsSql = "CREATE TABLE IF NOT EXISTS settings ("
                    + "key TEXT PRIMARY KEY,"
                    + "value TEXT NOT NULL"
                    + ")";
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
                stmt.execute(settingsSql);
            }
        }
    }

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

    public void addAll(Map<Chunk, List<Double>> embeddings) {
        store.putAll(embeddings);
    }

    public List<Chunk> search(List<Double> queryVector, int topK) {
        return store.entrySet().stream()
                .map(entry -> new ScoredChunk(entry.getKey(), cosineSimilarity(queryVector, entry.getValue())))
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .limit(topK)
                .map(ScoredChunk::getChunk)
                .collect(Collectors.toList());
    }

    public List<ScoredChunk> searchWithScores(List<Double> queryVector, int topK) {
        return store.entrySet().stream()
                .map(entry -> new ScoredChunk(entry.getKey(), cosineSimilarity(queryVector, entry.getValue())))
                .sorted(Comparator.comparingDouble(ScoredChunk::getScore).reversed())
                .limit(topK)
                .collect(Collectors.toList());
    }

    private double cosineSimilarity(List<Double> a, List<Double> b) {
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            dot += a.get(i) * b.get(i);
            normA += a.get(i) * a.get(i);
            normB += b.get(i) * b.get(i);
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
        // Check if 'vectors' table exists, and initialize DB if missing
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String checkTableSql = "SELECT name FROM sqlite_master WHERE type='table' AND name='vectors'";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(checkTableSql)) {
                if (!rs.next()) {
                    // Table does not exist, initialize DB
                    initSqliteDb();
                }
            }
        }
        // Now proceed with saving
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
            String sql = "INSERT OR REPLACE INTO vectors (chunk_id, vector, metadata) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                ObjectMapper mapper = new ObjectMapper();
                for (Map.Entry<Chunk, List<Double>> entry : embeddings.entrySet()) {
                    pstmt.setString(1, entry.getKey().toStorageString());
                    pstmt.setString(2, mapper.writeValueAsString(entry.getValue()));
                    pstmt.setString(3, mapper.writeValueAsString(entry.getKey())); // store metadata as JSON
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

    // Load all vectors from SQLite into memory
    public void loadAllFromSqliteToMemory() throws SQLException, IOException {
        Map<Chunk, List<Double>> loaded = loadFromSqlite();
        store.clear();
        store.putAll(loaded);
    }

    // Save all vectors from memory to SQLite
    public void saveAllFromMemoryToSqlite() throws SQLException, IOException {
        saveToSqlite(store);
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
            String sql = "SELECT metadata FROM vectors LIMIT 1";
            try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    String metadataJson = rs.getString("metadata");
                    // Assume metadata contains embedding method info (extend Chunk if needed)
                    if (metadataJson.contains("sbert")) {
                        return "sbert";
                    } else if (metadataJson.contains("ollama")) {
                        return "ollama";
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
        String dbType = org.dirchat.util.ConfigManager.getDatabaseType();
        if (dbType.equalsIgnoreCase("file")) {
            String indexPath = this.sqliteDbPath.replace("vectorstore.db", "vectors/" + docFileName + ".index");
            return new java.io.File(indexPath).exists();
        } else {
            // For SQLite, check if any chunk for this docFileName exists in the DB
            try (java.sql.Connection conn = java.sql.DriverManager.getConnection("jdbc:sqlite:" + sqliteDbPath)) {
                String sql = "SELECT 1 FROM vectors WHERE metadata LIKE ? LIMIT 1";
                try (java.sql.PreparedStatement pstmt = conn.prepareStatement(sql)) {
                    pstmt.setString(1, "%\"sourceFile\":\"" + docFileName + "\"%");
                    try (java.sql.ResultSet rs = pstmt.executeQuery()) {
                        return rs.next();
                    }
                }
            } catch (Exception e) {
                return false;
            }
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
}
