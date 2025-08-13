package org.dirchat.util;

import org.dirchat.model.Chunk;
import org.dirchat.model.QueryResult;
import java.util.*;

public class DocumentSimilarity {
    /**
     * Ranks documents by average of their top N similarity scores.
     * @param results List of QueryResult, each with a chunk and a similarity score
     * @param topN Number of top scores to average per document
     * @return List of document names and their average similarity, sorted descending
     */
    public static List<Map.Entry<String, Double>> rankByAverageSimilarity(List<QueryResult> results, int topN) {
        Map<String, List<Double>> docScores = new HashMap<>();
        for (QueryResult result : results) {
            String docName = result.getChunk().getSourceFile();
            docScores.computeIfAbsent(docName, k -> new ArrayList<>()).add(result.getScore());
        }
        Map<String, Double> docAvg = new HashMap<>();
        for (Map.Entry<String, List<Double>> entry : docScores.entrySet()) {
            List<Double> scores = entry.getValue();
            scores.sort(Comparator.reverseOrder());
            double avg = scores.stream().limit(topN).mapToDouble(Double::doubleValue).average().orElse(0.0);
            docAvg.put(entry.getKey(), avg);
        }
        List<Map.Entry<String, Double>> sortedDocs = new ArrayList<>(docAvg.entrySet());
        sortedDocs.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        return sortedDocs;
    }
}
