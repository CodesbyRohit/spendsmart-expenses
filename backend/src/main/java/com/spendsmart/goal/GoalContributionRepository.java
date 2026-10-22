package com.spendsmart.goal;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface GoalContributionRepository extends JpaRepository<GoalContribution, UUID> {

    List<GoalContribution> findByGoalIdAndUserIdOrderByContributionDateDescCreatedAtDesc(UUID goalId, UUID userId);

    Optional<GoalContribution> findByIdAndGoalIdAndUserId(UUID id, UUID goalId, UUID userId);

    List<GoalContribution> findByUserIdOrderByContributionDateAsc(UUID userId);

    @Query("""
            select coalesce(sum(case when c.type = com.spendsmart.goal.GoalContribution.Type.DEPOSIT
                                     then c.amount else -c.amount end), 0)
            from GoalContribution c where c.goalId = :goalId and c.userId = :userId""")
    BigDecimal savedAmount(UUID goalId, UUID userId);

    /** One query for every goal on the list page (avoids N+1). */
    @Query("""
            select c.goalId as goalId,
                   coalesce(sum(case when c.type = com.spendsmart.goal.GoalContribution.Type.DEPOSIT
                                     then c.amount else -c.amount end), 0) as saved,
                   count(c) as count, max(c.contributionDate) as lastDate
            from GoalContribution c where c.userId = :userId and c.goalId in :goalIds group by c.goalId""")
    List<GoalSaved> savedAmounts(UUID userId, Collection<UUID> goalIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GoalContribution c where c.goalId = :goalId and c.userId = :userId")
    int deleteForGoal(UUID goalId, UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            delete from GoalContribution c where c.userId = :userId and c.goalId in
              (select g.id from SavingsGoal g where g.userId = :userId and g.sample = true)""")
    int deleteSamples(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from GoalContribution c where c.userId = :userId")
    int deleteAllForUser(UUID userId);

    interface GoalSaved {
        UUID getGoalId();

        BigDecimal getSaved();

        long getCount();

        java.time.LocalDate getLastDate();
    }
}
