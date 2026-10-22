package com.spendsmart.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class MoneyAndDatesTest {

    @Test
    void amountsAreScaledToCurrencyMinorUnitsWithoutSilentRounding() {
        assertThat(Money.requirePositiveAmount(new BigDecimal("12.5"), "INR", "amount")).isEqualByComparingTo("12.50")
                .satisfies(v -> assertThat(v.scale()).isEqualTo(2));
        assertThat(Money.requirePositiveAmount(new BigDecimal("12.500"), "INR", "amount").scale()).isEqualTo(2);
        assertThatThrownBy(() -> Money.requirePositiveAmount(new BigDecimal("12.505"), "INR", "amount"))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> Money.requirePositiveAmount(new BigDecimal("1.5"), "JPY", "amount"))
                .isInstanceOf(FieldValidationException.class);
        assertThat(Money.requirePositiveAmount(new BigDecimal("1.234"), "KWD", "amount").scale()).isEqualTo(3);
        assertThatThrownBy(() -> Money.requirePositiveAmount(new BigDecimal("1E13"), "INR", "amount"))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void decimalArithmeticIsExact() {
        // The classic 0.1 + 0.2 floating-point error cannot occur with BigDecimal.
        assertThat(new BigDecimal("0.10").add(new BigDecimal("0.20"))).isEqualByComparingTo("0.30");
    }

    @Test
    void percentChangeRefusesZeroBaseline() {
        assertThat(Percent.change(new BigDecimal("50"), BigDecimal.ZERO)).isNull();
        assertThat(Percent.change(new BigDecimal("118"), new BigDecimal("100"))).isEqualByComparingTo("18.0");
        assertThat(Percent.change(new BigDecimal("75"), new BigDecimal("100"))).isEqualByComparingTo("-25.0");
        assertThat(Percent.of(new BigDecimal("1"), new BigDecimal("3"))).isEqualByComparingTo("33.3");
        assertThat(Percent.of(BigDecimal.ONE, BigDecimal.ZERO)).isNull();
    }

    @Test
    void wholeMonthRangesCompareAgainstPreviousWholeMonths() {
        DateRange october = DateRange.of(YearMonth.of(2026, 10));
        assertThat(october.previous()).isEqualTo(new DateRange(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30)));
        DateRange march = DateRange.of(YearMonth.of(2024, 3));
        assertThat(march.previous().to()).isEqualTo(LocalDate.of(2024, 2, 29)); // leap year
        DateRange quarter = new DateRange(LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30));
        assertThat(quarter.previous()).isEqualTo(new DateRange(LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30)));
        assertThat(october.isSingleCalendarMonth()).isTrue();
        assertThat(quarter.isSingleCalendarMonth()).isFalse();
    }

    @Test
    void arbitraryRangesCompareAgainstSameLengthImmediatelyBefore() {
        DateRange tenDays = new DateRange(LocalDate.of(2026, 10, 6), LocalDate.of(2026, 10, 15));
        assertThat(tenDays.days()).isEqualTo(10);
        assertThat(tenDays.previous()).isEqualTo(new DateRange(LocalDate.of(2026, 9, 26), LocalDate.of(2026, 10, 5)));
    }

    @Test
    void invalidRangesAreRejected() {
        assertThatThrownBy(() -> new DateRange(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 1)))
                .isInstanceOf(FieldValidationException.class);
        assertThatThrownBy(() -> new DateRange(LocalDate.of(2000, 1, 1), LocalDate.of(2026, 1, 1)))
                .isInstanceOf(FieldValidationException.class);
    }

    @Test
    void likePatternEscapesWildcards() {
        assertThat(Text.likePattern("50%_off")).isEqualTo("%50\\%\\_off%");
    }
}
