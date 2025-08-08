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

public class DocumentLoader {

    private final Tika tika = new Tika();

    public List<ParsedDocument> loadDocumentsFromFolder(String folderPath) {
        List<ParsedDocument> documents = new ArrayList<>();
        File folder = new File(folderPath);

        if (!folder.exists() || !folder.isDirectory()) {
            System.err.println("Invalid folder path: " + folderPath);
            return documents;
        }

        for (File file : folder.listFiles()) {
            if (file.isFile() && isSupported(file)) {
                try {
                    String content = parseDocument(file);
                    documents.add(new ParsedDocument(file.getName(), content));
                } catch (Exception e) {
                    System.err.println("Failed to parse: " + file.getName());
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