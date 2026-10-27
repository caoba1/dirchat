/*
 * dirchat — Java directory chat application.
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 * License: GNU AGPL v3 or later. See LICENSE for details.
 */

package org.dirchat.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public final class Messages {
    private static volatile Locale locale = Locale.ENGLISH;
    private static volatile ResourceBundle bundle = loadBundle(locale);

    private Messages() {
    }

    public static synchronized void setLanguage(String languageCode) {
        Locale requestedLocale = Locale.forLanguageTag(languageCode);
        String language = requestedLocale.getLanguage();
        if (!language.equals("en") && !language.equals("es")
                && !language.equals("de") && !language.equals("fr")) {
            throw new IllegalArgumentException("Unsupported language: " + languageCode);
        }

        ResourceBundle requestedBundle = loadBundle(requestedLocale);
        locale = requestedLocale;
        bundle = requestedBundle;
    }

    public static ResourceBundle getBundle() {
        return bundle;
    }

    public static Locale getLocale() {
        return locale;
    }

    public static String get(String key) {
        return bundle.getString(key);
    }

    public static String format(String key, Object... arguments) {
        return new MessageFormat(get(key), locale).format(arguments);
    }

    private static ResourceBundle loadBundle(Locale locale) {
        String language = locale.getLanguage();
        String resourceName = language.equals("en")
                ? "/messages.properties"
                : "/messages_" + language + ".properties";
        try (InputStream stream = Messages.class.getResourceAsStream(resourceName)) {
            if (stream == null) {
                throw new IllegalStateException("Missing message bundle: " + resourceName);
            }
            return new PropertyResourceBundle(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Could not load message bundle: " + resourceName, e);
        }
    }
}
