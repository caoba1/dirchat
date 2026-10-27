/*
 * dirchat — Java directory chat application.
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 * License: GNU AGPL v3 or later. See LICENSE for details.
 */

package org.dirchat.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class ConfigManager {
    public static final String VERSION = "0.1.0-alpha";
    public static final String NAME = "dirchat";
    public static final String DIRECTORY = ".dirchat";

    private static final Map<String, String> LEGACY_KEYS = Map.of(
            "indexing.paragraphsPerChunk", "indexing.paragraphs-per-chunk",
            "indexing.paragraphOverlap", "indexing.overlap-characters",
            "indexing.ollama.url", "models.ollama.base-url",
            "indexing.topK", "retrieval.top-k",
            "indexing.chunkCharacterLimit", "indexing.chunk-character-limit",
            "indexing.minScore", "retrieval.minimum-score",
            "queryAnswer", "chat.answers-enabled",
            "output.mode", "augmentation.output-mode",
            "app.language", "ui.language",
            "indexing.deleteIndexOnLoad", "indexing.regenerate-index-on-load");

    private static final Properties properties = loadProperties();

    public record Settings(
            int paragraphsPerChunk,
            int overlapCharacters,
            String ollamaBaseUrl,
            int topK,
            int chunkCharacterLimit,
            double minScore,
            boolean queryAnswer,
            String outputMode,
            String indexingMethod,
            String language,
            boolean deleteIndexOnLoad) {
        public Settings {
            if (paragraphsPerChunk < 1) {
                throw new ConfigValidationException("config.validation.paragraphs");
            }
            if (chunkCharacterLimit < 1) {
                throw new ConfigValidationException("config.validation.chunk-limit");
            }
            if (overlapCharacters < 0 || overlapCharacters >= chunkCharacterLimit) {
                throw new ConfigValidationException("config.validation.overlap");
            }
            if (topK < 1) {
                throw new ConfigValidationException("config.validation.top-k");
            }
            if (!Double.isFinite(minScore)) {
                throw new ConfigValidationException("config.validation.minimum-score");
            }

            String trimmedUrl = ollamaBaseUrl == null ? "" : ollamaBaseUrl.trim();
            try {
                URI uri = URI.create(trimmedUrl);
                String scheme = uri.getScheme();
                if (scheme == null
                        || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))
                        || uri.getHost() == null) {
                    throw new IllegalArgumentException();
                }
            } catch (IllegalArgumentException e) {
                throw new ConfigValidationException("config.validation.ollama-url");
            }
            ollamaBaseUrl = trimmedUrl;

            if (outputMode == null || !Set.of("truncate", "summarize", "none").contains(outputMode)) {
                throw new ConfigValidationException("config.validation.output-mode");
            }
            if (indexingMethod == null || !Set.of("default", "sbert", "ollama").contains(indexingMethod)) {
                throw new ConfigValidationException("config.validation.indexing-method");
            }
            if (language == null || !Set.of("en", "es", "de", "fr").contains(language)) {
                throw new ConfigValidationException("config.validation.language");
            }
        }
    }

    public static final class ConfigValidationException extends IllegalArgumentException {
        private final String messageKey;

        public ConfigValidationException(String messageKey) {
            super(messageKey);
            this.messageKey = messageKey;
        }

        public String getMessageKey() {
            return messageKey;
        }
    }

    private static Properties loadProperties() {
        Properties loaded = new Properties();
        boolean migrated = false;
        try (InputStream defaults = ConfigManager.class.getResourceAsStream("/config.properties")) {
            if (defaults == null) {
                throw new IOException("Missing default configuration resource: /config.properties");
            }
            loaded.load(defaults);

            Path sourceConfig = Path.of("src/main/resources/config.properties");
            if (Files.isRegularFile(sourceConfig)) {
                migrated |= loadLayer(loaded, sourceConfig);
            }

            Path userConfig = getConfigurationPath();
            if (Files.isRegularFile(userConfig)) {
                migrated |= loadLayer(loaded, userConfig);
            }
            if (migrated) {
                persist(loaded);
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
        return loaded;
    }

    private static boolean loadLayer(Properties destination, Path path) throws IOException {
        Properties layer = new Properties();
        boolean migrated = false;
        try (InputStream in = Files.newInputStream(path)) {
            layer.load(in);
        }
        for (Map.Entry<String, String> legacyEntry : LEGACY_KEYS.entrySet()) {
            String oldValue = layer.getProperty(legacyEntry.getKey());
            if (oldValue != null) {
                migrated = true;
                if (!layer.containsKey(legacyEntry.getValue())) {
                    layer.setProperty(legacyEntry.getValue(), oldValue);
                }
            }
            layer.remove(legacyEntry.getKey());
        }
        destination.putAll(layer);
        return migrated;
    }

    private static Path getConfigurationPath() {
        Path home = Path.of(System.getProperty("user.home"));
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.startsWith("win")) {
            String appData = System.getenv("APPDATA");
            return (appData == null || appData.isBlank()
                    ? home.resolve("AppData").resolve("Roaming")
                    : Path.of(appData)).resolve(NAME).resolve("config.properties");
        }
        if (os.contains("mac")) {
            return home.resolve("Library").resolve("Application Support")
                    .resolve(NAME).resolve("config.properties");
        }
        String xdgConfigHome = System.getenv("XDG_CONFIG_HOME");
        Path configHome = xdgConfigHome != null && !xdgConfigHome.isBlank()
                ? Path.of(xdgConfigHome)
                : home.resolve(".config");
        return configHome.resolve(NAME).resolve("config.properties");
    }

    public static synchronized void saveSettings(Settings settings) throws IOException {
        Properties updated = new Properties();
        updated.putAll(properties);
        updated.setProperty("indexing.method", settings.indexingMethod());
        updated.setProperty("indexing.paragraphs-per-chunk", String.valueOf(settings.paragraphsPerChunk()));
        updated.setProperty("indexing.overlap-characters", String.valueOf(settings.overlapCharacters()));
        updated.setProperty("indexing.chunk-character-limit", String.valueOf(settings.chunkCharacterLimit()));
        updated.setProperty("indexing.regenerate-index-on-load", String.valueOf(settings.deleteIndexOnLoad()));
        updated.setProperty("retrieval.top-k", String.valueOf(settings.topK()));
        updated.setProperty("retrieval.minimum-score", String.valueOf(settings.minScore()));
        updated.setProperty("models.ollama.base-url", settings.ollamaBaseUrl());
        updated.setProperty("chat.answers-enabled", String.valueOf(settings.queryAnswer()));
        updated.setProperty("augmentation.output-mode", settings.outputMode());
        updated.setProperty("ui.language", settings.language());
        persist(updated);
        properties.clear();
        properties.putAll(updated);
    }

    public static Settings getSettings() {
        return new Settings(
                getParagraphsPerChunk(),
                getOverlapCharacters(),
                getOllamaBaseUrl(),
                getTopK(),
                getChunkCharacterLimit(),
                getMinScore(),
                getQueryAnswer(),
                getOutputMode(),
                getIndexingMethod(),
                getLanguage(),
                getDeleteIndexOnLoad());
    }

    private static synchronized void updateProperty(String key, String value) throws IOException {
        Properties updated = new Properties();
        updated.putAll(properties);
        updated.setProperty(key, value);
        persist(updated);
        properties.clear();
        properties.putAll(updated);
    }

    private static void persist(Properties updated) throws IOException {
        Path configPath = getConfigurationPath();
        Files.createDirectories(configPath.getParent());
        Path temporaryFile = Files.createTempFile(configPath.getParent(), "dirchat-config-", ".tmp");
        try {
            try (OutputStream out = Files.newOutputStream(temporaryFile)) {
                updated.store(out, "dirchat user configuration");
            }
            try {
                Files.move(temporaryFile, configPath,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporaryFile, configPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporaryFile);
        }
    }

    public static String getIndexingMethod() {
        return properties.getProperty("indexing.method", "sbert");
    }

    public static void setIndexingMethod(String value) throws IOException {
        updateProperty("indexing.method", value);
    }

    public static int getParagraphsPerChunk() {
        return getInteger("indexing.paragraphs-per-chunk", 6);
    }

    public static void setParagraphsPerChunk(int value) throws IOException {
        updateProperty("indexing.paragraphs-per-chunk", String.valueOf(value));
    }

    public static int getOverlapCharacters() {
        return getInteger("indexing.overlap-characters", 0);
    }

    public static int setOverlapCharacters(int value) throws IOException {
        updateProperty("indexing.overlap-characters", String.valueOf(value));
        return value;
    }

    public static String getOllamaBaseUrl() {
        return properties.getProperty("models.ollama.base-url", "http://localhost:11434");
    }

    public static void setOllamaBaseUrl(String value) throws IOException {
        updateProperty("models.ollama.base-url", value);
    }

    public static int getTopK() {
        return getInteger("retrieval.top-k", 5);
    }

    public static void setTopK(int value) throws IOException {
        updateProperty("retrieval.top-k", String.valueOf(value));
    }

    public static String getOutputMode() {
        return properties.getProperty("augmentation.output-mode", "none");
    }

    public static void setOutputMode(String value) throws IOException {
        updateProperty("augmentation.output-mode", value);
    }

    public static String getLanguage() {
        String value = properties.getProperty("ui.language", "en");
        if (!Set.of("en", "es", "de", "fr").contains(value)) {
            LogMessages.logWarning("Unsupported ui.language setting; using English.");
            return "en";
        }
        return value;
    }

    public static void setLanguage(String value) throws IOException {
        updateProperty("ui.language", value);
    }

    public static int getChunkCharacterLimit() {
        return getInteger("indexing.chunk-character-limit", 1024);
    }

    public static void setChunkCharacterLimit(int value) throws IOException {
        updateProperty("indexing.chunk-character-limit", String.valueOf(value));
    }

    public static boolean getQueryAnswer() {
        return getBoolean("chat.answers-enabled", true);
    }

    public static void setQueryAnswer(boolean value) throws IOException {
        updateProperty("chat.answers-enabled", String.valueOf(value));
    }

    public static double getMinScore() {
        String value = properties.getProperty("retrieval.minimum-score", "0.2");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            LogMessages.logWarning("Invalid retrieval.minimum-score setting; using 0.2.");
            return 0.2;
        }
    }

    public static void setMinScore(double value) throws IOException {
        updateProperty("retrieval.minimum-score", String.valueOf(value));
    }

    public static boolean getDeleteIndexOnLoad() {
        return getBoolean("indexing.regenerate-index-on-load", true);
    }

    public static void setDeleteIndexOnLoad(boolean value) throws IOException {
        updateProperty("indexing.regenerate-index-on-load", String.valueOf(value));
    }

    private static int getInteger(String key, int defaultValue) {
        String value = properties.getProperty(key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LogMessages.logWarning("Invalid " + key + " setting; using " + defaultValue + ".");
            return defaultValue;
        }
    }

    private static boolean getBoolean(String key, boolean defaultValue) {
        String value = properties.getProperty(key, String.valueOf(defaultValue));
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        LogMessages.logWarning("Invalid " + key + " setting; using " + defaultValue + ".");
        return defaultValue;
    }
}
