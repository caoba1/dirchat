/*
 * dirchat — Java directory chat application.
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 * License: GNU AGPL v3 or later. See LICENSE for details.
 */

// Loads and parses documents using Apache Tika
package org.dirchat.document;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDDocumentOutline;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;

import org.dirchat.util.LogMessages;
import org.dirchat.util.Dirchat;

import org.xml.sax.ContentHandler;


import org.apache.tika.metadata.Metadata;
import org.apache.tika.Tika;
import org.apache.tika.exception.WriteLimitReachedException;
import org.apache.tika.metadata.*;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;

import java.io.FileInputStream;
import java.io.IOException;
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
            LogMessages.logWarning("Could not enumerate the requested directory.", e);
        }
    }

    private final Tika tika = new Tika(); //Todo: are we using Tika or other stuff?

    public static class LoadedDocument {
        private final int documentId; //unique accross documents. from path.hashCode()
        public final Metadata metadata = new Metadata(); // org.apache.tika.metadata.Metadata
        public final Path absolutePath;
        public String toc;
        private boolean indexed = false;
        private int numberOfPages = 0;// Whether this document has been indexed
        private double score = 0.0;

        public LoadedDocument(String absolutePath, boolean extractMetadata) {
            this.absolutePath = Path.of(absolutePath);
            this.documentId = this.absolutePath.toString().hashCode();
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
            this.absolutePath = null; // for loading XXX from DB
            this.toc = null;
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
        public Path getPath() { return absolutePath; }
        private boolean extractMetadata() {

            ContentHandler handler = new BodyContentHandler(1);
            AutoDetectParser parser = new AutoDetectParser();
            ParseContext context = new ParseContext();

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(() -> {
                try (InputStream stream = Files.newInputStream(absolutePath)) {
                    parser.parse(stream, handler, metadata, context);
                } catch (WriteLimitReachedException e) {
                    LogMessages.debug("Document text extraction reached its configured write limit.");
                } catch (Exception e) {
                    LogMessages.logWarning("Could not extract document text.", e);
                }
            });

            try {
                future.get(10, TimeUnit.SECONDS); // 10 second timeout
            } catch (TimeoutException e) {
                future.cancel(true);
                LogMessages.logWarning("Document parsing timed out.", e);
                executor.shutdownNow();
                return false;
            } catch (Exception e) {
                executor.shutdownNow();
                LogMessages.logWarning("Document parsing failed.", e);
                return false;
            }
            executor.shutdown();

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


        public long getCharCount() {
            try {
                return (long) (Files.size(absolutePath)/1.5);
            } catch (IOException e) {
                LogMessages.logWarning("Could not estimate document character count.", e);
                return 0L;
            }
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
                LogMessages.logWarning("Could not extract the PDF table of contents.", e);
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