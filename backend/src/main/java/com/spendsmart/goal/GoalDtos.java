package com.spendsmart.goal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class GoalDtos {

    private GoalDtos() {
    }

    public record CreateGoalRequest(
            @NotBlank(message = "Enter a goal name.") @Size(max = 80, message = "Name can be at most 80 characters.") String name,
            @NotNull(message = "Enter a target amount.") BigDecimal targetAmount,
            @NotBlank(message = "Choose a currency.") String currency,
            LocalDate targetDate,
            @Size(max = 500, message = "Notes can be at most 500 characters.") String notes,
            /* Optional amount already set aside; recorded as the first deposit so history stays complete. */
            BigDecimal initialAmount) {
    }

    /** Currency is not editable: existing contributions are in the original currency. */
    public record UpdateGoalRequest(
            @NotBlank(message = "Enter a goal name.") @Size(max = 80, message = "Name can be at most 80 characters.") String name,
            @NotNull(message = "Enter a target amount.") BigDecimal targetAmount,
            LocalDate targetDate,
            @Size(max = 500, message = "Notes can be at most 500 characters.") String notes,
            @NotNull Long version) {
    }

    public record ContributionRequest(
            @NotNull(message = "Choose deposit or withdrawal.") GoalContribution.Type type,
            @NotNull(message = "Enter an amount.") BigDecimal amount,
            @NotNull(message = "Choose a date.") LocalDate date,
            @Size(max = 200, message = "Note can be at most 200 characters.") String note) {
    }

    /**
     * {@code savedAmount} is the sum of the user's own recorded contributions — it is not a verified bank balance.
     * {@code progressPercent} is uncapped; {@code barPercent} is clamped to 0–100.
     */
    public record GoalResponse(UUID id, String name, BigDecimal targetAmount, String currency, LocalDate targetDate,
                               String notes, BigDecimal savedAmount, BigDecimal remainingAmount,
                               BigDecimal progressPercent, BigDecimal barPercent, boolean completed,
                               long contributionCount, LocalDate lastContributionDate,
                               /* Remaining ÷ whole months until the target date; null without a future target date. */
                               BigDecimal monthlyNeeded, Long daysLeft, boolean sample, long version) {
    }

    public record ContributionResponse(UUID id, GoalContribution.Type type, BigDecimal amount, LocalDate date,
                                       String note, Instant createdAt) {
    }
}
