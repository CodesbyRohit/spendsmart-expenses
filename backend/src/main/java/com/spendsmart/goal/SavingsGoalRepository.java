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

public interface SavingsGoalRepository extends JpaRepository<SavingsGoal, UUID> {

    Optional<SavingsGoal> findByIdAndUserId(UUID id, UUID userId);

    /** Row lock so concurrent withdrawals cannot both pass the "not more than saved" check. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from SavingsGoal g where g.id = :id and g.userId = :userId")
    Optional<SavingsGoal> lockByIdAndUserId(UUID id, UUID userId);

    List<SavingsGoal> findByUserIdOrderByCreatedAtAsc(UUID userId);

    List<SavingsGoal> findByUserIdAndNameContainingIgnoreCaseOrderByName(UUID userId, String fragment);

    boolean existsByUserIdAndSampleTrue(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SavingsGoal g where g.userId = :userId and g.sample = true")
    int deleteSamples(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from SavingsGoal g where g.userId = :userId")
    int deleteAllForUser(UUID userId);
}
