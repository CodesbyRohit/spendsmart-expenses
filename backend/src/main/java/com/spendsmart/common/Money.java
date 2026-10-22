package com.spendsmart.common;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Currency and amount rules. All money is {@link BigDecimal}; amounts are validated against the currency's ISO-4217
 * minor units and rejected (never silently rounded) if they carry more decimals.
 */
public final class Money {

    /** Upper bound per single entry; keeps NUMERIC(19,4) sums far from overflow. */
    public static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.9999");

    /** Curated list offered in the UI. Every code is a real ISO-4217 currency known to the JDK. */
    public static final List<String> SUPPORTED_CURRENCIES = List.of(
            "INR", "USD", "EUR", "GBP", "JPY", "AUD", "CAD", "SGD", "AED", "CHF",
            "CNY", "HKD", "NZD", "SEK", "NOK", "DKK", "ZAR", "BRL", "MXN", "KWD");

    private static final Set<String> SUPPORTED = Set.copyOf(SUPPORTED_CURRENCIES);

    private Money() {
    }

    public static boolean isSupportedCurrency(String code) {
        return code != null && SUPPORTED.contains(code);
    }

    public static String requireCurrency(String code, String field) {
        String normalized = code == null ? null : code.trim().toUpperCase(Locale.ROOT);
        if (!isSupportedCurrency(normalized)) {
            throw new FieldValidationException(field, "Choose a supported currency.");
        }
        return normalized;
    }

    public static int minorUnits(String currency) {
        return Math.max(0, Currency.getInstance(currency).getDefaultFractionDigits());
    }

    /**
     * Validates a user-entered positive amount for the given currency and returns it with the currency's scale.
     */
    public static BigDecimal requirePositiveAmount(BigDecimal amount, String currency, String field) {
        if (amount == null) {
            throw new FieldValidationException(field, "Enter an amount.");
        }
        if (amount.signum() <= 0) {
            throw new FieldValidationException(field, "Amount must be greater than zero.");
        }
        if (amount.compareTo(MAX_AMOUNT) > 0) {
            throw new FieldValidationException(field, "Amount is too large.");
        }
        int digits = minorUnits(currency);
        BigDecimal stripped = amount.stripTrailingZeros();
        if (stripped.scale() > digits) {
            throw new FieldValidationException(field, digits == 0
                    ? currency + " amounts cannot have decimals."
                    : currency + " amounts can have at most " + digits + " decimal places.");
        }
        return amount.setScale(digits, RoundingMode.UNNECESSARY);
    }

    /** Normalises a stored or summed value to the currency's display scale. */
    public static BigDecimal scale(BigDecimal value, String currency) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return v.setScale(minorUnits(currency), RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    public static String format(BigDecimal amount, String currency, Locale locale) {
        NumberFormat nf = NumberFormat.getCurrencyInstance(locale);
        nf.setCurrency(Currency.getInstance(currency));
        int digits = minorUnits(currency);
        nf.setMinimumFractionDigits(digits);
        nf.setMaximumFractionDigits(digits);
        return nf.format(amount);
    }
}
