/*
 * dirchat — Java directory chat application.
 * Copyright (C) 2026 Carlos de Obaldia Pastor
 * License: GNU AGPL v3 or later. See LICENSE for details.
 */

package org.dirchat.util;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class Dirchat {

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
            if (part.toString().equals(ConfigManager.DIRECTORY)) {
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

    public static List<Double> floatArrayToDoubleList(float[] array) {
        return java.util.stream.IntStream.range(0, array.length)
                .mapToDouble(i -> array[i])
                .boxed()
                .collect(Collectors.toList());
    }

}
