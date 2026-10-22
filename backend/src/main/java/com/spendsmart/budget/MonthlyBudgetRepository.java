package com.spendsmart.budget;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, UUID> {

    @Query("""
            select distinct b from MonthlyBudget b left join fetch b.lines l left join fetch l.category
            where b.userId = :userId and b.periodMonth = :month and b.currency = :currency""")
    Optional<MonthlyBudget> findPlan(UUID userId, LocalDate month, String currency);

    @Query("""
            select distinct b from MonthlyBudget b left join fetch b.lines l left join fetch l.category
            where b.userId = :userId and b.currency = :currency and b.periodMonth between :from and :to
            order by b.periodMonth desc""")
    List<MonthlyBudget> findPlansBetween(UUID userId, String currency, LocalDate from, LocalDate to);

    @Query("select distinct b.currency from MonthlyBudget b where b.userId = :userId")
    List<String> distinctCurrencies(UUID userId);

    @Query("""
            select distinct b from MonthlyBudget b left join fetch b.lines l left join fetch l.category
            where b.userId = :userId order by b.periodMonth""")
    List<MonthlyBudget> findAllForUser(UUID userId);

    boolean existsByUserIdAndSampleTrue(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MonthlyBudget b where b.userId = :userId and b.sample = true")
    int deleteSamples(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MonthlyBudget b where b.userId = :userId")
    int deleteAllForUser(UUID userId);
}
