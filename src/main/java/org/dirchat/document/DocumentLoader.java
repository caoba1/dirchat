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

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;

public class DocumentLoader {

    private final Tika tika = new Tika();
    // Allows the UI to register a callback for message bar updates.
    // This decouples backend logic from the UI, so DocumentLoader can notify the user without knowing about UI classes.
    private static java.util.function.Consumer<String> messageBarSetter;
    public static void setMessageBarSetter(java.util.function.Consumer<String> setter) {
        messageBarSetter = setter;
    }
    // Use this to send a message to the UI message bar (if registered)
    private void setMessageBar(String msg) {
        if (messageBarSetter != null) messageBarSetter.accept(msg);
    }

    public static class LoadedDocument {
        public final int documentId;
        public final String fileName;
        public final String content;
        public final Metadata metadata;
        public final String absolutePath;
        public LoadedDocument(int documentId, String fileName, String content, Metadata metadata, String absolutePath) {
            this.documentId = documentId;
            this.fileName = fileName;
            this.content = content;
            this.metadata = metadata;
            this.absolutePath = absolutePath;
        }
    }

    public List<LoadedDocument> loadDocumentsFromFolder(String folderPath) {
        List<LoadedDocument> documents = new ArrayList<>();
        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            String errMsg = "Invalid folder path: " + folderPath;
            System.err.println(errMsg);
            LogMessages.logger.warning(errMsg);
            setMessageBar(errMsg);
            return documents;
        }

        for (File file : folder.listFiles()) {
            if (file.isFile() && isSupported(file)) {
                try {
                    Metadata metadata = new Metadata();
                    String content = parseDocumentWithMetadata(file, metadata);
                    int documentId = file.getAbsolutePath().hashCode();
                    documents.add(new LoadedDocument(documentId, file.getName(), content, metadata, file.getAbsolutePath()));
                    String msg = "Loaded document: " + file.getName();
                    LogMessages.logger.info(msg);
                    setMessageBar(msg);
                } catch (Exception e) {
                    String errMsg = "Failed to parse: " + file.getName();
                    System.err.println(errMsg);
                    LogMessages.logger.warning(errMsg + ", reason: " + e.getMessage());
                    setMessageBar(errMsg);
                    e.printStackTrace();
                }
            }
        }
        return documents;
    }

    /** Available supported files **/
    private boolean isSupported(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".txt");
    }

    /** Document parser **/
    private String parseDocumentWithMetadata(File file, Metadata metadata) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        String toc = "";
        try (FileInputStream stream = new FileInputStream(file)) {
            parser.parse(stream, handler, metadata);
        }
        String name = file.getName().toLowerCase();
        if (name.endsWith(".pdf")) {
            toc = extractPdfToc(file);
        } else if (name.endsWith(".docx")) {
            toc = extractDocxToc(file);
        } else if (name.endsWith(".txt")) {
            toc = extractTxtToc(file);
        }
        metadata.set("toc", toc);
        return handler.toString();
    }

    private String extractPdfToc(File file) {
        StringBuilder sb = new StringBuilder();
        try (PDDocument document = PDDocument.load(file)) {
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
        }
        return sb.toString();
    }

    private void appendOutlineItem(StringBuilder sb, PDOutlineItem item, int depth) {
        for (int i = 0; i < depth; i++) sb.append("  ");
        sb.append("- ").append(item.getTitle());
        if (item.getDestination() != null) {
            sb.append(" (Page: ").append(item.findDestinationPageNumber()).append(")");
        }
        sb.append("\n");
        PDOutlineItem child = item.getFirstChild();
        while (child != null) {
            appendOutlineItem(sb, child, depth + 1);
            child = child.getNextSibling();
        }
    }

    private String extractDocxToc(File file) {
        return "[TOC extraction for DOCX not implemented]";
    }

    private String extractTxtToc(File file) {
        return "[TOC extraction for TXT not implemented]";
    }
}