package com.spendsmart.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AccountDtos {

    private AccountDtos() {
    }

    /** {@code currentPassword} is required only when the email changes. */
    public record UpdateProfileRequest(
            @NotBlank(message = "Enter your name.") @Size(max = 80, message = "Name can be at most 80 characters.") String displayName,
            @NotBlank(message = "Enter your email.") @Email(message = "Enter a valid email address.") @Size(max = 254) String email,
            @Size(max = 128) String currentPassword) {

        @Override
        public String toString() {
            return "UpdateProfileRequest[displayName=" + displayName + ", email=" + email + "]";
        }
    }

    public record UpdatePreferencesRequest(
            @NotBlank(message = "Choose a currency.") String defaultCurrency,
            @NotBlank(message = "Choose a format.") String locale,
            @NotNull(message = "Choose a theme.") Theme theme,
            @NotNull Boolean budgetAlertsEnabled,
            @NotNull @Min(value = 50, message = "Threshold must be between 50% and 100%.")
            @Max(value = 100, message = "Threshold must be between 50% and 100%.") Integer budgetAlertThreshold) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "Enter your current password.") @Size(max = 128) String currentPassword,
            @NotBlank(message = "Choose a new password.") @Size(max = 128) String newPassword) {

        @Override
        public String toString() {
            return "ChangePasswordRequest[***]";
        }
    }

    /** Deleting an account requires the password and typing DELETE, so it can't happen from a stray click or CSRF. */
    public record DeleteAccountRequest(
            @NotBlank(message = "Enter your password.") @Size(max = 128) String password,
            @NotBlank(message = "Type DELETE to confirm.") String confirmation) {

        @Override
        public String toString() {
            return "DeleteAccountRequest[***]";
        }
    }
}
