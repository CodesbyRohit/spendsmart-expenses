package com.spendsmart.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "Enter your email.") @Email(message = "Enter a valid email address.")
            @Size(max = 254, message = "Email is too long.") String email,
            @NotBlank(message = "Choose a password.") @Size(max = 128, message = "Password is too long.") String password,
            @NotBlank(message = "Enter your name.") @Size(max = 80, message = "Name can be at most 80 characters.") String displayName,
            @NotBlank(message = "Choose a currency.") String defaultCurrency,
            @NotBlank(message = "Choose a format.") String locale,
            /* Browser-detected IANA zone; optional, unknown values fall back to UTC (the user can't fix a hidden field). */
            @Size(max = 64) String timeZone) {

        @Override
        public String toString() {
            return "RegisterRequest[email=" + email + ", password=***]";
        }
    }

    public record LoginRequest(
            @NotBlank(message = "Enter your email.") @Size(max = 254) String email,
            @NotBlank(message = "Enter your password.") @Size(max = 128) String password) {

        @Override
        public String toString() {
            return "LoginRequest[email=" + email + ", password=***]";
        }
    }
}
