// Loads and parses documents using Apache Tika
package org.dirchat.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.tika.Tika;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.dirchat.util.LogMessages;
import org.dirchat.util.Dirchat;
import org.xml.sax.ContentHandler;

//import java.io.File;
//import java.io.FileInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

// This file uses Apache Tika to load and parse documents from a specified folder.
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
        private final int documentId; //unique accross documents. from path.hashCode()
        public Metadata metadata; // Now mutable. why? shouldnt we be able to set it at creation only?
        public final Path absolutePath;
        public String toc;
        private boolean indexed = false;
        private int numberOfPages = 0;// Whether this document has been indexed
        private double score = 0.0;

        public LoadedDocument(String absolutePath, boolean extractMetadata) {
            this.absolutePath = Path.of(absolutePath);
            this.documentId = this.absolutePath.toString().hashCode();
            this.metadata = null;
            this.toc = null;
            if (extractMetadata)
            {
                extractMetadata(); // org.apache.tika.metadata.Metadata: Initialize with empty metadata
                extractToc();
            }
        }

        // overloaded? constructor for loading the documents
        public LoadedDocument(int documentId) {
            this.documentId = documentId;
            this.absolutePath = null;
            this.toc = null;
            this.metadata = null;
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
            return absolutePath.getFileName().toString();
        }
        public Metadata getMetadata() {
            return metadata;
        }
        private boolean extractMetadata() {
            Metadata metadata = new Metadata();
            ContentHandler handler = new BodyContentHandler(1);
            AutoDetectParser parser = new AutoDetectParser();
            ParseContext context = new ParseContext();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(() -> {
                try (InputStream stream = new FileInputStream(absolutePath.toFile())) {
                    parser.parse(stream, handler, metadata, context);
                } catch (WriteLimitReachedException e) {
                    System.out.println("Content extraction stopped after 1 byte (expected behavior).");
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            try {
                future.get(10, TimeUnit.SECONDS); // 10 second timeout
            } catch (TimeoutException e) {
                future.cancel(true);
                System.err.println("Parsing timed out.");
                LogMessages.logWarning("Parsing timed out for: " + absolutePath);
                executor.shutdownNow();
                return false;
            } catch (Exception e) {
                executor.shutdownNow();
                e.printStackTrace();
                return false;
            }
            executor.shutdown();
            this.metadata = metadata;
            return true;
        }

        private boolean extractToc() {
            switch (getFileType().toLowerCase()) {
                case "pdf":
                    extractPdfToc();
                    break;
                case "docx":
                    extractDocxToc();
                    break;
                case "txt":
                    extractTxtToc();
                    break;
                default:
                    this.toc = "No TOC available for this file type.";
                    return false;
            }
            return true;
        }

        public String getAbsolutePath() {
            return absolutePath.toAbsolutePath().toString();
        }
        public String getIdString(){
            return String.valueOf(documentId);
        }
        public int getNumberPages(){
            return numberOfPages;
        }
        public String getFileType() {
            String fileType = Arrays.stream(absolutePath.getFileName().toString().split("\\."))
                            .reduce( (first, second) -> {return second;} )
                            .orElse("");
            return fileType;
        }
        public String getToc() {
            return toc;
        }
        public void setScore(double score) {
            this.score = score;
        }
        public double getScore() {
            return this.score;
        }

        private boolean extractPdfToc() {
            StringBuilder sb = new StringBuilder();
            // todo: remove usage of file
            try (PDDocument document = PDDocument.load(absolutePath.toFile())) {
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
                // Not part of, but done here because document is open
                int numPages = document.getNumberOfPages();
                this.numberOfPages = numPages;
            } catch (Exception e) {
                sb.append("[Error extracting PDF TOC: ").append(e.getMessage()).append("]");
                LogMessages.logWarning("Error extracting PDF TOC: " + e.getMessage());
            }
            this.toc = sb.toString();
            return true;
        }
    }


    private static void appendOutlineItem(StringBuilder sb, PDOutlineItem item, int depth) {
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

    private static String extractDocxToc() {
        return "[TOC extraction for DOCX not implemented]";
    }

    private static String extractTxtToc() {
        return "[TOC extraction for TXT not implemented]";
    }
}