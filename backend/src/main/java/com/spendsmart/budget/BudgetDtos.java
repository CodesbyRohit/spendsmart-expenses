package com.spendsmart.budget;

import com.spendsmart.budget.BudgetCalculator.Measure;
import com.spendsmart.budget.BudgetCalculator.Status;
import com.spendsmart.category.CategoryDtos.CategoryRef;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class BudgetDtos {

    private BudgetDtos() {
    }

    public record SaveBudgetRequest(
            @NotBlank(message = "Choose a currency.") String currency,
            BigDecimal totalLimit,
            @NotNull @Size(max = 50, message = "A budget can have at most 50 category limits.")
            List<@Valid @NotNull LineRequest> lines) {
    }

    public record LineRequest(@NotNull(message = "Choose a category.") UUID categoryId,
                              @NotNull(message = "Enter a limit.") BigDecimal limit) {
    }

    public record MeasureResponse(BigDecimal limit, BigDecimal spent, BigDecimal remaining, BigDecimal overBy,
                                  BigDecimal percentUsed, BigDecimal barPercent, Status status) {
        static MeasureResponse of(Measure m) {
            return new MeasureResponse(m.limit(), m.spent(), m.remaining(), m.overBy(), m.percentUsed(),
                    m.barPercent(), m.status());
        }
    }

    public record LineResponse(CategoryRef category, MeasureResponse measure) {
    }

    /**
     * A month's budget status. When {@code planExists} is false there is no plan yet, but {@code total.spent} still
     * reports the month's actual spending so the user can decide on limits.
     */
    public record BudgetMonthResponse(
            YearMonth month,
            String currency,
            boolean planExists,
            boolean sample,
            MeasureResponse total,
            List<LineResponse> lines,
            BigDecimal allocated,
            BigDecimal unbudgetedSpent,
            boolean overAllocated,
            int alertThreshold,
            /* Remaining total budget ÷ days left (current month only). */
            BigDecimal dailyAllowance,
            Long daysLeft) {
    }

    public record HistoryEntry(YearMonth month, BigDecimal totalLimit, BigDecimal spent, BigDecimal percentUsed,
                               Status status, int lineCount, int exceededLines) {
    }
}
