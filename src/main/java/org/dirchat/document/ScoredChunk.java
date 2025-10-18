package org.dirchat.document;

public class ScoredChunk extends Chunk {
    private final double score;
    public ScoredChunk(Chunk chunk, double score) {
        super(chunk);
        this.score = score;
    }
    public double getScore() {
        return score;
    }
}
