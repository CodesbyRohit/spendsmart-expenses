package com.spendsmart.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendsmart.analytics.AnalyticsDtos.Cadence;
import com.spendsmart.analytics.AnalyticsDtos.RecurringPattern;
import com.spendsmart.category.Category;
import com.spendsmart.common.TransactionType;
import com.spendsmart.transaction.Transaction;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecurringDetectorTest {

    private static final UUID USER = UUID.randomUUID();
    private static final Category SUBS = new Category(USER, "Subscriptions", TransactionType.EXPENSE, "repeat", true);

    @Test
    void detectsMonthlyChargesWithStableAmounts() {
        List<Transaction> txs = List.of(
                expense("2026-07-10", "649", "StreamBox"),
                expense("2026-08-10", "649", "StreamBox"),
                expense("2026-09-10", "649", "streambox"),
                expense("2026-10-10", "699", "StreamBox"));
        List<RecurringPattern> patterns = RecurringDetector.detect(new ArrayList<>(txs), "INR");
        assertThat(patterns).hasSize(1);
        RecurringPattern p = patterns.getFirst();
        assertThat(p.cadence()).isEqualTo(Cadence.MONTHLY);
        assertThat(p.occurrences()).isEqualTo(4);
        assertThat(p.typicalAmount()).isEqualByComparingTo("649");
        assertThat(p.nextExpectedDate()).isEqualTo(LocalDate.of(2026, 11, 10));
    }

    @Test
    void weeklyChargesGetAMonthlyEquivalent() {
        List<RecurringPattern> patterns = RecurringDetector.detect(new ArrayList<>(List.of(
                expense("2026-10-01", "500", "City Metro"),
                expense("2026-10-08", "500", "City Metro"),
                expense("2026-10-15", "500", "City Metro"))), "INR");
        assertThat(patterns).singleElement().satisfies(p -> {
            assertThat(p.cadence()).isEqualTo(Cadence.WEEKLY);
            assertThat(p.monthlyEquivalent()).isEqualByComparingTo("2166.67");
        });
    }

    @Test
    void twoOccurrencesAreNotEnough() {
        assertThat(RecurringDetector.detect(new ArrayList<>(List.of(
                expense("2026-09-10", "649", "StreamBox"),
                expense("2026-10-10", "649", "StreamBox"))), "INR")).isEmpty();
    }

    @Test
    void irregularSpacingIsNotRecurring() {
        assertThat(RecurringDetector.detect(new ArrayList<>(List.of(
                expense("2026-08-01", "300", "Cafe"),
                expense("2026-08-19", "300", "Cafe"),
                expense("2026-10-02", "300", "Cafe"))), "INR")).isEmpty();
    }

    @Test
    void wildlyVaryingAmountsAreNotRecurring() {
        assertThat(RecurringDetector.detect(new ArrayList<>(List.of(
                expense("2026-08-10", "200", "Supermarket"),
                expense("2026-09-10", "2000", "Supermarket"),
                expense("2026-10-10", "450", "Supermarket"))), "INR")).isEmpty();
    }

    private static Transaction expense(String date, String amount, String merchant) {
        Transaction t = new Transaction(USER, false);
        t.setType(TransactionType.EXPENSE);
        t.setAmount(new BigDecimal(amount));
        t.setCurrency("INR");
        t.setCategory(SUBS);
        t.setDescription("Charge");
        t.setMerchant(merchant);
        t.setTransactionDate(LocalDate.parse(date));
        return t;
    }
}
