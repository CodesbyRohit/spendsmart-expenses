package com.spendsmart.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.spendsmart.budget.BudgetCalculator.LineInput;
import com.spendsmart.budget.BudgetCalculator.Measure;
import com.spendsmart.budget.BudgetCalculator.Result;
import com.spendsmart.budget.BudgetCalculator.Status;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BudgetCalculatorTest {

    private static final UUID FOOD = UUID.randomUUID();
    private static final UUID RENT = UUID.randomUUID();
    private static final UUID FUN = UUID.randomUUID();

    @Test
    void totalCountsEveryExpenseOnceAndLinesAreABreakdown() {
        Result r = BudgetCalculator.calculate(new BigDecimal("1000"),
                List.of(new LineInput(FOOD, new BigDecimal("300")), new LineInput(RENT, new BigDecimal("500"))),
                Map.of(FOOD, new BigDecimal("250"), RENT, new BigDecimal("500"), FUN, new BigDecimal("100")),
                80, "INR");
        assertThat(r.total().spent()).isEqualByComparingTo("850");
        assertThat(r.allocated()).isEqualByComparingTo("800");
        assertThat(r.unbudgetedSpent()).isEqualByComparingTo("100");
        assertThat(r.overAllocated()).isFalse();
        assertThat(r.total().status()).isEqualTo(Status.WARNING); // 85%
    }

    @Test
    void exactlyAtLimitIsWarningNotExceeded() {
        Measure m = BudgetCalculator.measure(new BigDecimal("500"), new BigDecimal("500"), 80, "INR");
        assertThat(m.status()).isEqualTo(Status.WARNING);
        assertThat(m.percentUsed()).isEqualByComparingTo("100.0");
        assertThat(m.remaining()).isEqualByComparingTo("0");
        assertThat(m.overBy()).isEqualByComparingTo("0");
    }

    @Test
    void overspendingKeepsTruePercentButClampsBarAndReportsOverage() {
        Measure m = BudgetCalculator.measure(new BigDecimal("400"), new BigDecimal("530"), 80, "INR");
        assertThat(m.status()).isEqualTo(Status.EXCEEDED);
        assertThat(m.percentUsed()).isEqualByComparingTo("132.5");
        assertThat(m.barPercent()).isEqualByComparingTo("100.0");
        assertThat(m.remaining()).isEqualByComparingTo("-130");
        assertThat(m.overBy()).isEqualByComparingTo("130");
    }

    @Test
    void thresholdBoundaryIsInclusive() {
        assertThat(BudgetCalculator.measure(new BigDecimal("100"), new BigDecimal("79.99"), 80, "INR").status())
                .isEqualTo(Status.ON_TRACK);
        assertThat(BudgetCalculator.measure(new BigDecimal("100"), new BigDecimal("80"), 80, "INR").status())
                .isEqualTo(Status.WARNING);
    }

    @Test
    void noSpendingAndNoLimitAreHandledWithoutInventedNumbers() {
        Measure zero = BudgetCalculator.measure(new BigDecimal("100"), BigDecimal.ZERO, 80, "INR");
        assertThat(zero.percentUsed()).isEqualByComparingTo("0");
        assertThat(zero.barPercent()).isEqualByComparingTo("0");
        assertThat(zero.status()).isEqualTo(Status.ON_TRACK);

        Measure none = BudgetCalculator.measure(null, new BigDecimal("50"), 80, "INR");
        assertThat(none.status()).isEqualTo(Status.NO_LIMIT);
        assertThat(none.percentUsed()).isNull();
        assertThat(none.barPercent()).isNull();
        assertThat(none.spent()).isEqualByComparingTo("50");
    }

    @Test
    void flagsCategoryLimitsThatExceedTheTotal() {
        Result r = BudgetCalculator.calculate(new BigDecimal("500"),
                List.of(new LineInput(FOOD, new BigDecimal("300")), new LineInput(RENT, new BigDecimal("300"))),
                Map.of(), 80, "INR");
        assertThat(r.overAllocated()).isTrue();
        assertThat(r.total().spent()).isEqualByComparingTo("0");
    }

    @Test
    void dailyAllowanceRoundsDownAndDisappearsWhenNothingRemains() {
        assertThat(BudgetCalculator.dailyAllowance(new BigDecimal("100"), 3, "INR")).isEqualByComparingTo("33.33");
        assertThat(BudgetCalculator.dailyAllowance(new BigDecimal("100"), 3, "JPY")).isEqualByComparingTo("33");
        assertThat(BudgetCalculator.dailyAllowance(new BigDecimal("-5"), 3, "INR")).isNull();
        assertThat(BudgetCalculator.dailyAllowance(BigDecimal.ZERO, 3, "INR")).isNull();
    }
}
