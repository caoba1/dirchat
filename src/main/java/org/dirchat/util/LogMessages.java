/*
 * dirchat — Java directory chat application.
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 * License: GNU AGPL v3 or later. See LICENSE for details.
 */
package org.dirchat.util;
// dirchat logger

import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;

public class LogMessages {
    private static final Logger logger = Logger.getLogger(LogMessages.class.getName());

    private static org.dirchat.controller.UIController uiController = null;

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

    public static void setUIController(org.dirchat.controller.UIController controller) {
        uiController = controller;
    }

    public static void log(String message) {
        log(message, false);
    }

    public static void log(String message, boolean bypassUI) {
        logger.info(message);
        if (!bypassUI && uiController != null) {
            uiController.setMessageBar(message);
        }
    }

    public static void logWarning(String message) {
        logWarning(message, false);
    }

    public static void logWarning(String message, boolean bypassUI) {
        logger.warning(message);
        if (!bypassUI && uiController != null) {
            uiController.setMessageBar(message);
        }
    }

}
