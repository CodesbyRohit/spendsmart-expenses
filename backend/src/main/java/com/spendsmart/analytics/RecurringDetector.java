package com.spendsmart.analytics;

import com.spendsmart.analytics.AnalyticsDtos.Cadence;
import com.spendsmart.analytics.AnalyticsDtos.RecurringPattern;
import com.spendsmart.common.Money;
import com.spendsmart.common.Text;
import com.spendsmart.transaction.Transaction;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Flags an expense as recurring only when ALL of the following hold for a group of expenses sharing the same
 * merchant (or, without a merchant, the same description):
 * <ol>
 *   <li>at least {@value #MIN_OCCURRENCES} occurrences;</li>
 *   <li>every gap between consecutive dates fits one cadence — weekly 6–8 days, biweekly 13–15, monthly 27–33;</li>
 *   <li>every amount is within ±{@value #AMOUNT_TOLERANCE_PERCENT}% of the group's median amount.</li>
 * </ol>
 * Anything less regular is not reported, so the app never calls an irregular habit "recurring".
 */
public final class RecurringDetector {

    static final int MIN_OCCURRENCES = 3;
    static final int AMOUNT_TOLERANCE_PERCENT = 15;

    private RecurringDetector() {
    }

    public static List<RecurringPattern> detect(List<Transaction> expenses, String currency) {
        Map<String, List<Transaction>> groups = new LinkedHashMap<>();
        for (Transaction t : expenses) {
            String key = Text.normalizeKey(t.getMerchant() != null ? t.getMerchant() : t.getDescription());
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }
        List<RecurringPattern> patterns = new ArrayList<>();
        for (List<Transaction> group : groups.values()) {
            if (group.size() < MIN_OCCURRENCES) {
                continue;
            }
            group.sort(Comparator.comparing(Transaction::getTransactionDate));
            List<Long> gaps = new ArrayList<>();
            for (int i = 1; i < group.size(); i++) {
                gaps.add(ChronoUnit.DAYS.between(group.get(i - 1).getTransactionDate(), group.get(i).getTransactionDate()));
            }
            Cadence cadence = cadenceOf(gaps);
            if (cadence == null) {
                continue;
            }
            BigDecimal median = median(group.stream().map(Transaction::getAmount).toList());
            if (!amountsConsistent(group, median)) {
                continue;
            }
            Transaction last = group.getLast();
            long medianGap = gaps.stream().sorted().toList().get(gaps.size() / 2);
            LocalDate next = cadence == Cadence.MONTHLY ? last.getTransactionDate().plusMonths(1)
                    : last.getTransactionDate().plusDays(medianGap);
            BigDecimal typical = Money.scale(median, currency);
            BigDecimal monthly = switch (cadence) {
                case WEEKLY -> typical.multiply(BigDecimal.valueOf(52)).divide(BigDecimal.valueOf(12), Money.minorUnits(currency), RoundingMode.HALF_UP);
                case BIWEEKLY -> typical.multiply(BigDecimal.valueOf(26)).divide(BigDecimal.valueOf(12), Money.minorUnits(currency), RoundingMode.HALF_UP);
                case MONTHLY -> typical;
            };
            String label = last.getMerchant() != null ? last.getMerchant() : last.getDescription();
            patterns.add(new RecurringPattern(label, last.getCategory().getName(), cadence, typical, group.size(),
                    group.getFirst().getTransactionDate(), last.getTransactionDate(), next, monthly));
        }
        patterns.sort(Comparator.comparing(RecurringPattern::monthlyEquivalent).reversed());
        return patterns;
    }

    static Cadence cadenceOf(List<Long> gaps) {
        if (gaps.stream().allMatch(g -> g >= 6 && g <= 8)) {
            return Cadence.WEEKLY;
        }
        if (gaps.stream().allMatch(g -> g >= 13 && g <= 15)) {
            return Cadence.BIWEEKLY;
        }
        if (gaps.stream().allMatch(g -> g >= 27 && g <= 33)) {
            return Cadence.MONTHLY;
        }
        return null;
    }

    private static boolean amountsConsistent(List<Transaction> group, BigDecimal median) {
        BigDecimal tolerance = median.multiply(BigDecimal.valueOf(AMOUNT_TOLERANCE_PERCENT)).divide(BigDecimal.valueOf(100));
        return group.stream().allMatch(t -> t.getAmount().subtract(median).abs().compareTo(tolerance) <= 0);
    }

    static BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        if (n % 2 == 1) {
            return sorted.get(n / 2);
        }
        return sorted.get(n / 2 - 1).add(sorted.get(n / 2)).divide(BigDecimal.valueOf(2));
    }
}
