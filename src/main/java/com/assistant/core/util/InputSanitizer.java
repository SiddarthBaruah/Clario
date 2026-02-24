package com.assistant.core.util;

import java.util.regex.Pattern;

/**
 * Basic input sanitization for user-supplied text: strip HTML/script-like content
 * and enforce maximum length. Use for display/storage of task titles, descriptions,
 * person names/notes, and assistant personality prompts.
 */
public final class InputSanitizer {

    private static final Pattern SCRIPT_AND_HTML = Pattern.compile(
            "<script[^>]*>.*?</script>|<[^>]+>|javascript:\\s*|on\\w+\\s*=",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern CONTROL_CHARS = Pattern.compile("[\\x00-\\x1F\\x7F]");

    private InputSanitizer() {}

    /**
     * Sanitizes a string for safe storage: strips script/HTML and control characters,
     * trims and truncates to maxLength. Returns null if input is null.
     */
    public static String sanitize(String input, int maxLength) {
        if (input == null) {
            return null;
        }
        String s = input.trim();
        s = SCRIPT_AND_HTML.matcher(s).replaceAll("");
        s = CONTROL_CHARS.matcher(s).replaceAll("");
        if (s.length() > maxLength) {
            s = s.substring(0, maxLength);
        }
        return s;
    }

    /**
     * Sanitizes with default max length of 10_000 characters (e.g. for notes or personality).
     */
    public static String sanitizeLongText(String input) {
        return sanitize(input, 10_000);
    }

    /**
     * Sanitizes with max length of 500 (e.g. for task title).
     */
    public static String sanitizeTitle(String input) {
        return sanitize(input, 500);
    }

    /**
     * Sanitizes with max length of 255 (e.g. for person name).
     */
    public static String sanitizeName(String input) {
        return sanitize(input, 255);
    }
}
