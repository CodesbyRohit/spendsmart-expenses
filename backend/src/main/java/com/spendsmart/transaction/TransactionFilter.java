package com.spendsmart.transaction;

import com.spendsmart.common.FieldValidationException;
import com.spendsmart.common.Money;
import com.spendsmart.common.Text;
import com.spendsmart.common.TransactionType;
import java.time.LocalDate;
import java.util.UUID;

/** Validated list/export filter. Every field is optional; the owner is always applied separately. */
public record TransactionFilter(String query, TransactionType type, UUID categoryId, String currency,
                                LocalDate from, LocalDate to) {

    public TransactionFilter {
        query = Text.clean(query);
        if (query != null && query.length() > 100) {
            throw new FieldValidationException("q", "Search text can be at most 100 characters.");
        }
        if (currency != null && !currency.isBlank()) {
            currency = Money.requireCurrency(currency, "currency");
        } else {
            currency = null;
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw new FieldValidationException("to", "End date must be on or after the start date.");
        }
    }

    public static TransactionFilter none() {
        return new TransactionFilter(null, null, null, null, null, null);
    }
}
