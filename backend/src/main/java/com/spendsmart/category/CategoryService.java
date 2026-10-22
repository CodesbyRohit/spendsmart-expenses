package com.spendsmart.category;

import com.spendsmart.budget.CategoryBudgetRepository;
import com.spendsmart.category.CategoryDtos.CategoryResponse;
import com.spendsmart.category.CategoryDtos.CategoryUsageResponse;
import com.spendsmart.category.CategoryDtos.CreateCategoryRequest;
import com.spendsmart.category.CategoryDtos.UpdateCategoryRequest;
import com.spendsmart.common.ApiException;
import com.spendsmart.common.FieldValidationException;
import com.spendsmart.common.Text;
import com.spendsmart.common.TransactionType;
import com.spendsmart.transaction.TransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categories;
    private final TransactionRepository transactions;
    private final CategoryBudgetRepository categoryBudgets;

    public CategoryService(CategoryRepository categories, TransactionRepository transactions,
                           CategoryBudgetRepository categoryBudgets) {
        this.categories = categories;
        this.transactions = transactions;
        this.categoryBudgets = categoryBudgets;
    }

    /** Idempotent: does nothing if the user already has categories. */
    @Transactional
    public void seedDefaults(UUID userId) {
        if (categories.existsByUserId(userId)) {
            return;
        }
        categories.saveAll(DefaultCategories.ALL.stream()
                .map(d -> new Category(userId, d.name(), d.type(), d.icon(), true))
                .toList());
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> list(UUID userId, boolean includeArchived) {
        Map<UUID, Long> counts = transactions.countByCategory(userId).stream()
                .collect(Collectors.toMap(TransactionRepository.CategoryCount::getCategoryId,
                        TransactionRepository.CategoryCount::getCount));
        return categories.findByUserIdOrderByTypeAscNameAsc(userId).stream()
                .filter(c -> includeArchived || !c.isArchived())
                .map(c -> CategoryResponse.of(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoryResponse create(UUID userId, CreateCategoryRequest request) {
        String name = requireName(request.name());
        requireIcon(request.icon());
        ensureUniqueName(userId, request.type(), name, null);
        Category saved = categories.save(new Category(userId, name, request.type(), request.icon(), false));
        return CategoryResponse.of(saved, 0);
    }

    @Transactional
    public CategoryResponse update(UUID userId, UUID id, UpdateCategoryRequest request) {
        Category category = require(userId, id);
        String name = requireName(request.name());
        requireIcon(request.icon());
        ensureUniqueName(userId, category.getType(), name, category.getId());
        category.rename(name);
        category.setIcon(request.icon());
        category.setArchived(request.archived());
        return CategoryResponse.of(category, transactions.countByUserIdAndCategoryId(userId, id));
    }

    @Transactional(readOnly = true)
    public CategoryUsageResponse usage(UUID userId, UUID id) {
        require(userId, id);
        return new CategoryUsageResponse(transactions.countByUserIdAndCategoryId(userId, id),
                categoryBudgets.countByUserIdAndCategoryId(userId, id));
    }

    /**
     * Deletion rules: a category with no transactions can be deleted outright. A category with transactions can
     * only be deleted when {@code reassignTo} names another category of the same type owned by the same user; its
     * transactions move there first. Budget lines for the deleted category are removed (the reassigned spending then
     * counts toward the target category's budget line, if it has one).
     */
    @Transactional
    public void delete(UUID userId, UUID id, UUID reassignTo) {
        Category category = require(userId, id);
        long inUse = transactions.countByUserIdAndCategoryId(userId, id);
        if (inUse > 0) {
            if (reassignTo == null) {
                throw ApiException.conflict("CATEGORY_IN_USE", inUse + " transaction(s) use this category. "
                        + "Move them to another category or archive this category instead.");
            }
            if (reassignTo.equals(id)) {
                throw new FieldValidationException("reassignTo", "Choose a different category.");
            }
            Category target = categories.findByIdAndUserId(reassignTo, userId)
                    .orElseThrow(() -> new FieldValidationException("reassignTo", "Choose one of your categories."));
            if (target.getType() != category.getType()) {
                throw new FieldValidationException("reassignTo", "The replacement must also be an "
                        + category.getType().name().toLowerCase() + " category.");
            }
            transactions.reassignCategory(userId, category, target);
        }
        categoryBudgets.deleteByUserIdAndCategoryId(userId, id);
        categories.delete(categories.findByIdAndUserId(id, userId).orElseThrow());
    }

    /** Ownership-checked lookup used by other services. Missing and foreign categories are indistinguishable. */
    @Transactional(readOnly = true)
    public Category require(UUID userId, UUID id) {
        return categories.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Category"));
    }

    /** Resolves a category for a new or edited transaction, enforcing type compatibility. */
    @Transactional(readOnly = true)
    public Category requireForTransaction(UUID userId, UUID categoryId, TransactionType type, UUID currentCategoryId) {
        Category category = categories.findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new FieldValidationException("categoryId", "Choose one of your categories."));
        if (category.getType() != type) {
            throw new FieldValidationException("categoryId", type == TransactionType.EXPENSE
                    ? "Choose an expense category." : "Choose an income category.");
        }
        if (category.isArchived() && !category.getId().equals(currentCategoryId)) {
            throw new FieldValidationException("categoryId", "This category is archived. Choose an active category.");
        }
        return category;
    }

    private static String requireName(String raw) {
        String name = Text.clean(raw);
        if (name == null) {
            throw new FieldValidationException("name", "Enter a name.");
        }
        if (name.length() > 50) {
            throw new FieldValidationException("name", "Name can be at most 50 characters.");
        }
        return name;
    }

    private static void requireIcon(String icon) {
        if (!CategoryIcons.isAllowed(icon)) {
            throw new FieldValidationException("icon", "Choose an icon from the list.");
        }
    }

    private void ensureUniqueName(UUID userId, TransactionType type, String name, UUID selfId) {
        categories.findByUserIdAndTypeAndNormalizedName(userId, type, Text.normalizeKey(name))
                .filter(existing -> !existing.getId().equals(selfId))
                .ifPresent(existing -> {
                    throw new FieldValidationException("name", "You already have an "
                            + type.name().toLowerCase() + " category with this name.");
                });
    }
}
