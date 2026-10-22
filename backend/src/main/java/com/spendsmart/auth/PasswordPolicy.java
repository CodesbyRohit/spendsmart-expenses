package com.spendsmart.auth;

import com.spendsmart.common.FieldValidationException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Length-based policy in line with NIST SP 800-63B: at least 10 characters, no composition rules.
 * The 72-byte ceiling exists because BCrypt only uses the first 72 bytes of input.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 10;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static void check(String password, String email, String field) {
        if (password == null || password.codePointCount(0, password.length()) < MIN_LENGTH) {
            throw new FieldValidationException(field, "Use at least " + MIN_LENGTH + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new FieldValidationException(field, "Password is too long (maximum " + MAX_BYTES + " bytes).");
        }
        if (password.isBlank()) {
            throw new FieldValidationException(field, "Password can't be only spaces.");
        }
        if (email != null && password.toLowerCase(Locale.ROOT).contains(email.toLowerCase(Locale.ROOT))) {
            throw new FieldValidationException(field, "Password must not contain your email address.");
        }
    }
}
