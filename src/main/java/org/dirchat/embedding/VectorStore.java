package org.dirchat.embedding;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.dirchat.model.Chunk;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class VectorStore {

    private final Map<Chunk, List<Double>> store = new HashMap<>();

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

    public void save(Map<Chunk, List<Double>> embeddings, String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<Double>> stringKeyMap = embeddings.entrySet().stream()
            .collect(Collectors.toMap(
                e -> e.getKey().toStorageString(),
                Map.Entry::getValue
            ));
        mapper.writeValue(new File(filePath), stringKeyMap);
    }

    public Map<Chunk, List<Double>> load(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, List<Double>> stringKeyMap = mapper.readValue(new File(filePath), new TypeReference<Map<String, List<Double>>>() {});
        Map<Chunk, List<Double>> chunkMap = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : stringKeyMap.entrySet()) {
            chunkMap.put(Chunk.fromStorageString(entry.getKey()), entry.getValue());
        }
        return chunkMap;
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
