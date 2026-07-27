package com.wattsmart.backend.common.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DotenvLoader {

    private DotenvLoader() {
    }

    public static void load() {
        load(Path.of(".env"));
        load(Path.of("..", "..", ".env"));
    }

    private static void load(Path path) {
        if (!Files.isRegularFile(path)) {
            return;
        }
        try {
            Files.lines(path)
                    .map(String::trim)
                    .filter(line -> !line.isBlank() && !line.startsWith("#") && line.contains("="))
                    .forEach(DotenvLoader::setSystemPropertyIfMissing);
        } catch (IOException ignored) {
            // Environment variables are optional; startup should not fail because a .env file is unreadable.
        }
    }

    private static void setSystemPropertyIfMissing(String line) {
        int separatorIndex = line.indexOf('=');
        String key = line.substring(0, separatorIndex).trim();
        String value = stripQuotes(line.substring(separatorIndex + 1).trim());
        if (System.getenv(key) == null && System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
