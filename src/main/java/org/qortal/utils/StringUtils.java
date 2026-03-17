package org.qortal.utils;

public class StringUtils {

    public static String sanitizeString(String input) {
        String sanitized = input
                .replaceAll("[<>:\"/\\\\|?*]", "") // Remove invalid characters
                .replaceAll("^\\s+|\\s+$", "") // Trim leading and trailing whitespace
                .replaceAll("\\s+", "_"); // Replace consecutive whitespace with underscores

        return sanitized;
    }
}
