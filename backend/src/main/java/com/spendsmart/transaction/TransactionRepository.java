package com.spendsmart.transaction;

import com.spendsmart.category.Category;
import com.spendsmart.common.TransactionType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * All queries take the owner's user id. Aggregates are computed in SQL from persisted rows; date ranges are inclusive
 * on both ends ({@code between}).
 */
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    @Override
    @EntityGraph(attributePaths = "category")
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = "category")
    List<Transaction> findAll(Specification<Transaction> spec, Sort sort);

    @EntityGraph(attributePaths = "category")
    Optional<Transaction> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = "category")
    List<Transaction> findTop6ByUserIdAndCurrencyOrderByTransactionDateDescCreatedAtDesc(UUID userId, String currency);

    @EntityGraph(attributePaths = "category")
    List<Transaction> findByUserIdAndCurrencyAndTypeAndTransactionDateBetweenOrderByTransactionDateAsc(
            UUID userId, String currency, TransactionType type, LocalDate from, LocalDate to);

    @EntityGraph(attributePaths = "category")
    List<Transaction> findByUserIdOrderByTransactionDateAscCreatedAtAsc(UUID userId);

    long countByUserIdAndCategoryId(UUID userId, UUID categoryId);

    boolean existsByUserId(UUID userId);

    boolean existsByUserIdAndSampleTrue(UUID userId);

    @Query("select t.category.id as categoryId, count(t) as count from Transaction t where t.userId = :userId group by t.category.id")
    List<CategoryCount> countByCategory(UUID userId);

    @Query("select distinct t.currency from Transaction t where t.userId = :userId order by t.currency")
    List<String> distinctCurrencies(UUID userId);

    @Query("""
            select t.type as type, sum(t.amount) as total, count(t) as count
            from Transaction t
            where t.userId = :userId and t.currency = :currency and t.transactionDate between :from and :to
            group by t.type""")
    List<TypeTotal> totalsByType(UUID userId, String currency, LocalDate from, LocalDate to);

    @Query("""
            select c.id as categoryId, c.name as categoryName, c.icon as icon, sum(t.amount) as total, count(t) as count
            from Transaction t join t.category c
            where t.userId = :userId and t.currency = :currency and t.type = :type
              and t.transactionDate between :from and :to
            group by c.id, c.name, c.icon
            order by sum(t.amount) desc, c.name asc""")
    List<CategoryTotal> totalsByCategory(UUID userId, String currency, TransactionType type, LocalDate from, LocalDate to);

    @Query("""
            select t.transactionDate as date, t.type as type, sum(t.amount) as total
            from Transaction t
            where t.userId = :userId and t.currency = :currency and t.transactionDate between :from and :to
            group by t.transactionDate, t.type
            order by t.transactionDate""")
    List<DailyTotal> dailyTotals(UUID userId, String currency, LocalDate from, LocalDate to);

    @Query("""
            select coalesce(sum(t.amount), 0) from Transaction t
            where t.userId = :userId and t.currency = :currency and t.type = com.spendsmart.common.TransactionType.EXPENSE
              and t.transactionDate between :from and :to""")
    BigDecimal sumExpenses(UUID userId, String currency, LocalDate from, LocalDate to);

    @Query("""
            select t.category.id as categoryId, sum(t.amount) as total from Transaction t
            where t.userId = :userId and t.currency = :currency and t.type = com.spendsmart.common.TransactionType.EXPENSE
              and t.transactionDate between :from and :to
            group by t.category.id""")
    List<CategorySum> expenseSumsByCategory(UUID userId, String currency, LocalDate from, LocalDate to);

    @Query("""
            select t.category.id as categoryId, extract(year from t.transactionDate) as year,
                   extract(month from t.transactionDate) as month, sum(t.amount) as total
            from Transaction t
            where t.userId = :userId and t.currency = :currency and t.type = com.spendsmart.common.TransactionType.EXPENSE
              and t.transactionDate between :from and :to
            group by t.category.id, extract(year from t.transactionDate), extract(month from t.transactionDate)""")
    List<MonthlyCategorySum> monthlyExpenseSumsByCategory(UUID userId, String currency, LocalDate from, LocalDate to);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Transaction t set t.category = :target where t.userId = :userId and t.category = :source")
    int reassignCategory(UUID userId, Category source, Category target);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Transaction t where t.userId = :userId and t.sample = true")
    int deleteSamples(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Transaction t where t.userId = :userId")
    int deleteAllForUser(UUID userId);

    @Query("select t.id from Transaction t where t.userId = :userId and t.id in :ids")
    List<UUID> findOwnedIds(UUID userId, Collection<UUID> ids);

    interface CategoryCount {
        UUID getCategoryId();

        long getCount();
    }

    interface TypeTotal {
        TransactionType getType();

        BigDecimal getTotal();

        long getCount();
    }

    interface CategoryTotal {
        UUID getCategoryId();

        String getCategoryName();

        String getIcon();

        BigDecimal getTotal();

        long getCount();
    }

    interface CategorySum {
        UUID getCategoryId();

        BigDecimal getTotal();
    }

    interface MonthlyCategorySum {
        UUID getCategoryId();

        Integer getYear();

        Integer getMonth();

        BigDecimal getTotal();
    }

    interface DailyTotal {
        LocalDate getDate();

        TransactionType getType();

        BigDecimal getTotal();
    }
}
