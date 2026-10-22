package com.spendsmart.analytics;

import com.spendsmart.analytics.AnalyticsDtos.CategorySlice;
import com.spendsmart.analytics.AnalyticsDtos.Insight;
import com.spendsmart.analytics.AnalyticsDtos.RecurringPattern;
import com.spendsmart.analytics.AnalyticsDtos.Tone;
import com.spendsmart.analytics.AnalyticsDtos.Totals;
import com.spendsmart.budget.BudgetCalculator.Status;
import com.spendsmart.budget.BudgetDtos.BudgetMonthResponse;
import com.spendsmart.budget.BudgetDtos.LineResponse;
import com.spendsmart.common.Money;
import com.spendsmart.common.Percent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Deterministic, rule-based insights. Each rule states the data it needs and stays silent when that data is absent.
 * No rule ever divides by a zero baseline: in that case it explains the situation in words instead.
 */
public final class InsightEngine {

    /** A category movement is reported only if it changed by at least this much... */
    static final BigDecimal MIN_CHANGE_PERCENT = BigDecimal.TEN;
    /** ...and represents at least this share of the period's expenses (filters out noise from tiny categories). */
    static final BigDecimal MIN_SHARE_PERCENT = BigDecimal.valueOf(5);

    private InsightEngine() {
    }

    public record Input(String currency, Locale locale, boolean singleMonth, Totals current, Totals previous,
                        List<CategorySlice> expenseCategories, LargestExpense largest, BudgetMonthResponse budget,
                        List<RecurringPattern> recurring) {
    }

    public record LargestExpense(String description, BigDecimal amount, java.time.LocalDate date) {
    }

    public static List<Insight> generate(Input in) {
        List<Insight> out = new ArrayList<>();
        String period = in.singleMonth() ? "this month" : "this period";
        String previousPeriod = in.singleMonth() ? "the previous month" : "the previous period of the same length";

        if (in.current().expenseCount() == 0 && in.current().incomeCount() == 0) {
            return out;
        }

        // Rule: overall expense change vs the previous period.
        BigDecimal expenseChange = Percent.change(in.current().expenses(), in.previous().expenses());
        if (in.current().expenseCount() > 0) {
            if (expenseChange == null) {
                out.add(new Insight("expense-change-no-baseline", Tone.NEUTRAL,
                        "No expenses were recorded in " + previousPeriod + ", so there is no baseline to compare "
                                + period + "'s " + money(in, in.current().expenses()) + " against."));
            } else if (expenseChange.abs().compareTo(BigDecimal.ONE) >= 0) {
                boolean up = expenseChange.signum() > 0;
                out.add(new Insight("expense-change", up ? Tone.WARNING : Tone.POSITIVE,
                        "Total expenses are " + pct(expenseChange.abs()) + (up ? " higher" : " lower") + " than in "
                                + previousPeriod + " (" + money(in, in.previous().expenses()) + " → "
                                + money(in, in.current().expenses()) + ")."));
            }
        }

        // Rule: the biggest category movements (at most two), by absolute amount.
        List<CategorySlice> movers = in.expenseCategories().stream()
                .filter(c -> c.sharePercent() != null && c.sharePercent().compareTo(MIN_SHARE_PERCENT) >= 0)
                .filter(c -> c.changePercent() != null && c.changePercent().abs().compareTo(MIN_CHANGE_PERCENT) >= 0)
                .sorted(Comparator.comparing((CategorySlice c) -> c.total().subtract(c.previousTotal()).abs()).reversed())
                .limit(2)
                .toList();
        for (CategorySlice c : movers) {
            boolean up = c.changePercent().signum() > 0;
            out.add(new Insight("category-change", up ? Tone.WARNING : Tone.POSITIVE,
                    "Your " + c.name() + " spending " + (up ? "increased " : "decreased ") + pct(c.changePercent().abs())
                            + " compared with " + previousPeriod + " (" + money(in, c.previousTotal()) + " → "
                            + money(in, c.total()) + ")."));
        }
        // Rule: a large category with no baseline gets an explanation instead of a percentage.
        in.expenseCategories().stream()
                .filter(c -> c.previousTotal().signum() == 0 && c.sharePercent() != null
                        && c.sharePercent().compareTo(BigDecimal.valueOf(15)) >= 0)
                .findFirst()
                .filter(c -> in.previous().expenseCount() > 0)
                .ifPresent(c -> out.add(new Insight("category-new", Tone.NEUTRAL,
                        c.name() + " accounts for " + pct(c.sharePercent()) + " of expenses " + period
                                + ", with nothing recorded in it during " + previousPeriod + ".")));

        // Rule: concentration — only meaningful with more than three categories.
        if (in.expenseCategories().size() > 3 && in.current().expenses().signum() > 0) {
            List<CategorySlice> top = in.expenseCategories().subList(0, 3);
            BigDecimal topSum = top.stream().map(CategorySlice::total).reduce(BigDecimal.ZERO, BigDecimal::add);
            out.add(new Insight("top-three-share", Tone.NEUTRAL,
                    "Your three largest expense categories (" + top.get(0).name() + ", " + top.get(1).name() + " and "
                            + top.get(2).name() + ") account for " + pct(Percent.of(topSum, in.current().expenses()))
                            + " of recorded expenses " + period + "."));
        }

        // Rule: income vs expenses.
        if (in.current().incomeCount() == 0 && in.current().expenseCount() > 0) {
            out.add(new Insight("no-income", Tone.NEUTRAL,
                    "No income is recorded " + period + ", so a savings rate can't be calculated."));
        } else if (in.current().income().signum() > 0) {
            BigDecimal net = in.current().net();
            if (net.signum() < 0) {
                out.add(new Insight("overspent-income", Tone.WARNING,
                        "Recorded expenses exceeded recorded income by " + money(in, net.negate()) + " " + period + "."));
            } else if (in.current().expenseCount() > 0) {
                out.add(new Insight("savings-rate", Tone.POSITIVE,
                        "You kept " + pct(Percent.of(net, in.current().income())) + " of your recorded income " + period
                                + " (" + money(in, net) + ")."));
            }
        }

        // Rule: budget lines at or over the alert threshold (current budget month only).
        if (in.budget() != null && in.budget().planExists()) {
            List<LineResponse> atRisk = in.budget().lines().stream()
                    .filter(l -> l.measure().status() == Status.WARNING || l.measure().status() == Status.EXCEEDED)
                    .limit(2)
                    .toList();
            for (LineResponse l : atRisk) {
                boolean over = l.measure().status() == Status.EXCEEDED;
                out.add(new Insight(over ? "budget-exceeded" : "budget-threshold", Tone.WARNING,
                        over ? "Your " + l.category().name() + " budget is exceeded by "
                                + money(in, l.measure().overBy()) + " (" + pct(l.measure().percentUsed()) + " used)."
                                : "You have used " + pct(l.measure().percentUsed()) + " of your monthly "
                                + l.category().name() + " budget."));
            }
        }

        // Rule: the single largest expense, when there are enough expenses for it to stand out.
        if (in.largest() != null && in.current().expenseCount() >= 3 && in.current().expenses().signum() > 0) {
            out.add(new Insight("largest-expense", Tone.NEUTRAL,
                    "Your largest expense " + period + " was " + money(in, in.largest().amount()) + " for \""
                            + in.largest().description() + "\" — " + pct(Percent.of(in.largest().amount(),
                            in.current().expenses())) + " of the total."));
        }

        // Rule: recurring payments detected by RecurringDetector.
        if (!in.recurring().isEmpty()) {
            BigDecimal monthly = in.recurring().stream().map(RecurringPattern::monthlyEquivalent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            out.add(new Insight("recurring", Tone.NEUTRAL,
                    in.recurring().size() + (in.recurring().size() == 1 ? " regular payment" : " regular payments")
                            + " detected in the last six months, about " + money(in, monthly)
                            + " per month in total. Review them for subscriptions you no longer use."));
        }
        return out;
    }

    private static String money(Input in, BigDecimal amount) {
        return Money.format(amount, in.currency(), in.locale());
    }

    private static String pct(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString() + "%";
    }
}
