package com.spendsmart.goal;

import com.spendsmart.common.ApiException;
import com.spendsmart.common.FieldValidationException;
import com.spendsmart.common.Money;
import com.spendsmart.common.Percent;
import com.spendsmart.common.Text;
import com.spendsmart.goal.GoalContributionRepository.GoalSaved;
import com.spendsmart.goal.GoalDtos.ContributionRequest;
import com.spendsmart.goal.GoalDtos.ContributionResponse;
import com.spendsmart.goal.GoalDtos.CreateGoalRequest;
import com.spendsmart.goal.GoalDtos.GoalResponse;
import com.spendsmart.goal.GoalDtos.UpdateGoalRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {

    private final SavingsGoalRepository goals;
    private final GoalContributionRepository contributions;
    private final Clock clock;

    public GoalService(SavingsGoalRepository goals, GoalContributionRepository contributions, Clock clock) {
        this.goals = goals;
        this.contributions = contributions;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<GoalResponse> list(UUID userId) {
        List<SavingsGoal> all = goals.findByUserIdOrderByCreatedAtAsc(userId);
        if (all.isEmpty()) {
            return List.of();
        }
        Map<UUID, GoalSaved> saved = contributions.savedAmounts(userId, all.stream().map(SavingsGoal::getId).toList())
                .stream().collect(Collectors.toMap(GoalSaved::getGoalId, Function.identity()));
        return all.stream().map(g -> {
            GoalSaved s = saved.get(g.getId());
            return toResponse(g, s == null ? BigDecimal.ZERO : s.getSaved(), s == null ? 0 : s.getCount(),
                    s == null ? null : s.getLastDate());
        }).toList();
    }

    @Transactional(readOnly = true)
    public GoalResponse get(UUID userId, UUID id) {
        return response(userId, require(userId, id));
    }

    @Transactional
    public GoalResponse create(UUID userId, CreateGoalRequest request) {
        String currency = Money.requireCurrency(request.currency(), "currency");
        SavingsGoal goal = new SavingsGoal(userId, currency, false);
        apply(goal, request.name(), request.targetAmount(), request.targetDate(), request.notes());
        SavingsGoal saved = goals.saveAndFlush(goal);
        if (request.initialAmount() != null && request.initialAmount().signum() != 0) {
            BigDecimal initial = Money.requirePositiveAmount(request.initialAmount(), currency, "initialAmount");
            contributions.save(new GoalContribution(saved.getId(), userId, GoalContribution.Type.DEPOSIT, initial,
                    LocalDate.now(clock), "Amount already saved when the goal was created"));
        }
        return response(userId, saved);
    }

    @Transactional
    public GoalResponse update(UUID userId, UUID id, UpdateGoalRequest request) {
        SavingsGoal goal = require(userId, id);
        if (request.version() != goal.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(SavingsGoal.class, id);
        }
        apply(goal, request.name(), request.targetAmount(), request.targetDate(), request.notes());
        return response(userId, goals.saveAndFlush(goal));
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        SavingsGoal goal = require(userId, id);
        contributions.deleteForGoal(goal.getId(), userId);
        goals.deleteById(goal.getId());
    }

    @Transactional(readOnly = true)
    public List<ContributionResponse> contributions(UUID userId, UUID goalId) {
        SavingsGoal goal = require(userId, goalId);
        return contributions.findByGoalIdAndUserIdOrderByContributionDateDescCreatedAtDesc(goalId, userId).stream()
                .map(c -> toResponse(c, goal.getCurrency()))
                .toList();
    }

    @Transactional
    public GoalResponse addContribution(UUID userId, UUID goalId, ContributionRequest request) {
        // Locking the goal row serialises contributions so two withdrawals cannot overdraw it together.
        SavingsGoal goal = goals.lockByIdAndUserId(goalId, userId).orElseThrow(() -> ApiException.notFound("Savings goal"));
        BigDecimal amount = Money.requirePositiveAmount(request.amount(), goal.getCurrency(), "amount");
        if (request.date().isAfter(LocalDate.now(clock))) {
            throw new FieldValidationException("date", "Contributions can't be dated in the future.");
        }
        if (request.type() == GoalContribution.Type.WITHDRAWAL) {
            BigDecimal saved = contributions.savedAmount(goalId, userId);
            if (amount.compareTo(saved) > 0) {
                throw new FieldValidationException("amount", "You can withdraw at most "
                        + Money.scale(saved, goal.getCurrency()).toPlainString() + " " + goal.getCurrency() + ".");
            }
        }
        contributions.save(new GoalContribution(goalId, userId, request.type(), amount, request.date(),
                Text.clean(request.note())));
        return response(userId, goal);
    }

    @Transactional
    public GoalResponse deleteContribution(UUID userId, UUID goalId, UUID contributionId) {
        SavingsGoal goal = goals.lockByIdAndUserId(goalId, userId).orElseThrow(() -> ApiException.notFound("Savings goal"));
        GoalContribution contribution = contributions.findByIdAndGoalIdAndUserId(contributionId, goalId, userId)
                .orElseThrow(() -> ApiException.notFound("Contribution"));
        if (contribution.getType() == GoalContribution.Type.DEPOSIT) {
            BigDecimal after = contributions.savedAmount(goalId, userId).subtract(contribution.getAmount());
            if (after.signum() < 0) {
                throw ApiException.conflict("GOAL_WOULD_GO_NEGATIVE",
                        "Removing this deposit would make the saved amount negative. Remove a later withdrawal first.");
            }
        }
        contributions.delete(contribution);
        contributions.flush();
        return response(userId, goal);
    }

    private SavingsGoal require(UUID userId, UUID id) {
        return goals.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Savings goal"));
    }

    private void apply(SavingsGoal goal, String rawName, BigDecimal target, LocalDate targetDate, String notes) {
        String name = Text.clean(rawName);
        if (name == null) {
            throw new FieldValidationException("name", "Enter a goal name.");
        }
        goal.setName(name);
        goal.setTargetAmount(Money.requirePositiveAmount(target, goal.getCurrency(), "targetAmount"));
        if (targetDate != null && targetDate.isAfter(LocalDate.now(clock).plusYears(100))) {
            throw new FieldValidationException("targetDate", "Choose a date within the next 100 years.");
        }
        goal.setTargetDate(targetDate);
        goal.setNotes(Text.cleanMultiline(notes));
    }

    private GoalResponse response(UUID userId, SavingsGoal goal) {
        List<GoalSaved> rows = contributions.savedAmounts(userId, List.of(goal.getId()));
        GoalSaved s = rows.isEmpty() ? null : rows.getFirst();
        return toResponse(goal, s == null ? BigDecimal.ZERO : s.getSaved(), s == null ? 0 : s.getCount(),
                s == null ? null : s.getLastDate());
    }

    GoalResponse toResponse(SavingsGoal g, BigDecimal savedRaw, long count, LocalDate lastDate) {
        String currency = g.getCurrency();
        BigDecimal saved = Money.scale(savedRaw, currency);
        BigDecimal target = Money.scale(g.getTargetAmount(), currency);
        BigDecimal remaining = target.subtract(saved).max(Money.scale(BigDecimal.ZERO, currency));
        BigDecimal percent = Percent.of(saved, target);
        LocalDate today = LocalDate.now(clock);
        BigDecimal monthlyNeeded = null;
        Long daysLeft = null;
        if (g.getTargetDate() != null) {
            daysLeft = ChronoUnit.DAYS.between(today, g.getTargetDate());
            if (daysLeft > 0 && remaining.signum() > 0) {
                // Count the current partial month as one so the figure is never a division by zero.
                long months = Math.max(1, ChronoUnit.MONTHS.between(today, g.getTargetDate()) + 1);
                monthlyNeeded = remaining.divide(BigDecimal.valueOf(months), Money.minorUnits(currency), RoundingMode.UP);
            }
        }
        return new GoalResponse(g.getId(), g.getName(), target, currency, g.getTargetDate(), g.getNotes(), saved,
                remaining, percent, Percent.clampForBar(percent), saved.compareTo(target) >= 0, count, lastDate,
                monthlyNeeded, daysLeft, g.isSample(), g.getVersion());
    }

    private static ContributionResponse toResponse(GoalContribution c, String currency) {
        return new ContributionResponse(c.getId(), c.getType(), Money.scale(c.getAmount(), currency),
                c.getContributionDate(), c.getNote(), c.getCreatedAt());
    }
}
