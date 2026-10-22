package com.spendsmart.analytics;

import com.spendsmart.analytics.AnalyticsDtos.MonthPoint;
import com.spendsmart.analytics.AnalyticsDtos.Overview;
import com.spendsmart.budget.BudgetDtos.BudgetMonthResponse;
import com.spendsmart.common.DateRange;
import com.spendsmart.common.Money;
import com.spendsmart.goal.GoalDtos.GoalResponse;
import com.spendsmart.goal.GoalService;
import com.spendsmart.sample.SampleDataService;
import com.spendsmart.transaction.TransactionDtos.TransactionResponse;
import com.spendsmart.transaction.TransactionRepository;
import com.spendsmart.user.User;
import com.spendsmart.user.UserLookup;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final AnalyticsService analytics;
    private final TransactionRepository transactions;
    private final GoalService goals;
    private final SampleDataService sampleData;
    private final UserLookup userLookup;

    public DashboardService(AnalyticsService analytics, TransactionRepository transactions, GoalService goals,
                            SampleDataService sampleData, UserLookup userLookup) {
        this.analytics = analytics;
        this.transactions = transactions;
        this.goals = goals;
        this.sampleData = sampleData;
        this.userLookup = userLookup;
    }

    /**
     * @param hasAnyTransactions false only for a brand-new account (drives the first-run empty state)
     * @param currencies         currencies the user has records in, plus their default
     */
    public record Dashboard(Overview overview, BudgetMonthResponse budget, List<TransactionResponse> recentTransactions,
                            List<GoalResponse> goals, List<MonthPoint> monthly, boolean hasAnyTransactions,
                            boolean sampleDataPresent, List<String> currencies) {
    }

    @Transactional(readOnly = true)
    public Dashboard build(UUID userId, DateRange range, String rawCurrency) {
        User user = userLookup.require(userId);
        String currency = rawCurrency == null || rawCurrency.isBlank() ? user.getDefaultCurrency()
                : Money.requireCurrency(rawCurrency, "currency");
        Overview overview = analytics.overview(userId, range, currency);
        BudgetMonthResponse budget = analytics.budgetForRange(userId, range, currency);
        List<TransactionResponse> recent = transactions
                .findTop6ByUserIdAndCurrencyOrderByTransactionDateDescCreatedAtDesc(userId, currency).stream()
                .map(TransactionResponse::of).toList();
        List<GoalResponse> goalList = goals.list(userId).stream()
                .filter(g -> g.currency().equals(currency))
                .sorted(Comparator.comparing(GoalResponse::completed)
                        .thenComparing(GoalResponse::progressPercent, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .toList();
        TreeSet<String> currencies = new TreeSet<>(transactions.distinctCurrencies(userId));
        currencies.add(user.getDefaultCurrency());
        return new Dashboard(overview, budget, recent, goalList, analytics.monthly(userId, currency, 6),
                transactions.existsByUserId(userId), sampleData.isPresent(userId), List.copyOf(currencies));
    }
}
