// dirchat - A chat application
// Copyright (C) 2026 Carlos de Obaldia Pastor
// SPDX-License-Identifier: AGPL-3.0-or-later

package org.dirchat.util;

import java.io.FileNotFoundException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.file.AccessDeniedException;
import java.nio.file.NoSuchFileException;
import java.sql.SQLException;

/** Builds user-facing, localized failure messages from exceptions. */
public final class ErrorMessages {

    private ErrorMessages() {
    }

    /** Returns "context reason (technical detail)"; reason and detail are omitted when unknown. */
    public static String describe(Throwable error, String contextKey) {
        StringBuilder text = new StringBuilder(Messages.get(contextKey));
        String reasonKey = reasonKey(error);
        if (reasonKey != null) {
            text.append(' ').append(Messages.get(reasonKey));
        }
        String technical = technicalDetail(error);
        if (!technical.isEmpty()) {
            text.append(' ').append(Messages.format("error.detail", technical));
        }
        return text.toString();
    }

    private static String reasonKey(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof ConnectException || t instanceof UnknownHostException) {
                return "error.cause.connection";
            }
            if (t instanceof SocketTimeoutException) {
                return "error.cause.timeout";
            }
            if (t instanceof NoSuchFileException || t instanceof FileNotFoundException) {
                return "error.cause.file-not-found";
            }
            if (t instanceof AccessDeniedException || t instanceof SecurityException) {
                return "error.cause.access-denied";
            }
            if (t instanceof SQLException) {
                return "error.cause.database";
            }
            if (t instanceof org.apache.tika.exception.TikaException) {
                return "error.cause.document";
            }
            if (t instanceof OutOfMemoryError) {
                return "error.cause.out-of-memory";
            }
        }
        return null;
    }

    private static String technicalDetail(Throwable error) {
        Throwable root = error;
        while (root != null && root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        if (root == null) {
            return "";
        }
        String message = root.getMessage();
        return root.getClass().getSimpleName() + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
