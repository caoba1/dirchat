/*
 * dirchat — Java directory chat application.
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 * License: GNU AGPL v3 or later. See LICENSE for details.
 */

// Represents a response with references to source documents
package org.dirchat.model;

import org.dirchat.document.Chunk;

import java.util.List;

public class QueryResult {
    private final String answer;
    private final List<Chunk> referencedChunks;

    private final Chunk chunk;
    private final double score;

    public QueryResult(String answer, List<Chunk> referencedChunks) {
        this.answer = answer;
        this.referencedChunks = referencedChunks;
        this.chunk = null;
        this.score = 0.0;
    }

    public QueryResult(Chunk chunk, double score) {
        this.answer = null;
        this.referencedChunks = null;
        this.chunk = chunk;
        this.score = score;
    }

    public String getAnswer() {
        return answer;
    }

    public List<Chunk> getReferencedChunks() {
        return referencedChunks;
    }

    public Chunk getChunk() {
        return chunk;
    }

    public double getScore() {
        return score;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Answer: ").append(answer).append("\n");
        sb.append("References:\n");
        for (Chunk chunk : referencedChunks) {
            sb.append("- ").append(chunk.getSourceFile())
                    .append(" [#").append(chunk.getChunkIndex()).append("]\n");
        }
        return sb.toString();
    }
}
