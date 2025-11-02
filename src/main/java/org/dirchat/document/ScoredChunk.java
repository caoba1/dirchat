package org.dirchat.document;

public class ScoredChunk {
    private final double score;
    private final int documentId;
    private final int chunkId;
    public ScoredChunk(int documentId,int  chunkId, long cursorPointer, double score) {
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.score = score;
    }
    public double getScore() {
        return score;
    }
}
