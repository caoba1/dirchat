/*
 * dirchat — Java directory chat application.
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 * License: GNU AGPL v3 or later. See LICENSE for details.
 */
package org.dirchat.util;

public class SqliteErrorException extends Exception {
    // Custom implementation of an exception for SQLite errors
    public SqliteErrorException(String message) {
        super(message);
    }
    public SqliteErrorException(String message, Throwable cause) {
        super(message, cause);
    }
}
