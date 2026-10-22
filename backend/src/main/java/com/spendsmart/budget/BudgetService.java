package com.spendsmart.budget;

import com.spendsmart.budget.BudgetCalculator.LineInput;
import com.spendsmart.budget.BudgetCalculator.Measure;
import com.spendsmart.budget.BudgetCalculator.Result;
import com.spendsmart.budget.BudgetDtos.BudgetMonthResponse;
import com.spendsmart.budget.BudgetDtos.HistoryEntry;
import com.spendsmart.budget.BudgetDtos.LineRequest;
import com.spendsmart.budget.BudgetDtos.LineResponse;
import com.spendsmart.budget.BudgetDtos.MeasureResponse;
import com.spendsmart.budget.BudgetDtos.SaveBudgetRequest;
import com.spendsmart.category.Category;
import com.spendsmart.category.CategoryDtos.CategoryRef;
import com.spendsmart.category.CategoryService;
import com.spendsmart.common.ApiException;
import com.spendsmart.common.DateRange;
import com.spendsmart.common.FieldValidationException;
import com.spendsmart.common.FieldValidationException.FieldIssue;
import com.spendsmart.common.Money;
import com.spendsmart.common.SpendingChangedEvent;
import com.spendsmart.common.TransactionType;
import com.spendsmart.transaction.TransactionRepository;
import com.spendsmart.transaction.TransactionRepository.MonthlyCategorySum;
import com.spendsmart.user.User;
import com.spendsmart.user.UserClock;
import com.spendsmart.user.UserLookup;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BudgetService {

    private final MonthlyBudgetRepository budgets;
    private final TransactionRepository transactions;
    private final CategoryService categoryService;
    private final UserLookup userLookup;
    private final ApplicationEventPublisher events;
    private final UserClock userClock;

    public BudgetService(MonthlyBudgetRepository budgets, TransactionRepository transactions,
                         CategoryService categoryService, UserLookup userLookup, ApplicationEventPublisher events,
                         UserClock userClock) {
        this.budgets = budgets;
        this.transactions = transactions;
        this.categoryService = categoryService;
        this.userLookup = userLookup;
        this.events = events;
        this.userClock = userClock;
    }

    @Transactional(readOnly = true)
    public BudgetMonthResponse status(UUID userId, YearMonth month, String rawCurrency) {
        String currency = Money.requireCurrency(rawCurrency, "currency");
        User user = userLookup.require(userId);
        Optional<MonthlyBudget> plan = budgets.findPlan(userId, month.atDay(1), currency);
        return toResponse(user, month, currency, plan.orElse(null));
    }

    @Transactional
    public BudgetMonthResponse save(UUID userId, YearMonth month, SaveBudgetRequest request) {
        String currency = Money.requireCurrency(request.currency(), "currency");
        BigDecimal totalLimit = request.totalLimit() == null ? null
                : Money.requirePositiveAmount(request.totalLimit(), currency, "totalLimit");
        Map<UUID, BigDecimal> requestedLines = validateLines(userId, request.lines(), currency);
        if (totalLimit == null && requestedLines.isEmpty()) {
            throw new FieldValidationException("totalLimit", "Set an overall limit or at least one category limit.");
        }

        MonthlyBudget plan = budgets.findPlan(userId, month.atDay(1), currency)
                .orElseGet(() -> new MonthlyBudget(userId, month, currency, false));
        plan.setTotalLimit(totalLimit);
        // Update existing lines in place rather than delete-and-reinsert, which would trip the unique constraint
        // (Hibernate flushes inserts before orphan deletes).
        plan.getLines().removeIf(line -> !requestedLines.containsKey(line.getCategory().getId()));
        Map<UUID, CategoryBudget> existing = plan.getLines().stream()
                .collect(Collectors.toMap(l -> l.getCategory().getId(), Function.identity()));
        requestedLines.forEach((categoryId, limit) -> {
            CategoryBudget line = existing.get(categoryId);
            if (line != null) {
                line.setLimitAmount(limit);
            } else {
                plan.getLines().add(new CategoryBudget(plan, categoryService.require(userId, categoryId), limit));
            }
        });
        MonthlyBudget saved = budgets.save(plan);
        events.publishEvent(SpendingChangedEvent.of(userId, currency, month));
        return toResponse(userLookup.require(userId), month, currency, saved);
    }

    @Transactional
    public BudgetMonthResponse copyPrevious(UUID userId, YearMonth month, String rawCurrency) {
        String currency = Money.requireCurrency(rawCurrency, "currency");
        if (budgets.findPlan(userId, month.atDay(1), currency).isPresent()) {
            throw ApiException.conflict("BUDGET_EXISTS", "This month already has a budget. Edit it instead.");
        }
        MonthlyBudget previous = budgets.findPlan(userId, month.minusMonths(1).atDay(1), currency)
                .orElseThrow(() -> ApiException.notFound("A budget for the previous month"));
        MonthlyBudget copy = new MonthlyBudget(userId, month, currency, false);
        copy.setTotalLimit(previous.getTotalLimit());
        previous.getLines().stream()
                .filter(l -> !l.getCategory().isArchived())
                .forEach(l -> copy.getLines().add(new CategoryBudget(copy, l.getCategory(), l.getLimitAmount())));
        MonthlyBudget saved = budgets.save(copy);
        events.publishEvent(SpendingChangedEvent.of(userId, currency, month));
        return toResponse(userLookup.require(userId), month, currency, saved);
    }

    @Transactional
    public void delete(UUID userId, YearMonth month, String rawCurrency) {
        String currency = Money.requireCurrency(rawCurrency, "currency");
        MonthlyBudget plan = budgets.findPlan(userId, month.atDay(1), currency)
                .orElseThrow(() -> ApiException.notFound("Budget"));
        budgets.delete(plan);
    }

    /** The last {@code months} calendar months up to and including the current one, newest first. */
    @Transactional(readOnly = true)
    public List<HistoryEntry> history(UUID userId, String rawCurrency, int months) {
        String currency = Money.requireCurrency(rawCurrency, "currency");
        int span = Math.max(1, Math.min(months, 24));
        User user = userLookup.require(userId);
        YearMonth last = userClock.currentMonth(user);
        YearMonth first = last.minusMonths(span - 1L);
        Map<YearMonth, MonthlyBudget> plans = budgets.findPlansBetween(userId, currency, first.atDay(1), last.atDay(1))
                .stream().collect(Collectors.toMap(MonthlyBudget::getMonth, Function.identity()));
        Map<YearMonth, Map<UUID, BigDecimal>> spending = new HashMap<>();
        for (MonthlyCategorySum row : transactions.monthlyExpenseSumsByCategory(userId, currency, first.atDay(1),
                last.atEndOfMonth())) {
            spending.computeIfAbsent(YearMonth.of(row.getYear(), row.getMonth()), k -> new HashMap<>())
                    .merge(row.getCategoryId(), row.getTotal(), BigDecimal::add);
        }
        List<HistoryEntry> entries = new ArrayList<>();
        for (YearMonth m = last; !m.isBefore(first); m = m.minusMonths(1)) {
            MonthlyBudget plan = plans.get(m);
            Result result = BudgetCalculator.calculate(plan == null ? null : plan.getTotalLimit(),
                    plan == null ? List.of() : lineInputs(plan), spending.getOrDefault(m, Map.of()),
                    user.getBudgetAlertThreshold(), currency);
            int exceeded = (int) result.lines().stream()
                    .filter(l -> l.measure().status() == BudgetCalculator.Status.EXCEEDED).count();
            entries.add(new HistoryEntry(m, result.total().limit(), result.total().spent(), result.total().percentUsed(),
                    plan == null ? BudgetCalculator.Status.NO_LIMIT : result.total().status(),
                    plan == null ? 0 : plan.getLines().size(), exceeded));
        }
        return entries;
    }

    @Transactional(readOnly = true)
    public List<String> currencies(UUID userId) {
        Set<String> all = new HashSet<>(budgets.distinctCurrencies(userId));
        all.addAll(transactions.distinctCurrencies(userId));
        all.add(userLookup.require(userId).getDefaultCurrency());
        return all.stream().sorted().toList();
    }

    private BudgetMonthResponse toResponse(User user, YearMonth month, String currency, MonthlyBudget plan) {
        DateRange range = DateRange.of(month);
        Map<UUID, BigDecimal> spent = transactions.expenseSumsByCategory(user.getId(), currency, range.from(), range.to())
                .stream().collect(Collectors.toMap(TransactionRepository.CategorySum::getCategoryId,
                        TransactionRepository.CategorySum::getTotal, BigDecimal::add));
        Result result = BudgetCalculator.calculate(plan == null ? null : plan.getTotalLimit(),
                plan == null ? List.of() : lineInputs(plan), spent, user.getBudgetAlertThreshold(), currency);

        Map<UUID, Category> categoryById = new LinkedHashMap<>();
        if (plan != null) {
            plan.getLines().forEach(l -> categoryById.put(l.getCategory().getId(), l.getCategory()));
        }
        List<LineResponse> lines = result.lines().stream()
                .map(l -> new LineResponse(CategoryRef.of(categoryById.get(l.categoryId())), MeasureResponse.of(l.measure())))
                .sorted((a, b) -> compareLines(a.measure(), b.measure()))
                .toList();

        BigDecimal allowance = null;
        Long daysLeft = null;
        LocalDate today = userClock.today(user);
        if (YearMonth.from(today).equals(month)) {
            daysLeft = ChronoUnit.DAYS.between(today, month.atEndOfMonth()) + 1;
            Measure total = result.total();
            allowance = BudgetCalculator.dailyAllowance(total.remaining(), daysLeft, currency);
        }
        return new BudgetMonthResponse(month, currency, plan != null, plan != null && plan.isSample(),
                MeasureResponse.of(result.total()), lines, result.allocated(), result.unbudgetedSpent(),
                result.overAllocated(), user.getBudgetAlertThreshold(), allowance, daysLeft);
    }

    /** Most at-risk lines first: highest utilisation, then name. */
    private static int compareLines(MeasureResponse a, MeasureResponse b) {
        return b.percentUsed().compareTo(a.percentUsed());
    }

    private static List<LineInput> lineInputs(MonthlyBudget plan) {
        return plan.getLines().stream().map(l -> new LineInput(l.getCategory().getId(), l.getLimitAmount())).toList();
    }

    private Map<UUID, BigDecimal> validateLines(UUID userId, List<LineRequest> lines, String currency) {
        Map<UUID, BigDecimal> result = new LinkedHashMap<>();
        List<FieldIssue> issues = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            LineRequest line = lines.get(i);
            String prefix = "lines[" + i + "].";
            if (result.containsKey(line.categoryId())) {
                issues.add(new FieldIssue(prefix + "categoryId", "This category already has a limit."));
                continue;
            }
            try {
                Category category = categoryService.require(userId, line.categoryId());
                if (category.getType() != TransactionType.EXPENSE) {
                    issues.add(new FieldIssue(prefix + "categoryId", "Budgets can only use expense categories."));
                    continue;
                }
                result.put(line.categoryId(), Money.requirePositiveAmount(line.limit(), currency, prefix + "limit"));
            } catch (FieldValidationException e) {
                issues.addAll(e.fieldErrors());
            } catch (ApiException e) {
                issues.add(new FieldIssue(prefix + "categoryId", "Choose one of your categories."));
            }
        }
        if (!issues.isEmpty()) {
            throw new FieldValidationException(issues);
        }
        return result;
    }
}
