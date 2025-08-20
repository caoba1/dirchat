package org.dirchat.util;

import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

public class LogMessages {
    public static final Logger logger = Logger.getLogger(LogMessages.class.getName());
    static {
        try {
            FileHandler fh = new FileHandler("dirchat.log", true);
            fh.setFormatter(new SimpleFormatter());
            logger.addHandler(fh);
            logger.setUseParentHandlers(true);
        } catch (Exception e) {
            System.err.println("Failed to set up logger in LogMessages: " + e.getMessage());
        }
    }
    // Log message keys for messages.properties
    public static final String LOADING_FOLDER = "LOADING_FOLDER";
    public static final String LOADED_EMBEDDINGS = "LOADED_EMBEDDINGS";
    public static final String GENERATED_INDEX = "GENERATED_INDEX";
    public static final String QUESTION = "QUESTION";
    public static final String ANSWER = "ANSWER";
    public static final String FAILED_TO_LOAD_INDEX = "FAILED_TO_LOAD_INDEX";
    public static final String FAILED_TO_SAVE_INDEX = "FAILED_TO_SAVE_INDEX";
    public static final String DOCUMENTS_INDEXED = "DOCUMENTS_INDEXED";
}
