package org.dirchat.document;

public class ParsedDocument {
    private final String fileName;
    private final String content;

    public ParsedDocument(String fileName, String content) {
        this.fileName = fileName;
        this.content = content;
    }

    public String getFileName() {
        return fileName;
    }

    public String getContent() {
        return content;
    }
}
