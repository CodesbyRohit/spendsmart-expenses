package com.spendsmart.search;

import com.spendsmart.category.CategoryDtos.CategoryRef;
import com.spendsmart.category.CategoryRepository;
import com.spendsmart.common.FieldValidationException;
import com.spendsmart.common.Text;
import com.spendsmart.goal.SavingsGoalRepository;
import com.spendsmart.transaction.TransactionDtos.TransactionResponse;
import com.spendsmart.transaction.TransactionFilter;
import com.spendsmart.transaction.TransactionService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Global search across the signed-in user's own transactions, categories and goals. */
@Service
public class SearchService {

    private final TransactionService transactions;
    private final CategoryRepository categories;
    private final SavingsGoalRepository goals;

    public SearchService(TransactionService transactions, CategoryRepository categories, SavingsGoalRepository goals) {
        this.transactions = transactions;
        this.categories = categories;
        this.goals = goals;
    }

    public record GoalHit(UUID id, String name, String currency) {
    }

    public record SearchResults(String query, List<TransactionResponse> transactions, long transactionTotal,
                                List<CategoryRef> categories, List<GoalHit> goals) {
    }

    @Transactional(readOnly = true)
    public SearchResults search(UUID userId, String rawQuery) {
        String query = Text.clean(rawQuery);
        if (query == null || query.length() < 2) {
            throw new FieldValidationException("q", "Type at least two characters.");
        }
        var page = transactions.list(userId, new TransactionFilter(query, null, null, null, null, null), 0, 8, null);
        String key = Text.normalizeKey(query);
        List<CategoryRef> cats = categories.findByUserIdAndNormalizedNameContainingOrderByName(userId, key).stream()
                .limit(5).map(CategoryRef::of).toList();
        List<GoalHit> goalHits = goals.findByUserIdAndNameContainingIgnoreCaseOrderByName(userId, query).stream()
                .limit(5).map(g -> new GoalHit(g.getId(), g.getName(), g.getCurrency())).toList();
        return new SearchResults(query, page.items(), page.totalItems(), cats, goalHits);
    }
}
