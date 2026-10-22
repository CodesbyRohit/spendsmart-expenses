package com.spendsmart.category;

import static com.spendsmart.common.TransactionType.EXPENSE;
import static com.spendsmart.common.TransactionType.INCOME;

import com.spendsmart.common.TransactionType;
import java.util.List;

/** Categories every new account starts with. Seeded in the same database transaction that creates the account. */
public final class DefaultCategories {

    public record Definition(String name, TransactionType type, String icon) {
    }

    public static final List<Definition> ALL = List.of(
            new Definition("Food and Dining", EXPENSE, "utensils"),
            new Definition("Groceries", EXPENSE, "shopping-cart"),
            new Definition("Transportation", EXPENSE, "bus"),
            new Definition("Education", EXPENSE, "graduation-cap"),
            new Definition("Shopping", EXPENSE, "shopping-bag"),
            new Definition("Housing", EXPENSE, "home"),
            new Definition("Utilities", EXPENSE, "zap"),
            new Definition("Healthcare", EXPENSE, "heart-pulse"),
            new Definition("Entertainment", EXPENSE, "film"),
            new Definition("Subscriptions", EXPENSE, "repeat"),
            new Definition("Travel", EXPENSE, "plane"),
            new Definition("Other", EXPENSE, "circle-dot"),
            new Definition("Salary", INCOME, "briefcase"),
            new Definition("Freelance Income", INCOME, "laptop"),
            new Definition("Other", INCOME, "circle-dot"));

    private DefaultCategories() {
    }
}
