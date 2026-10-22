package com.spendsmart.notification;

import com.spendsmart.budget.BudgetCalculator.Status;
import com.spendsmart.budget.BudgetDtos.BudgetMonthResponse;
import com.spendsmart.budget.BudgetDtos.LineResponse;
import com.spendsmart.budget.BudgetDtos.MeasureResponse;
import com.spendsmart.budget.BudgetService;
import com.spendsmart.common.Money;
import com.spendsmart.common.SpendingChangedEvent;
import com.spendsmart.user.User;
import com.spendsmart.user.UserLookup;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates in-app budget alerts after spending or limits change.
 *
 * <ul>
 *   <li>Runs after the triggering change commits, in its own transaction, so an alert failure can never roll back
 *       or fail the user's request.</li>
 *   <li>Only the current calendar month is evaluated — back-dated edits to closed months don't raise alerts.</li>
 *   <li>Deduplicated by (budget line, month, level) via a unique key: each line alerts at most once at the
 *       threshold and once when exceeded, per month.</li>
 * </ul>
 */
@Service
public class BudgetAlertService {

    private static final Logger log = LoggerFactory.getLogger(BudgetAlertService.class);
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);

    private final BudgetService budgetService;
    private final NotificationRepository notifications;
    private final UserLookup userLookup;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    public BudgetAlertService(BudgetService budgetService, NotificationRepository notifications, UserLookup userLookup,
                              Clock clock, PlatformTransactionManager transactionManager) {
        this.budgetService = budgetService;
        this.notifications = notifications;
        this.userLookup = userLookup;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSpendingChanged(SpendingChangedEvent event) {
        try {
            // Own transaction, committed inside the try block, so even a commit failure is contained here.
            newTransaction.executeWithoutResult(status -> {
                User user = userLookup.require(event.userId());
                if (!user.isBudgetAlertsEnabled()) {
                    return;
                }
                YearMonth current = YearMonth.now(clock);
                event.affected().stream()
                        .filter(p -> p.month().equals(current))
                        .forEach(p -> evaluate(user, budgetService.status(user.getId(), p.month(), p.currency())));
            });
        } catch (RuntimeException e) {
            // Never propagate: the user's change has already been saved. Log ids only, no financial values.
            log.warn("Budget alert evaluation failed for user {}: {}", event.userId(), e.getClass().getSimpleName());
        }
    }

    void evaluate(User user, BudgetMonthResponse status) {
        if (!status.planExists()) {
            return;
        }
        String scope = status.month() + ":" + status.currency();
        Locale locale = Locale.forLanguageTag(user.getLocale());
        String monthLabel = status.month().format(MONTH);
        String link = "/budgets?month=" + status.month() + "&currency=" + status.currency();
        alertFor(user.getId(), "TOTAL:" + scope, "Overall budget", status.total(), status.currency(), locale,
                monthLabel, link, status.alertThreshold());
        for (LineResponse line : status.lines()) {
            alertFor(user.getId(), "CAT:" + line.category().id() + ":" + scope, line.category().name(), line.measure(),
                    status.currency(), locale, monthLabel, link, status.alertThreshold());
        }
    }

    private void alertFor(UUID userId, String key, String label, MeasureResponse m, String currency, Locale locale,
                          String monthLabel, String link, int threshold) {
        if (m.status() == Status.EXCEEDED) {
            create(userId, Notification.Type.BUDGET_EXCEEDED, "EXCEEDED:" + key,
                    label + " budget exceeded",
                    "You have spent " + Money.format(m.spent(), currency, locale) + " of your "
                            + Money.format(m.limit(), currency, locale) + " " + label.toLowerCase(Locale.ROOT)
                            + " budget for " + monthLabel + " — " + Money.format(m.overBy(), currency, locale) + " over.",
                    link);
        } else if (m.status() == Status.WARNING) {
            create(userId, Notification.Type.BUDGET_THRESHOLD, "THRESHOLD:" + key,
                    label + " budget at " + m.percentUsed().stripTrailingZeros().toPlainString() + "%",
                    "You have used " + m.percentUsed().stripTrailingZeros().toPlainString() + "% of your "
                            + label.toLowerCase(Locale.ROOT) + " budget for " + monthLabel + " (alert threshold "
                            + threshold + "%). " + Money.format(m.remaining(), currency, locale) + " remains.",
                    link);
        }
    }

    private void create(UUID userId, Notification.Type type, String dedupeKey, String title, String message, String link) {
        String key = dedupeKey.length() > 160 ? dedupeKey.substring(0, 160) : dedupeKey;
        if (notifications.existsByUserIdAndDedupeKey(userId, key)) {
            return;
        }
        // If a concurrent request inserts the same key first, the unique constraint rejects this insert and the
        // evaluation transaction rolls back harmlessly (caught in onSpendingChanged): deduplication still holds.
        notifications.saveAndFlush(new Notification(userId, type, truncate(title, 120), truncate(message, 300),
                link, key));
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}
