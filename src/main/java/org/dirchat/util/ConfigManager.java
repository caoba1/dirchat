package org.dirchat.util;

import java.io.*;
import java.util.Properties;
import java.util.logging.Logger;

public class ConfigManager {
    private static final String CONFIG_FILE = "src/main/resources/config.properties";
    private static final Properties properties = new Properties();
    private static final Logger logger = Logger.getLogger(ConfigManager.class.getName());

    static {
        try (InputStream in = new FileInputStream(CONFIG_FILE)) {
            properties.load(in);
        } catch (IOException e) {
            System.err.println("Failed to load config file: " + e.getMessage());
            logger.warning("Failed to load configuration file"); //TODO: message.properties
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
            logger.warning("Failed to save configuration file"); //TODO: message.properties
            throw e;
        }
    }

    // indexing.paragraphsPerChunk
    public static int getParagraphsPerChunk() {
        String value = properties.getProperty("paragraphsPerChunk", "6");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warning("Invalid paragraphsPerChunk value in config.properties, using default 6");
            return 6;
        }
    }
    public static void setParagraphsPerChunk(int paragraphsPerChunk) throws IOException {
        properties.setProperty("paragraphsPerChunk", String.valueOf(paragraphsPerChunk));
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated paragraphsPerChunk value");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            logger.warning("Failed to save configuration file");
            throw e;
        }
    }

    // indexing.paragraphOverlap
    public static int getOverlap() {
        String value = properties.getProperty("indexing.paragraphOverlap", "0");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warning("Invalid overlap value in config.properties, using default 0");
            return 0;
        }
    }
    public static int setOverlap(int overlap) throws IOException {
        properties.setProperty("indexing.paragraphOverlap", String.valueOf(overlap));
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated overlap value");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            logger.warning("Failed to save configuration file");
            throw e;
        }
        return overlap;
    }

    // indexing.database.type
    public static String getDatabaseType() {

        return properties.getProperty("indexing.database.type", "sqlite");
    }
    public static void setDatabaseType(String databaseType) throws IOException {
        properties.setProperty("indexing.database.type", databaseType);
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated database type");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            logger.warning("Failed to save configuration file");
            throw e;
        }
    }

}
