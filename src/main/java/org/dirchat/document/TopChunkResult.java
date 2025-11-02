package org.dirchat.document;

public class TopChunkResult {
    private final int documentId;
    private final int chunkId;
    private final double score;

    public TopChunkResult(int documentId, int chunkId, double score) {
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.score = score;
    }

    public int getDocumentId() {
        return documentId;
    }

    public int getChunkId() {
        return chunkId;
    }

    public double getScore() {
        return score;
    }
}

