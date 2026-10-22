package com.spendsmart.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.spendsmart.category.Category;
import com.spendsmart.category.CategoryRepository;
import com.spendsmart.common.TransactionType;
import com.spendsmart.user.User;
import com.spendsmart.user.UserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

/** Repository-level checks against the Flyway-migrated schema, including database-enforced invariants. */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TransactionRepositoryTest {

    @Autowired
    UserRepository users;
    @Autowired
    CategoryRepository categories;
    @Autowired
    TransactionRepository transactions;

    private User user(String tag) {
        return users.saveAndFlush(new User(tag + "-" + UUID.randomUUID() + "@example.test", "{noop}x", "T", "INR", "en-IN"));
    }

    private Transaction tx(UUID userId, Category category, TransactionType type, String amount, String date) {
        Transaction t = new Transaction(userId, false);
        t.setType(type);
        t.setCategory(category);
        t.setAmount(new BigDecimal(amount));
        t.setCurrency("INR");
        t.setDescription("t");
        t.setTransactionDate(LocalDate.parse(date));
        return t;
    }

    @Test
    void aggregatesAreScopedByUserCurrencyAndInclusiveDates() {
        User a = user("a");
        User b = user("b");
        Category food = categories.save(new Category(a.getId(), "Food", TransactionType.EXPENSE, "utensils", false));
        Category bFood = categories.save(new Category(b.getId(), "Food", TransactionType.EXPENSE, "utensils", false));
        transactions.saveAll(List.of(
                tx(a.getId(), food, TransactionType.EXPENSE, "10.25", "2026-10-01"),
                tx(a.getId(), food, TransactionType.EXPENSE, "20.75", "2026-10-31"),
                tx(a.getId(), food, TransactionType.EXPENSE, "99", "2026-11-01"),
                tx(b.getId(), bFood, TransactionType.EXPENSE, "500", "2026-10-15")));
        transactions.flush();

        BigDecimal sum = transactions.sumExpenses(a.getId(), "INR", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"));
        assertThat(sum).isEqualByComparingTo("31.00");
        assertThat(transactions.totalsByCategory(a.getId(), "INR", TransactionType.EXPENSE,
                LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")))
                .singleElement().satisfies(r -> assertThat(r.getCount()).isEqualTo(2));
        assertThat(transactions.sumExpenses(a.getId(), "USD", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31")))
                .isEqualByComparingTo("0");
    }

    @Test
    void databaseRejectsCategoryOfWrongType() {
        User a = user("a");
        Category salary = categories.saveAndFlush(new Category(a.getId(), "Salary", TransactionType.INCOME, "briefcase", false));

        assertThatThrownBy(() -> transactions.saveAndFlush(tx(a.getId(), salary, TransactionType.EXPENSE, "1", "2026-10-01")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsCrossUserCategoryReference() {
        User a = user("a");
        User b = user("b");
        Category bFood = categories.saveAndFlush(new Category(b.getId(), "Food", TransactionType.EXPENSE, "utensils", false));
        assertThatThrownBy(() -> transactions.saveAndFlush(tx(a.getId(), bFood, TransactionType.EXPENSE, "1", "2026-10-01")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void databaseRejectsNonPositiveAmounts() {
        User a = user("a");
        Category food = categories.saveAndFlush(new Category(a.getId(), "Food", TransactionType.EXPENSE, "utensils", false));
        assertThatThrownBy(() -> transactions.saveAndFlush(tx(a.getId(), food, TransactionType.EXPENSE, "0", "2026-10-01")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
