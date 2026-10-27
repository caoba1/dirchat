/*
 * dirchat — Java directory chat application.
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 * License: GNU AGPL v3 or later. See LICENSE for details.
 */
package org.dirchat.util;
// dirchat logger

import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.SimpleFormatter;
import java.io.IOException;
import java.util.Locale;

public class LogMessages {
    private static final Logger logger = Logger.getLogger(LogMessages.class.getName());

    static {
        logger.setLevel(configuredLevel());
        try {
            FileHandler fh = new FileHandler("dirchat.log", true);
            fh.setFormatter(new SimpleFormatter());
            fh.setLevel(Level.ALL);
            logger.addHandler(fh);
            logger.setUseParentHandlers(true);
        } catch (IOException e) {
            Logger.getLogger(LogMessages.class.getName())
                    .log(Level.WARNING, "Could not initialize the file logger.", e);
        }
    }

    private static Level configuredLevel() {
        String value = System.getProperty("dirchat.log.level", "INFO").trim().toUpperCase(Locale.ROOT);
        if ("DEBUG".equals(value)) {
            return Level.FINE;
        }
        if ("TRACE".equals(value)) {
            return Level.FINER;
        }
        try {
            return Level.parse(value);
        } catch (IllegalArgumentException e) {
            logger.warning("Invalid dirchat.log.level value; using INFO.");
            return Level.INFO;
        }
    }

    public static void debug(String message) {
        logger.fine(message);
    }

    public static void log(String message) {
        logger.info(message);
    }

    public static void logWarning(String message) {
        logger.warning(message);
    }

    public static void logWarning(String message, Throwable error) {
        logger.log(Level.WARNING, message, error);
    }

    public static void logError(String message, Throwable error) {
        logger.log(Level.SEVERE, message, error);
    }

}
