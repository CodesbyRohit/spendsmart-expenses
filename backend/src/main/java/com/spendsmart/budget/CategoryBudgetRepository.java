package com.spendsmart.budget;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface CategoryBudgetRepository extends JpaRepository<CategoryBudget, UUID> {

    @Query("select count(l) from CategoryBudget l where l.userId = :userId and l.category.id = :categoryId")
    long countByUserIdAndCategoryId(UUID userId, UUID categoryId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CategoryBudget l where l.userId = :userId and l.category.id = :categoryId")
    int deleteByUserIdAndCategoryId(UUID userId, UUID categoryId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from CategoryBudget l where l.userId = :userId and l.monthlyBudget.id in
              (select b.id from MonthlyBudget b where b.userId = :userId and b.sample = true)""")
    int deleteSamples(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CategoryBudget l where l.userId = :userId")
    int deleteAllForUser(UUID userId);
}
