package com.spendsmart.analytics;

import com.spendsmart.analytics.AnalyticsDtos.CategorySlice;
import com.spendsmart.analytics.AnalyticsDtos.Comparison;
import com.spendsmart.analytics.AnalyticsDtos.Granularity;
import com.spendsmart.analytics.AnalyticsDtos.MonthPoint;
import com.spendsmart.analytics.AnalyticsDtos.Overview;
import com.spendsmart.analytics.AnalyticsDtos.Range;
import com.spendsmart.analytics.AnalyticsDtos.RecurringPattern;
import com.spendsmart.analytics.AnalyticsDtos.Totals;
import com.spendsmart.analytics.AnalyticsDtos.TrendPoint;
import com.spendsmart.budget.BudgetDtos.BudgetMonthResponse;
import com.spendsmart.budget.BudgetService;
import com.spendsmart.common.DateRange;
import com.spendsmart.common.Money;
import com.spendsmart.common.Percent;
import com.spendsmart.common.TransactionType;
import com.spendsmart.transaction.Transaction;
import com.spendsmart.transaction.TransactionRepository;
import com.spendsmart.transaction.TransactionRepository.CategoryTotal;
import com.spendsmart.transaction.TransactionRepository.DailyTotal;
import com.spendsmart.transaction.TransactionRepository.TypeTotal;
import com.spendsmart.user.User;
import com.spendsmart.user.UserClock;
import com.spendsmart.user.UserLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every figure is computed on request from persisted transactions, for exactly one currency and one inclusive date
 * range. Nothing here is cached or stored.
 */
@Service
public class AnalyticsService {

    private static final int RECURRING_LOOKBACK_DAYS = 183;

    private final TransactionRepository transactions;
    private final BudgetService budgetService;
    private final UserLookup userLookup;
    private final UserClock userClock;

    public AnalyticsService(TransactionRepository transactions, BudgetService budgetService, UserLookup userLookup,
                            UserClock userClock) {
        this.transactions = transactions;
        this.budgetService = budgetService;
        this.userLookup = userLookup;
        this.userClock = userClock;
    }

    @Transactional(readOnly = true)
    public Overview overview(UUID userId, DateRange range, String rawCurrency) {
        String currency = Money.requireCurrency(rawCurrency, "currency");
        User user = userLookup.require(userId);
        DateRange previous = range.previous();

        Totals current = totals(userId, currency, range);
        Totals prior = totals(userId, currency, previous);
        Comparison comparison = new Comparison(
                Percent.change(current.income(), prior.income()),
                Percent.change(current.expenses(), prior.expenses()),
                current.net().subtract(prior.net()));

        List<CategorySlice> expenseSlices = slices(userId, currency, TransactionType.EXPENSE, range, previous, current.expenses());
        List<CategorySlice> incomeSlices = slices(userId, currency, TransactionType.INCOME, range, previous, current.income());

        Granularity granularity = range.days() <= 62 ? Granularity.DAY : range.days() <= 366 ? Granularity.WEEK : Granularity.MONTH;
        List<TrendPoint> trend = trend(transactions.dailyTotals(userId, currency, range.from(), range.to()), range,
                granularity, currency);

        BigDecimal averageDaily = current.expenses().divide(BigDecimal.valueOf(range.days()), Money.minorUnits(currency),
                RoundingMode.HALF_UP);
        BigDecimal savingsRate = current.income().signum() > 0 ? Percent.of(current.net(), current.income()) : null;

        List<Transaction> expenses = transactions.findByUserIdAndCurrencyAndTypeAndTransactionDateBetweenOrderByTransactionDateAsc(
                userId, currency, TransactionType.EXPENSE, range.from(), range.to());
        InsightEngine.LargestExpense largest = expenses.stream()
                .max(Comparator.comparing(Transaction::getAmount))
                .map(t -> new InsightEngine.LargestExpense(t.getDescription(), Money.scale(t.getAmount(), currency),
                        t.getTransactionDate()))
                .orElse(null);

        BudgetMonthResponse budget = budgetForRange(userId, range, currency);
        List<RecurringPattern> recurring = recurring(userId, currency, range.to());

        var insights = InsightEngine.generate(new InsightEngine.Input(currency, Locale.forLanguageTag(user.getLocale()),
                range.isSingleCalendarMonth(), current, prior, expenseSlices, largest, budget, recurring));

        return new Overview(currency, toRange(range), toRange(previous), current, prior, comparison, savingsRate,
                averageDaily, expenseSlices, incomeSlices, granularity, trend, insights);
    }

    /** Calendar-month totals for the last {@code months} months up to and including the current month, oldest first. */
    @Transactional(readOnly = true)
    public List<MonthPoint> monthly(UUID userId, String rawCurrency, int months) {
        String currency = Money.requireCurrency(rawCurrency, "currency");
        int span = Math.max(2, Math.min(months, 24));
        YearMonth last = userClock.currentMonth(userLookup.require(userId));
        YearMonth first = last.minusMonths(span - 1L);
        // One extra month before the window, so the first point also has a month-over-month comparison.
        YearMonth baseline = first.minusMonths(1);
        Map<YearMonth, BigDecimal[]> sums = new HashMap<>();
        for (DailyTotal row : transactions.dailyTotals(userId, currency, baseline.atDay(1), last.atEndOfMonth())) {
            BigDecimal[] pair = sums.computeIfAbsent(YearMonth.from(row.getDate()), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            int idx = row.getType() == TransactionType.INCOME ? 0 : 1;
            pair[idx] = pair[idx].add(row.getTotal());
        }
        List<MonthPoint> points = new ArrayList<>();
        BigDecimal previousExpenses = sums.getOrDefault(baseline, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO})[1];
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            BigDecimal[] pair = sums.getOrDefault(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            BigDecimal income = Money.scale(pair[0], currency);
            BigDecimal expenses = Money.scale(pair[1], currency);
            points.add(new MonthPoint(m, income, expenses, income.subtract(expenses),
                    Percent.change(expenses, previousExpenses)));
            previousExpenses = pair[1];
        }
        return points;
    }

    @Transactional(readOnly = true)
    public List<RecurringPattern> recurring(UUID userId, String rawCurrency, LocalDate asOf) {
        String currency = Money.requireCurrency(rawCurrency, "currency");
        LocalDate end = asOf == null ? userClock.today(userLookup.require(userId)) : asOf;
        List<Transaction> expenses = transactions.findByUserIdAndCurrencyAndTypeAndTransactionDateBetweenOrderByTransactionDateAsc(
                userId, currency, TransactionType.EXPENSE, end.minusDays(RECURRING_LOOKBACK_DAYS), end);
        return RecurringDetector.detect(expenses, currency);
    }

    /**
     * Budgets are monthly, so the dashboard shows the budget for the calendar month containing the range's end date
     * (capped at the current month). The response says which month that is.
     */
    @Transactional(readOnly = true)
    public BudgetMonthResponse budgetForRange(UUID userId, DateRange range, String currency) {
        YearMonth month = YearMonth.from(range.to());
        YearMonth now = userClock.currentMonth(userLookup.require(userId));
        if (month.isAfter(now)) {
            month = now;
        }
        return budgetService.status(userId, month, currency);
    }

    Totals totals(UUID userId, String currency, DateRange range) {
        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expenses = BigDecimal.ZERO;
        long incomeCount = 0;
        long expenseCount = 0;
        for (TypeTotal row : transactions.totalsByType(userId, currency, range.from(), range.to())) {
            if (row.getType() == TransactionType.INCOME) {
                income = row.getTotal();
                incomeCount = row.getCount();
            } else {
                expenses = row.getTotal();
                expenseCount = row.getCount();
            }
        }
        BigDecimal in = Money.scale(income, currency);
        BigDecimal out = Money.scale(expenses, currency);
        return new Totals(in, out, in.subtract(out), incomeCount, expenseCount);
    }

    private List<CategorySlice> slices(UUID userId, String currency, TransactionType type, DateRange range,
                                       DateRange previous, BigDecimal periodTotal) {
        List<CategoryTotal> now = transactions.totalsByCategory(userId, currency, type, range.from(), range.to());
        Map<UUID, BigDecimal> before = transactions.totalsByCategory(userId, currency, type, previous.from(), previous.to())
                .stream().collect(Collectors.toMap(CategoryTotal::getCategoryId, CategoryTotal::getTotal));
        return now.stream().map(c -> {
            BigDecimal total = Money.scale(c.getTotal(), currency);
            BigDecimal prev = Money.scale(before.getOrDefault(c.getCategoryId(), BigDecimal.ZERO), currency);
            return new CategorySlice(c.getCategoryId(), c.getCategoryName(), c.getIcon(), total, c.getCount(),
                    Percent.of(total, periodTotal), prev, Percent.change(total, prev));
        }).toList();
    }

    static List<TrendPoint> trend(List<DailyTotal> rows, DateRange range, Granularity granularity, String currency) {
        Map<LocalDate, BigDecimal[]> byDay = new HashMap<>();
        for (DailyTotal row : rows) {
            BigDecimal[] pair = byDay.computeIfAbsent(row.getDate(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            int idx = row.getType() == TransactionType.INCOME ? 0 : 1;
            pair[idx] = pair[idx].add(row.getTotal());
        }
        List<TrendPoint> points = new ArrayList<>();
        LocalDate cursor = range.from();
        while (!cursor.isAfter(range.to())) {
            LocalDate end = switch (granularity) {
                case DAY -> cursor;
                case WEEK -> cursor.plusDays(6);
                case MONTH -> YearMonth.from(cursor).atEndOfMonth();
            };
            if (end.isAfter(range.to())) {
                end = range.to();
            }
            BigDecimal income = BigDecimal.ZERO;
            BigDecimal expenses = BigDecimal.ZERO;
            for (LocalDate d = cursor; !d.isAfter(end); d = d.plusDays(1)) {
                BigDecimal[] pair = byDay.get(d);
                if (pair != null) {
                    income = income.add(pair[0]);
                    expenses = expenses.add(pair[1]);
                }
            }
            points.add(new TrendPoint(cursor, end, Money.scale(income, currency), Money.scale(expenses, currency)));
            cursor = end.plusDays(1);
        }
        return points;
    }

    private static Range toRange(DateRange r) {
        return new Range(r.from(), r.to(), r.days());
    }
}
