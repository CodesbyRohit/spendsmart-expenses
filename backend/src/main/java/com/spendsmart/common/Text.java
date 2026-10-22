package com.spendsmart.common;

import java.util.Locale;

/** Input normalisation for free-text fields. */
public final class Text {

    private Text() {
    }

    /** Trims and collapses internal whitespace; returns {@code null} for blank input. */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String collapsed = value.strip().replaceAll("\\s+", " ");
        return collapsed.isEmpty() ? null : collapsed;
    }

    /** Like {@link #clean} but keeps line breaks (for notes). */
    public static String cleanMultiline(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    public static String normalizeKey(String value) {
        String cleaned = clean(value);
        return cleaned == null ? null : cleaned.toLowerCase(Locale.ROOT);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    /** Escapes LIKE wildcards so user search text is matched literally. */
    public static String likePattern(String query) {
        String escaped = query.toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
