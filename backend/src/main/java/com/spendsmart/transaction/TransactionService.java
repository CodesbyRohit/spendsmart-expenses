package com.spendsmart.transaction;

import com.spendsmart.category.Category;
import com.spendsmart.category.CategoryService;
import com.spendsmart.common.ApiException;
import com.spendsmart.common.FieldValidationException;
import com.spendsmart.common.Money;
import com.spendsmart.common.PageResponse;
import com.spendsmart.common.SpendingChangedEvent;
import com.spendsmart.common.SpendingChangedEvent.Period;
import com.spendsmart.common.Text;
import com.spendsmart.transaction.TransactionDtos.TransactionRequest;
import com.spendsmart.transaction.TransactionDtos.TransactionResponse;
import com.spendsmart.user.UserClock;
import com.spendsmart.user.UserLookup;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransactionService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final LocalDate EARLIEST_DATE = LocalDate.of(1970, 1, 1);

    /** API sort keys → entity properties. Anything else is rejected, so clients cannot sort on arbitrary paths. */
    private static final Map<String, String> SORTABLE = Map.of(
            "date", "transactionDate",
            "transactionDate", "transactionDate",
            "amount", "amount",
            "description", "description",
            "merchant", "merchant",
            "createdAt", "createdAt");

    private final TransactionRepository transactions;
    private final CategoryService categoryService;
    private final ApplicationEventPublisher events;
    private final UserLookup userLookup;
    private final UserClock userClock;
    private final int maxExportRows;

    public TransactionService(TransactionRepository transactions, CategoryService categoryService,
                              ApplicationEventPublisher events, UserLookup userLookup, UserClock userClock,
                              @Value("${app.export.max-rows:50000}") int maxExportRows) {
        this.transactions = transactions;
        this.categoryService = categoryService;
        this.events = events;
        this.userLookup = userLookup;
        this.userClock = userClock;
        this.maxExportRows = maxExportRows;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> list(UUID userId, TransactionFilter filter, int page, int size, String sort) {
        if (page < 0) {
            throw new FieldValidationException("page", "Page must be zero or greater.");
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new FieldValidationException("size", "Page size must be between 1 and " + MAX_PAGE_SIZE + ".");
        }
        Page<Transaction> result = transactions.findAll(TransactionSpecifications.forOwner(userId, filter),
                PageRequest.of(page, size, parseSort(sort)));
        return PageResponse.from(result, TransactionResponse::of);
    }

    /**
     * Counts first, so an oversized export is refused with one cheap COUNT query instead of loading every row into
     * memory and only then discovering it is over the cap.
     */
    @Transactional(readOnly = true)
    public List<Transaction> exportRows(UUID userId, TransactionFilter filter) {
        var spec = TransactionSpecifications.forOwner(userId, filter);
        if (transactions.count(spec) > maxExportRows) {
            throw ApiException.unprocessable("EXPORT_TOO_LARGE",
                    "The export has more than " + maxExportRows + " rows. Narrow the date range and try again.");
        }
        return transactions.findAll(spec, Sort.by(Sort.Order.asc("transactionDate"), Sort.Order.asc("createdAt")));
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(UUID userId, UUID id) {
        return TransactionResponse.of(require(userId, id));
    }

    @Transactional
    public TransactionResponse create(UUID userId, TransactionRequest request) {
        Transaction tx = new Transaction(userId, false);
        apply(userId, tx, request, null);
        Transaction saved = transactions.saveAndFlush(tx);
        publish(userId, Set.of(periodOf(saved)));
        return TransactionResponse.of(saved);
    }

    @Transactional
    public TransactionResponse update(UUID userId, UUID id, TransactionRequest request) {
        Transaction tx = require(userId, id);
        if (request.version() == null) {
            throw new FieldValidationException("version", "Missing record version. Reload and try again.");
        }
        if (request.version() != tx.getVersion()) {
            throw new ObjectOptimisticLockingFailureException(Transaction.class, id);
        }
        Period before = periodOf(tx);
        apply(userId, tx, request, tx.getCategory().getId());
        Transaction saved = transactions.saveAndFlush(tx);
        Set<Period> affected = new HashSet<>();
        affected.add(before);
        affected.add(periodOf(saved));
        publish(userId, affected);
        return TransactionResponse.of(saved);
    }

    @Transactional
    public void delete(UUID userId, UUID id) {
        Transaction tx = require(userId, id);
        Period period = periodOf(tx);
        transactions.delete(tx);
        publish(userId, Set.of(period));
    }

    private Transaction require(UUID userId, UUID id) {
        return transactions.findByIdAndUserId(id, userId).orElseThrow(() -> ApiException.notFound("Transaction"));
    }

    private void apply(UUID userId, Transaction tx, TransactionRequest request, UUID currentCategoryId) {
        String currency = Money.requireCurrency(request.currency(), "currency");
        String description = Text.clean(request.description());
        if (description == null) {
            throw new FieldValidationException("description", "Enter a description.");
        }
        LocalDate latest = userClock.today(userLookup.require(userId)).plusYears(1);
        if (request.transactionDate().isBefore(EARLIEST_DATE) || request.transactionDate().isAfter(latest)) {
            throw new FieldValidationException("transactionDate", "Choose a date between 1970 and one year from today.");
        }
        Category category = categoryService.requireForTransaction(userId, request.categoryId(), request.type(),
                currentCategoryId);
        tx.setType(request.type());
        tx.setAmount(Money.requirePositiveAmount(request.amount(), currency, "amount"));
        tx.setCurrency(currency);
        tx.setCategory(category);
        tx.setDescription(description);
        tx.setTransactionDate(request.transactionDate());
        tx.setMerchant(Text.clean(request.merchant()));
        tx.setPaymentMethod(request.paymentMethod());
        tx.setNotes(Text.cleanMultiline(request.notes()));
    }

    private void publish(UUID userId, Set<Period> periods) {
        events.publishEvent(new SpendingChangedEvent(userId, Set.copyOf(periods)));
    }

    private static Period periodOf(Transaction tx) {
        return new Period(tx.getCurrency(), YearMonth.from(tx.getTransactionDate()));
    }

    static Sort parseSort(String sort) {
        Sort.Order primary = Sort.Order.desc("transactionDate");
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",", 2);
            String property = SORTABLE.get(parts[0].trim());
            if (property == null) {
                throw new FieldValidationException("sort", "Sort by date, amount, description, merchant or createdAt.");
            }
            boolean ascending = parts.length > 1 && parts[1].trim().equalsIgnoreCase("asc");
            primary = ascending ? Sort.Order.asc(property) : Sort.Order.desc(property);
        }
        // Tie-breakers make pagination stable when many rows share a date or amount.
        return Sort.by(primary, Sort.Order.desc("createdAt"), Sort.Order.asc("id"));
    }

    /** Exposed for analytics so the filter options shown in the UI match persisted data. */
    @Transactional(readOnly = true)
    public List<String> currencies(UUID userId) {
        return transactions.distinctCurrencies(userId);
    }
}
