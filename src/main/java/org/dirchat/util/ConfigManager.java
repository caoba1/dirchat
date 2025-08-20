package org.dirchat.util;

import java.io.*;
import java.util.Properties;
import org.dirchat.util.LogMessages;

public class ConfigManager {
    private static final String CONFIG_FILE = "src/main/resources/config.properties";
    private static final Properties properties = new Properties();

    static {
        try (InputStream in = new FileInputStream(CONFIG_FILE)) {
            properties.load(in);
        } catch (IOException e) {
            System.err.println("Failed to load config file: " + e.getMessage());
            LogMessages.logger.warning("Failed to load configuration file"); //TODO: message.properties
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
            LogMessages.logger.warning("Failed to save configuration file"); //TODO: message.properties
            throw e;
        }
    }

    // indexing.paragraphsPerChunk
    public static int getParagraphsPerChunk() {
        String value = properties.getProperty("indexing.paragraphsPerChunk", "6");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LogMessages.logger.warning("Invalid paragraphsPerChunk value in config.properties, using default 6");
            return 6;
        }
    }
    public static void setParagraphsPerChunk(int paragraphsPerChunk) throws IOException {
        properties.setProperty("indexing.paragraphsPerChunk", String.valueOf(paragraphsPerChunk));
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated paragraphsPerChunk value");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logger.warning("Failed to save configuration file");
            throw e;
        }
    }

    // indexing.paragraphOverlap
    public static int getOverlap() {
        String value = properties.getProperty("indexing.paragraphOverlap", "0");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LogMessages.logger.warning("Invalid overlap value in config.properties, using default 0");
            return 0;
        }
    }
    public static int setOverlap(int overlap) throws IOException {
        properties.setProperty("indexing.paragraphOverlap", String.valueOf(overlap));
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated overlap value");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logger.warning("Failed to save configuration file");
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
            LogMessages.logger.warning("Failed to save configuration file");
            throw e;
        }
    }

    // indexing.topK
    public static int getTopK() {
        String value = properties.getProperty("indexing.topK", "5");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            LogMessages.logger.warning("Invalid topK value in config.properties, using default 5");
            return 5;
        }
    }
    public static void setTopK(int topK) throws IOException {
        properties.setProperty("indexing.topK", String.valueOf(topK));
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated topK value");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            LogMessages.logger.warning("Failed to save configuration file");
            throw e;
        }
    }
}
