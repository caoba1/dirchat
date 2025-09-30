// Loads and parses documents using Apache Tika
package org.dirchat.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.dirchat.util.LogMessages;
import org.dirchat.util.Dirchat;

//import java.io.File;
//import java.io.FileInputStream;
import java.io.FileInputStream;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

public class DocumentLoader {
    private List<Path>  listFiles;
    private final Path folderPath;

    public DocumentLoader(String folderPathStr){
        super();
        folderPath = Paths.get(folderPathStr);
        try{
            if (Files.exists(folderPath) && Files.isDirectory(folderPath)) {
                listFiles = Files.walk(folderPath)
                        .filter(path -> !Dirchat.isInIndexDirectory(path))
                        .filter(path -> Dirchat.safeIsRegularFile(path))
                        .toList();
            }
            else
            {
                throw new IllegalArgumentException("Folder path does not exist or is not a directory: " + folderPathStr);
            }
        }
        catch (Exception e) {
            LogMessages.logWarning("DocumentLoader: Folder path does not exist or is not a directory: " + folderPathStr);
        }
    }

    private final Tika tika = new Tika(); //Todo: are we using Tika or other stuff?
    // Allows the UI to register a callback for message bar updates.
    // This decouples backend logic from the UI, so DocumentLoader can notify the user without knowing about UI classes.
    private static java.util.function.Consumer<String> messageBarSetter;

    public static class LoadedDocument {
        public final int documentId;
        public final String fileName;
        public final String content;
        public Metadata metadata; // Now mutable
        public final String absolutePath;
        public String toc;
        private boolean indexed = false; // Whether this document has been indexed
        public LoadedDocument(int documentId, String fileName, String content, String absolutePath) {
            this.documentId = documentId;
            this.fileName = fileName;
            this.content = content;
            this.absolutePath = absolutePath;
            this.toc = null;
            this.metadata = new Metadata(); // Initialize with empty metadata
        }
        public void setMetadata(Metadata metadata) {
            this.metadata = metadata;
        }
        public void setIntexed(boolean indexed) {
            this.indexed = indexed;
        }
        public boolean isIndexed() {
            return indexed;
        }
        public int getDocumentId() {
            return documentId;
        }
        public String getFileName() {
            return fileName;
        }
        public String getContent() {
            return content;
        }
        public Metadata getMetadata() {
            return metadata;
        }
        public String getAbsolutePath() {
            return absolutePath;
        }
        public String getIdString(){
            return String.valueOf(documentId);
        }
        public int getNumberPages(){
            return documentId;
        }
        public String getFileType() {
            int dotIndex = fileName.lastIndexOf('.');
            if (dotIndex != -1 && dotIndex < fileName.length() - 1) {
                return fileName.substring(dotIndex + 1);
            }
            return ""; // No extension found
        }
        public String getToc() {
            return toc;
        }

    }

    public List<LoadedDocument> loadDocumentListFromFolder() {
        List<LoadedDocument> documents = new ArrayList<>();
        if ( listFiles.isEmpty()) {
            String errMsg = "Invalid folder path: " + folderPath;
            System.err.println(errMsg);
            LogMessages.logWarning(errMsg);
            return documents;
        }
        for (Path path : listFiles) {
            if (Files.exists(path) && Dirchat.isSupported(path)) {
                try {
                    Metadata metadata = new Metadata();
                    String content = parseDocumentWithMetadata(path, metadata);
                    int documentId = path.toAbsolutePath().toString().hashCode(); // IMPORTANT

                    // TODO: Add toc and other metadata information. This information is also stored in the vector store.
                    LoadedDocument loadedDoc = new LoadedDocument(
                            documentId,
                            path.getFileName().toString(),
                            content,
                            path.toAbsolutePath().toString());

                    loadedDoc.setMetadata(metadata); // Set metadata after document creation
                    // Extract and save document metadata (including TOC) before embedding

                    //TODO vectorStore.extractAndSaveDocumentMetadata(loadedDoc);



                    documents.add(loadedDoc);
                    String msg = "Document loaded in list of LoadedDocuments: " + path.getFileName().toString();
                    LogMessages.log(msg);
                } catch (org.dirchat.util.SqliteErrorException se) {
                    String errMsg = "SQLITE error parsing: " + path.getFileName().toString();
                    System.err.println(errMsg);
                    LogMessages.logWarning(errMsg + ", reason: " + se.getMessage());
                    se.printStackTrace();
                    if (messageBarSetter != null) messageBarSetter.accept("SQLITE error: " + se.getMessage());
                } catch (Exception e) {
                    String errMsg = "Failed to parse: " + path.getFileName().toString();
                    System.err.println(errMsg);
                    LogMessages.logWarning(errMsg + ", reason: " + e.getMessage());
                    e.printStackTrace();
                    if (messageBarSetter != null) messageBarSetter.accept("Error: " + e.getMessage());
                }
            }
        }
        return documents;
    }



    /** Document parser **/
    private String parseDocumentWithMetadata(Path filePath, Metadata metadata) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        String toc = "";
        try (FileInputStream stream = new FileInputStream(filePath.toFile())) {
            parser.parse(stream, handler, metadata);
        }
        String name = filePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".pdf")) {
            toc = extractPdfToc(filePath);
        } else if (name.endsWith(".docx")) {
            toc = extractDocxToc(filePath);
        } else if (name.endsWith(".txt")) {
            toc = extractTxtToc(filePath);
        }
        metadata.set("toc", toc);
        return handler.toString();
    }

    private String extractPdfToc(Path filePath) {
        StringBuilder sb = new StringBuilder();
        try (PDDocument document = PDDocument.load(filePath.toFile())) {
            PDDocumentOutline outline = document.getDocumentCatalog().getDocumentOutline();
            if (outline != null) {
                PDOutlineItem current = outline.getFirstChild();
                int depth = 0;
                while (current != null) {
                    appendOutlineItem(sb, current, depth);
                    current = current.getNextSibling();
                }
            } else {
                sb.append("[No TOC/bookmarks found]");
            }
        } catch (Exception e) {
            sb.append("[Error extracting PDF TOC: ").append(e.getMessage()).append("]");
            LogMessages.logWarning("Error extracting PDF TOC: " + e.getMessage());
        }
        return sb.toString();
    }

    private void appendOutlineItem(StringBuilder sb, PDOutlineItem item, int depth) {
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append("- ").append(item.getTitle());
        // Try to get the page number from the destination
        try {
            if (item.getDestination() != null && item.getDestination() instanceof org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination) {
                org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination pageDest = (org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination) item.getDestination();
                sb.append(" (Page: ").append(pageDest.getPageNumber()).append(")");
            }
        } catch (java.io.IOException e) {
            sb.append(" (Page: [error: ").append(e.getMessage()).append("])");
        }
        sb.append("\n");
        PDOutlineItem child = item.getFirstChild();
        while (child != null) {
            appendOutlineItem(sb, child, depth + 1);
            child = child.getNextSibling();
        }
    }

    private String extractDocxToc(Path filePath) {
        return "[TOC extraction for DOCX not implemented]";
    }

    private String extractTxtToc(Path filePath) {
        return "[TOC extraction for TXT not implemented]";
    }
}