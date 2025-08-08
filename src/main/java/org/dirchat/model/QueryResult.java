// Represents a response with references to source documents
package org.dirchat.model;

import java.util.List;

public class QueryResult {
    private final String answer;
    private final List<Chunk> referencedChunks;

    public QueryResult(String answer, List<Chunk> referencedChunks) {
        this.answer = answer;
        this.referencedChunks = referencedChunks;
    }

    public String getAnswer() {
        return answer;
    }

    public List<Chunk> getReferencedChunks() {
        return referencedChunks;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Answer: ").append(answer).append("\n");
        sb.append("References:\n");
        for (Chunk chunk : referencedChunks) {
            sb.append("- ").append(chunk.getSourceFile())
                    .append(" [#").append(chunk.getIndex()).append("]\n");
        }
        return sb.toString();
    }
}
