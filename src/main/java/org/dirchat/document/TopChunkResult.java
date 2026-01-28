/*
 * dirchat — Java directory chat application.
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 * License: GNU AGPL v3 or later. See LICENSE for details.
 */
package org.dirchat.document;

public class TopChunkResult {
    private final int documentId;
    private final int chunkId;
    private final int pageNumber;
    private final double score;
    public TopChunkResult(int documentId, int chunkId, int pageNumber, double score) {
        this.documentId = documentId;
        this.chunkId = chunkId;
        this.pageNumber = pageNumber;
        this.score = score;
    }
    public int getDocumentId() {
        return documentId;
    }
    public int getChunkId() {
        return chunkId;
    }
    public int getPageNumber() {
        return pageNumber;
    }
    public double getScore() {
        return score;
    }
}
