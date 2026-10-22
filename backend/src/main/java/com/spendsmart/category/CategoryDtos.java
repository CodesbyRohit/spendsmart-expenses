package com.spendsmart.category;

import com.spendsmart.common.TransactionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class CategoryDtos {

    private CategoryDtos() {
    }

    public record CategoryResponse(UUID id, String name, TransactionType type, String icon, boolean isDefault,
                                   boolean archived, long transactionCount) {

        static CategoryResponse of(Category c, long transactionCount) {
            return new CategoryResponse(c.getId(), c.getName(), c.getType(), c.getIcon(), c.isDefaultCategory(),
                    c.isArchived(), transactionCount);
        }
    }

    /** Compact form embedded in transactions and budgets. */
    public record CategoryRef(UUID id, String name, TransactionType type, String icon) {

        public static CategoryRef of(Category c) {
            return new CategoryRef(c.getId(), c.getName(), c.getType(), c.getIcon());
        }
    }

    public record CreateCategoryRequest(
            @NotBlank(message = "Enter a name.") @Size(max = 50, message = "Name can be at most 50 characters.") String name,
            @NotNull(message = "Choose income or expense.") TransactionType type,
            @NotBlank(message = "Choose an icon.") String icon) {
    }

    /** The type is intentionally absent: it cannot change once transactions may reference the category. */
    public record UpdateCategoryRequest(
            @NotBlank(message = "Enter a name.") @Size(max = 50, message = "Name can be at most 50 characters.") String name,
            @NotBlank(message = "Choose an icon.") String icon,
            @NotNull Boolean archived) {
    }

    public record CategoryUsageResponse(long transactionCount, long budgetLineCount) {
    }
}
