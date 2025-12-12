package org.dirchat.util;
// TODO: Refactor

import java.io.*;
import java.util.Properties;
import org.dirchat.util.LogMessages;

public class ConfigManager {
    private static final String CONFIG_FILE = "src/main/resources/config.properties";
    public static final String VERSION = "0.1.0-alpha";
    public static final String NAME = "dirchat";
    public static final String DIRECTORY = ".dirchat";
    private static final Properties properties = new Properties();

    static {
        try (InputStream in = new FileInputStream(CONFIG_FILE)) {
            properties.load(in);
        } catch (IOException e) {
            System.err.println("Failed to load config file: " + e.getMessage());
            LogMessages.logWarning("Failed to load configuration file"); //TODO: message.properties
        }
    }

    // indexing.method
    public static String getIndexingMethod() {
        return properties.getProperty("indexing.method", "default");
    }
    public static void setIndexingMethod(String indexingMethod) throws IOException{
        properties.setProperty("indexing.method", indexingMethod);
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated indexing method");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logWarning("Failed to save configuration file"); //TODO: message.properties
            throw e;
        }
    }

    // indexing.paragraphsPerChunk
    public static int getParagraphsPerChunk() {
        String value = properties.getProperty("indexing.paragraphsPerChunk", "6");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LogMessages.logWarning("Invalid paragraphsPerChunk value in config.properties, using default 6");
            return 6;
        }
    }
    public static void setParagraphsPerChunk(int paragraphsPerChunk) throws IOException {
        properties.setProperty("indexing.paragraphsPerChunk", String.valueOf(paragraphsPerChunk));
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated paragraphsPerChunk value");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logWarning("Failed to save configuration file");
            throw e;
        }
    }

    // indexing.paragraphOverlap
    public static int getOverlapCharacters() {
        String value = properties.getProperty("indexing.paragraphOverlap", "0");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LogMessages.logWarning("Invalid overlap value in config.properties, using default 0");
            return 0;
        }
    }
    public static int setOverlapCharacters(int overlap) throws IOException {
        properties.setProperty("indexing.paragraphOverlap", String.valueOf(overlap));
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated overlap value");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logWarning("Failed to save configuration file");
            throw e;
        }
        return overlap;
    }


    // ollama.base.url
    public static String getOllamaBaseUrl() {
        return properties.getProperty("indexing.ollama.url", "http://localhost:11434");
    }
    public static void setOllamaBaseUrl(String ollamaBaseUrl) throws IOException {
        properties.setProperty("indexing.ollama.url", ollamaBaseUrl);
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated Ollama base URL");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logWarning("Failed to save configuration file");
            throw e;
        }
    }

    // indexing.topK
    public static int getTopK() {
        String value = properties.getProperty("indexing.topK", "5");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LogMessages.logWarning("Invalid topK value in config.properties, using default 5");
            return 5;
        }
    }
    public static void setTopK(int topK) throws IOException {
        properties.setProperty("indexing.topK", String.valueOf(topK));
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated topK value");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logWarning("Failed to save configuration file");
            throw e;
        }
    }

    // output.mode (enum: truncate, summarize, none)
    public static String getOutputMode() {
        return properties.getProperty("output.mode", "none");
    }
    public static void setOutputMode(String mode) throws IOException {
        properties.setProperty("output.mode", mode);
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated output mode");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logWarning("Failed to save configuration file");
            throw e;
        }
    }

    // app.language
    public static String getLanguage() {
        return properties.getProperty("app.language", "en");
    }
    public static void setLanguage(String langCode) throws IOException {
        properties.setProperty("app.language", langCode);
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated app language");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logWarning("Failed to save configuration file");
            throw e;
        }
    }

    // indexing.chunkCharacterLimit (int)
    public static int getChunkCharacterLimit() {
        String value = properties.getProperty("indexing.chunkCharacterLimit", "1024");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LogMessages.logWarning("Invalid chunkCharacterLimit value in config.properties, using default 1000");
            return -1;
        }
    }
    public static void setChunkCharacterLimit(int limit) throws IOException {
        properties.setProperty("indexing.chunkCharacterLimit", String.valueOf(limit));
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated chunkCharacterLimit value");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logWarning("Failed to save configuration file");
            throw e;
        }
    }

    // queryAnswer
    public static boolean getQueryAnswer() {
        return Boolean.parseBoolean(properties.getProperty("queryAnswer", "true"));
    }
    public static void setQueryAnswer(boolean value) throws IOException {
        properties.setProperty("queryAnswer", Boolean.toString(value));
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated queryAnswer value");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logWarning("Failed to save configuration file");
            throw e;
        }
    }

    // minScore
    public static double getMinScore() {
        String value = properties.getProperty("indexing.minScore", "0.0");
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            LogMessages.logWarning("Invalid minScore value in config.properties, using default 0.0");
            return 0.0;
        }
    }
    public static void setMinScore(double minScore) throws IOException {
        properties.setProperty("indexing.minScore", String.valueOf(minScore));
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated minScore value");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logWarning("Failed to save configuration file");
            throw e;
        }
    }

    // indexing.deleteIndexOnLoad
    public static boolean getDeleteIndexOnLoad() {
        String value = properties.getProperty("indexing.deleteIndexOnLoad", "false");
        return Boolean.parseBoolean(value);
    }
    public static void setDeleteIndexOnLoad(boolean delete) throws IOException {
        properties.setProperty("indexing.deleteIndexOnLoad", String.valueOf(delete));
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated deleteIndexOnLoad value");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logWarning("Failed to save configuration file");
            throw e;
        }
    }
}
