package com.spendsmart.transaction;

import com.spendsmart.category.CategoryDtos.CategoryRef;
import com.spendsmart.common.Money;
import com.spendsmart.common.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class TransactionDtos {

    private TransactionDtos() {
    }

    /**
     * Create/update payload. Only these fields are bindable — id, owner, timestamps and the sample flag are never
     * taken from the client (no mass assignment), and unknown JSON properties are rejected.
     */
    public record TransactionRequest(
            @NotNull(message = "Choose income or expense.") TransactionType type,
            @NotNull(message = "Enter an amount.") BigDecimal amount,
            @NotBlank(message = "Choose a currency.") String currency,
            @NotNull(message = "Choose a category.") UUID categoryId,
            @NotBlank(message = "Enter a description.") @Size(max = 140, message = "Description can be at most 140 characters.") String description,
            @NotNull(message = "Choose a date.") LocalDate transactionDate,
            @Size(max = 100, message = "Merchant can be at most 100 characters.") String merchant,
            PaymentMethod paymentMethod,
            @Size(max = 1000, message = "Notes can be at most 1000 characters.") String notes,
            /* Optimistic-lock version from the last read; required for updates, ignored on create. */
            Long version) {
    }

    public record TransactionResponse(UUID id, TransactionType type, BigDecimal amount, String currency,
                                      CategoryRef category, String description, LocalDate transactionDate,
                                      String merchant, PaymentMethod paymentMethod, String notes, boolean sample,
                                      Instant createdAt, Instant updatedAt, long version) {

        public static TransactionResponse of(Transaction t) {
            return new TransactionResponse(t.getId(), t.getType(), Money.scale(t.getAmount(), t.getCurrency()), t.getCurrency(),
                    CategoryRef.of(t.getCategory()), t.getDescription(), t.getTransactionDate(), t.getMerchant(),
                    t.getPaymentMethod(), t.getNotes(), t.isSample(), t.getCreatedAt(), t.getUpdatedAt(), t.getVersion());
        }
    }
}
