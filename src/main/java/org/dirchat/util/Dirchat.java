package org.dirchat.util;

import java.nio.file.Path;
import java.util.function.Predicate;

public class Dirchat {
    public static final String VERSION = "0.1.0-alpha";
    public static final String NAME = "dirchat";
    public static final String DIRECTORY = ".dirchat";

    // Functions in use
    public static boolean safeIsRegularFile(Path path) {
        try {
            return java.nio.file.Files.isRegularFile(path); //
        } catch (Exception e) {
            return false;
        }
    }

    // Is index directory in path
    public static boolean isInIndexDirectory(Path path) {
        for (Path part : path) {
            if (part.toString().equals(DIRECTORY)) {
                return true;
            }
        }
        return false;
    }

    // Is supported file
    public static boolean isSupported(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".pdf") || name.endsWith(".docx") || name.endsWith(".txt");
    }


}
