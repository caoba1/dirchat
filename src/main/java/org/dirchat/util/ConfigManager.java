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
            logger.warning("Failed to load configuration file");
        }
    }

    public static String getIndexingMethod() {
        return properties.getProperty("indexing.method", "default");
    }

    public static void setIndexingMethod(String indexingMethod) throws IOException{
        properties.setProperty("indexing.method", indexingMethod);
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated indexing method");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            logger.warning("Failed to save configuration file");
            throw e;
        }
    }

    public static int getParagraphsPerChunk() {
        String value = properties.getProperty("paragraphsPerChunk", "6");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warning("Invalid paragraphsPerChunk value in config.properties, using default 6");
            return 6;
        }
    }

    public static int getOverlap() {
        String value = properties.getProperty("overlap", "0");
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            logger.warning("Invalid overlap value in config.properties, using default 0");
            return 0;
        }
    }

    public static String getDatabaseType() {
        return properties.getProperty("database.type", "sqlite");
    }

    public static void setDatabaseType(String databaseType) throws IOException {
        properties.setProperty("database.type", databaseType);
        try (OutputStream out = new FileOutputStream(CONFIG_FILE)) {
            properties.store(out, "Updated database type");
        } catch (IOException e) {
            System.err.println("Failed to save config file: " + e.getMessage());
            logger.warning("Failed to save configuration file");
            throw e;
        }
    }
}
