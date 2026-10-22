package com.spendsmart.user;

import com.spendsmart.common.FieldValidationException;
import java.util.List;
import java.util.Set;

/** Number/date formatting locales offered in settings. */
public final class Locales {

    public static final List<String> SUPPORTED = List.of(
            "en-IN", "en-US", "en-GB", "en-AU", "en-CA", "en-SG", "en-IE", "en-NZ", "en-ZA",
            "hi-IN", "de-DE", "fr-FR", "es-ES", "it-IT", "nl-NL", "pt-BR", "ja-JP", "sv-SE");

    private static final Set<String> ALLOWED = Set.copyOf(SUPPORTED);

    private Locales() {
    }

    public static String require(String tag, String field) {
        if (tag == null || !ALLOWED.contains(tag)) {
            throw new FieldValidationException(field, "Choose a supported format.");
        }
        return tag;
    }
}
