package com.spendsmart.sample;

import com.spendsmart.budget.CategoryBudget;
import com.spendsmart.budget.CategoryBudgetRepository;
import com.spendsmart.budget.MonthlyBudget;
import com.spendsmart.budget.MonthlyBudgetRepository;
import com.spendsmart.category.Category;
import com.spendsmart.category.CategoryRepository;
import com.spendsmart.common.ApiException;
import com.spendsmart.common.Money;
import com.spendsmart.common.TransactionType;
import com.spendsmart.goal.GoalContribution;
import com.spendsmart.goal.GoalContributionRepository;
import com.spendsmart.goal.SavingsGoal;
import com.spendsmart.goal.SavingsGoalRepository;
import com.spendsmart.transaction.PaymentMethod;
import com.spendsmart.transaction.Transaction;
import com.spendsmart.transaction.TransactionRepository;
import com.spendsmart.user.User;
import com.spendsmart.user.UserLookup;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opt-in sample data for exploring the app. Every row it creates is flagged {@code is_sample}; the UI shows a
 * permanent banner while any exist, CSV exports mark them, and {@link #remove} deletes exactly those rows.
 * The figures are invented examples — plausible, not anyone's real finances.
 */
@Service
public class SampleDataService {

    /** Rough magnitude per currency so sample amounts look plausible. These are not exchange rates. */
    private static final Map<String, BigDecimal> SCALE = Map.ofEntries(
            Map.entry("INR", BigDecimal.ONE), Map.entry("JPY", new BigDecimal("1.8")), Map.entry("KWD", new BigDecimal("0.0037")),
            Map.entry("USD", new BigDecimal("0.012")), Map.entry("EUR", new BigDecimal("0.011")), Map.entry("GBP", new BigDecimal("0.0095")),
            Map.entry("AUD", new BigDecimal("0.018")), Map.entry("CAD", new BigDecimal("0.016")), Map.entry("SGD", new BigDecimal("0.016")),
            Map.entry("AED", new BigDecimal("0.044")), Map.entry("CHF", new BigDecimal("0.011")), Map.entry("CNY", new BigDecimal("0.086")),
            Map.entry("HKD", new BigDecimal("0.094")), Map.entry("NZD", new BigDecimal("0.02")), Map.entry("SEK", new BigDecimal("0.13")),
            Map.entry("NOK", new BigDecimal("0.13")), Map.entry("DKK", new BigDecimal("0.082")), Map.entry("ZAR", new BigDecimal("0.22")),
            Map.entry("BRL", new BigDecimal("0.066")), Map.entry("MXN", new BigDecimal("0.22")));

    private final TransactionRepository transactions;
    private final CategoryRepository categories;
    private final MonthlyBudgetRepository budgets;
    private final CategoryBudgetRepository categoryBudgets;
    private final SavingsGoalRepository goals;
    private final GoalContributionRepository contributions;
    private final UserLookup userLookup;
    private final Clock clock;

    public SampleDataService(TransactionRepository transactions, CategoryRepository categories,
                             MonthlyBudgetRepository budgets, CategoryBudgetRepository categoryBudgets,
                             SavingsGoalRepository goals, GoalContributionRepository contributions,
                             UserLookup userLookup, Clock clock) {
        this.transactions = transactions;
        this.categories = categories;
        this.budgets = budgets;
        this.categoryBudgets = categoryBudgets;
        this.goals = goals;
        this.contributions = contributions;
        this.userLookup = userLookup;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public boolean isPresent(UUID userId) {
        return transactions.existsByUserIdAndSampleTrue(userId) || budgets.existsByUserIdAndSampleTrue(userId)
                || goals.existsByUserIdAndSampleTrue(userId);
    }

    public record SampleSummary(int transactions, boolean budgetCreated, boolean goalCreated, String currency) {
    }

    @Transactional
    public SampleSummary load(UUID userId) {
        if (isPresent(userId)) {
            throw ApiException.conflict("SAMPLE_DATA_EXISTS", "Sample data is already loaded. Remove it first.");
        }
        User user = userLookup.require(userId);
        String currency = user.getDefaultCurrency();
        Generator g = new Generator(userId, currency, categoryLookup(userId));
        LocalDate today = LocalDate.now(clock);
        YearMonth current = YearMonth.from(today);
        Random random = new Random(42); // fixed seed: the same sample every time

        for (int back = 2; back >= 0; back--) {
            YearMonth m = current.minusMonths(back);
            g.add(m.atDay(1), TransactionType.INCOME, "Salary", "Monthly salary", "Employer payroll", PaymentMethod.BANK_TRANSFER, 52000);
            if (back != 1) {
                g.add(m.atDay(Math.min(18, m.lengthOfMonth())), TransactionType.INCOME, "Freelance Income",
                        "Website project milestone", null, PaymentMethod.BANK_TRANSFER, 8000 + random.nextInt(4000));
            }
            g.add(m.atDay(3), TransactionType.EXPENSE, "Housing", "Rent", "Landlord", PaymentMethod.BANK_TRANSFER, 15000);
            g.add(m.atDay(5), TransactionType.EXPENSE, "Utilities", "Electricity bill", "City Power", PaymentMethod.UPI, 1400 + random.nextInt(500));
            g.add(m.atDay(7), TransactionType.EXPENSE, "Utilities", "Mobile and internet", "Telecom Co", PaymentMethod.UPI, 799);
            g.add(m.atDay(10), TransactionType.EXPENSE, "Subscriptions", "Video streaming plan", "StreamBox", PaymentMethod.CREDIT_CARD, 649);
            g.add(m.atDay(12), TransactionType.EXPENSE, "Subscriptions", "Music streaming", "TuneHub", PaymentMethod.CREDIT_CARD, 119);
            for (int week = 0; week < 4; week++) {
                int day = 2 + week * 7;
                g.add(m.atDay(day), TransactionType.EXPENSE, "Groceries", "Weekly groceries", "FreshMart",
                        PaymentMethod.DEBIT_CARD, 1600 + random.nextInt(900));
                g.add(m.atDay(day + 2), TransactionType.EXPENSE, "Transportation", "Metro card top-up", "City Metro",
                        PaymentMethod.UPI, 500);
            }
            int dining = back == 0 ? 7 : 5; // the current month has more dining, so insights have something to say
            for (int i = 0; i < dining; i++) {
                g.add(m.atDay(4 + i * 3), TransactionType.EXPENSE, "Food and Dining",
                        i % 2 == 0 ? "Dinner with friends" : "Lunch near office", i % 2 == 0 ? "Spice Route" : "Cafe Corner",
                        PaymentMethod.UPI, 450 + random.nextInt(900));
            }
            g.add(m.atDay(15), TransactionType.EXPENSE, "Shopping", "Clothing", "Urban Threads", PaymentMethod.CREDIT_CARD, 1200 + random.nextInt(2500));
            g.add(m.atDay(20), TransactionType.EXPENSE, "Entertainment", "Movie tickets", "Cineplex", PaymentMethod.UPI, 600 + random.nextInt(300));
            if (back == 2) {
                g.add(m.atDay(22), TransactionType.EXPENSE, "Healthcare", "Pharmacy", "Wellness Pharmacy", PaymentMethod.CASH, 850);
            }
            if (back == 1) {
                g.add(m.atDay(24), TransactionType.EXPENSE, "Travel", "Weekend trip train tickets", "Rail booking", PaymentMethod.UPI, 3200);
            }
            if (back == 0) {
                g.add(m.atDay(9), TransactionType.EXPENSE, "Education", "Online course", "LearnWell", PaymentMethod.CREDIT_CARD, 2499);
            }
        }
        // Only past and present: sample data never invents future spending.
        List<Transaction> rows = g.rows.stream().filter(t -> !t.getTransactionDate().isAfter(today)).toList();
        transactions.saveAll(rows);

        boolean budgetCreated = false;
        if (budgets.findPlan(userId, current.atDay(1), currency).isEmpty()) {
            MonthlyBudget plan = new MonthlyBudget(userId, current, currency, true);
            plan.setTotalLimit(g.amount(42000));
            addLine(plan, g.category(TransactionType.EXPENSE, "Food and Dining"), g.amount(4000));
            addLine(plan, g.category(TransactionType.EXPENSE, "Groceries"), g.amount(8000));
            addLine(plan, g.category(TransactionType.EXPENSE, "Transportation"), g.amount(2500));
            addLine(plan, g.category(TransactionType.EXPENSE, "Shopping"), g.amount(3000));
            addLine(plan, g.category(TransactionType.EXPENSE, "Subscriptions"), g.amount(1000));
            budgets.save(plan);
            budgetCreated = true;
        }

        SavingsGoal goal = new SavingsGoal(userId, currency, true);
        goal.setName("Emergency fund");
        goal.setTargetAmount(g.amount(150000));
        goal.setTargetDate(current.plusMonths(10).atEndOfMonth());
        goal.setNotes("Sample goal: three months of essential expenses.");
        SavingsGoal savedGoal = goals.saveAndFlush(goal);
        for (int back = 2; back >= 0; back--) {
            LocalDate date = current.minusMonths(back).atDay(2);
            if (!date.isAfter(today)) {
                contributions.save(new GoalContribution(savedGoal.getId(), userId, GoalContribution.Type.DEPOSIT,
                        g.amount(10000), date, "Monthly transfer to savings"));
            }
        }
        return new SampleSummary(rows.size(), budgetCreated, true, currency);
    }

    @Transactional
    public void remove(UUID userId) {
        transactions.deleteSamples(userId);
        categoryBudgets.deleteSamples(userId);
        budgets.deleteSamples(userId);
        contributions.deleteSamples(userId);
        goals.deleteSamples(userId);
    }

    private static void addLine(MonthlyBudget plan, Category category, BigDecimal limit) {
        if (category != null && plan.getLines().stream().noneMatch(l -> l.getCategory().getId().equals(category.getId()))) {
            plan.getLines().add(new CategoryBudget(plan, category, limit));
        }
    }

    private Map<String, Category> categoryLookup(UUID userId) {
        Map<String, Category> map = new java.util.HashMap<>();
        for (Category c : categories.findByUserIdOrderByTypeAscNameAsc(userId)) {
            if (!c.isArchived()) {
                map.putIfAbsent(c.getType() + ":" + c.getNormalizedName(), c);
                map.putIfAbsent(c.getType() + ":*", c);
            }
        }
        return map;
    }

    private static final class Generator {
        private final UUID userId;
        private final String currency;
        private final Map<String, Category> categories;
        private final BigDecimal scale;
        private final List<Transaction> rows = new ArrayList<>();

        Generator(UUID userId, String currency, Map<String, Category> categories) {
            this.userId = userId;
            this.currency = currency;
            this.categories = categories;
            this.scale = SCALE.getOrDefault(currency, BigDecimal.ONE);
        }

        BigDecimal amount(int baseInr) {
            BigDecimal v = BigDecimal.valueOf(baseInr).multiply(scale).setScale(Money.minorUnits(currency), RoundingMode.HALF_UP);
            return v.signum() > 0 ? v : BigDecimal.ONE.movePointLeft(Money.minorUnits(currency));
        }

        /** Falls back to the user's "Other" (or any) category of that type if a default was renamed or archived. */
        Category category(TransactionType type, String name) {
            Category c = categories.get(type + ":" + name.toLowerCase(java.util.Locale.ROOT));
            if (c == null) {
                c = categories.get(type + ":other");
            }
            return c != null ? c : categories.get(type + ":*");
        }

        void add(LocalDate date, TransactionType type, String categoryName, String description, String merchant,
                 PaymentMethod method, int baseInr) {
            Category category = category(type, categoryName);
            if (category == null) {
                return;
            }
            Transaction t = new Transaction(userId, true);
            t.setType(type);
            t.setCategory(category);
            t.setAmount(amount(baseInr));
            t.setCurrency(currency);
            t.setDescription(description);
            t.setMerchant(merchant);
            t.setPaymentMethod(method);
            t.setTransactionDate(date);
            rows.add(t);
        }
    }
}
