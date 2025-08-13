// Loads and parses documents using Apache Tika
package org.dirchat.document;

import org.apache.tika.Tika;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

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

    private static final Logger logger = Logger.getLogger(DocumentLoader.class.getName());

    public List<ParsedDocument> loadDocumentsFromFolder(String folderPath) {
        List<ParsedDocument> documents = new ArrayList<>();
        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            String errMsg = "Invalid folder path: " + folderPath;
            System.err.println(errMsg);
            logger.warning(errMsg);
            setMessageBar(errMsg);
            return documents;
        }

        for (File file : folder.listFiles()) {
            if (file.isFile() && isSupported(file)) {
                try {
                    String content = parseDocument(file);
                    documents.add(new ParsedDocument(file.getName(), content));
                    String msg = "Loaded document: " + file.getName();
                    logger.info(msg);
                    setMessageBar(msg);
                } catch (Exception e) {
                    String errMsg = "Failed to parse: " + file.getName();
                    System.err.println(errMsg);
                    logger.warning(errMsg + ", reason: " + e.getMessage());
                    setMessageBar(errMsg);
                    e.printStackTrace();
                }
            }
        }
        return documents;
    }

    private boolean isSupported(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".txt");
    }

    private String parseDocument(File file) throws Exception {
        AutoDetectParser parser = new AutoDetectParser();
        BodyContentHandler handler = new BodyContentHandler(-1);
        Metadata metadata = new Metadata();

        try (FileInputStream stream = new FileInputStream(file)) {
            parser.parse(stream, handler, metadata);
        }

        return handler.toString();
    }
}