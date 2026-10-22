package com.spendsmart.export;

import com.spendsmart.budget.MonthlyBudgetRepository;
import com.spendsmart.category.CategoryRepository;
import com.spendsmart.common.Money;
import com.spendsmart.goal.GoalContributionRepository;
import com.spendsmart.goal.SavingsGoalRepository;
import com.spendsmart.notification.NotificationRepository;
import com.spendsmart.transaction.TransactionDtos.TransactionResponse;
import com.spendsmart.transaction.TransactionRepository;
import com.spendsmart.user.UserLookup;
import com.spendsmart.user.UserResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Machine-readable copy of everything stored for one account (data portability). Excludes the password hash. */
@Service
public class AccountExportService {

    private final UserLookup userLookup;
    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final MonthlyBudgetRepository budgets;
    private final SavingsGoalRepository goals;
    private final GoalContributionRepository contributions;
    private final NotificationRepository notifications;
    private final Clock clock;

    public AccountExportService(UserLookup userLookup, CategoryRepository categories, TransactionRepository transactions,
                                MonthlyBudgetRepository budgets, SavingsGoalRepository goals,
                                GoalContributionRepository contributions, NotificationRepository notifications,
                                Clock clock) {
        this.userLookup = userLookup;
        this.categories = categories;
        this.transactions = transactions;
        this.budgets = budgets;
        this.goals = goals;
        this.contributions = contributions;
        this.notifications = notifications;
        this.clock = clock;
    }

    public record ExportedCategory(UUID id, String name, String type, String icon, boolean isDefault, boolean archived) {
    }

    public record ExportedBudgetLine(UUID categoryId, String categoryName, BigDecimal limit) {
    }

    public record ExportedBudget(YearMonth month, String currency, BigDecimal totalLimit, boolean sample,
                                 List<ExportedBudgetLine> lines) {
    }

    public record ExportedContribution(String type, BigDecimal amount, LocalDate date, String note) {
    }

    public record ExportedGoal(UUID id, String name, BigDecimal targetAmount, String currency, LocalDate targetDate,
                               String notes, boolean sample, List<ExportedContribution> contributions) {
    }

    public record ExportedNotification(String type, String title, String message, Instant createdAt, Instant readAt) {
    }

    public record AccountExport(String format, Instant exportedAt, UserResponse profile,
                                List<ExportedCategory> categories, List<TransactionResponse> transactions,
                                List<ExportedBudget> budgets, List<ExportedGoal> savingsGoals,
                                List<ExportedNotification> notifications) {
    }

    @Transactional(readOnly = true)
    public AccountExport export(UUID userId) {
        var allContributions = contributions.findByUserIdOrderByContributionDateAsc(userId);
        return new AccountExport(
                "spendsmart-account-export/v1",
                Instant.now(clock),
                UserResponse.of(userLookup.require(userId)),
                categories.findByUserIdOrderByTypeAscNameAsc(userId).stream()
                        .map(c -> new ExportedCategory(c.getId(), c.getName(), c.getType().name(), c.getIcon(),
                                c.isDefaultCategory(), c.isArchived()))
                        .toList(),
                transactions.findByUserIdOrderByTransactionDateAscCreatedAtAsc(userId).stream()
                        .map(TransactionResponse::of).toList(),
                budgets.findAllForUser(userId).stream()
                        .map(b -> new ExportedBudget(b.getMonth(), b.getCurrency(),
                                b.getTotalLimit() == null ? null : Money.scale(b.getTotalLimit(), b.getCurrency()),
                                b.isSample(),
                                b.getLines().stream().map(l -> new ExportedBudgetLine(l.getCategory().getId(),
                                        l.getCategory().getName(), Money.scale(l.getLimitAmount(), b.getCurrency()))).toList()))
                        .toList(),
                goals.findByUserIdOrderByCreatedAtAsc(userId).stream()
                        .map(g -> new ExportedGoal(g.getId(), g.getName(), Money.scale(g.getTargetAmount(), g.getCurrency()),
                                g.getCurrency(), g.getTargetDate(), g.getNotes(), g.isSample(),
                                allContributions.stream().filter(c -> c.getGoalId().equals(g.getId()))
                                        .map(c -> new ExportedContribution(c.getType().name(),
                                                Money.scale(c.getAmount(), g.getCurrency()), c.getContributionDate(), c.getNote()))
                                        .toList()))
                        .toList(),
                notifications.findByUserIdOrderByCreatedAtAsc(userId).stream()
                        .map(n -> new ExportedNotification(n.getType().name(), n.getTitle(), n.getMessage(),
                                n.getCreatedAt(), n.getReadAt()))
                        .toList());
    }
}
