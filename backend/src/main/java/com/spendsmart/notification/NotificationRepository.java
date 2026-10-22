package com.spendsmart.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    @Query("select n from Notification n where n.userId = :userId order by n.createdAt desc")
    List<Notification> recent(UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    boolean existsByUserIdAndDedupeKey(UUID userId, String dedupeKey);

    List<Notification> findByUserIdOrderByCreatedAtAsc(UUID userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Notification n set n.readAt = :now where n.userId = :userId and n.readAt is null")
    int markAllRead(UUID userId, Instant now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Notification n where n.userId = :userId")
    int deleteAllForUser(UUID userId);
}
