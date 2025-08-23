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

