/*
 * dirchat — Java directory chat application.
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 * License: GNU AGPL v3 or later. See LICENSE for details.
 */
package org.dirchat.document;

public class ScoredChunk {
    private final double score;
    private final int documentId;
    private final int chunkId;
    private final int pageNumber;
    public ScoredChunk(int documentId, int chunkId, int pageNumber, long cursorPointer, double score) {
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.pageNumber = pageNumber;
        this.score = score;
    }
    public double getScore() {
        return score;
    }
    public int getPageNumber() {
        return pageNumber;
    }
}
