// Stores metadata like filename, page number, etc.
package org.dirchat.document;

public class DocumentMetadata {
    private final String fileName;
    private final Integer pageNumber;
    private final Integer chunkIndex;

    public DocumentMetadata(String fileName, Integer pageNumber, Integer chunkIndex) {
        this.fileName = fileName;
        this.pageNumber = pageNumber;
        this.chunkIndex = chunkIndex;
    }

    public String getFileName() {
        return fileName;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    @Override
    public String toString() {
        return "DocumentMetadata [" +
                "fileName='" + fileName + '\'' +
                ", pageNumber=" + pageNumber +
                ", chunkIndex=" + chunkIndex +
                "]";
    }


}
