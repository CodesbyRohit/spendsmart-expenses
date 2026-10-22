package com.spendsmart.notification;

import com.spendsmart.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private final NotificationRepository notifications;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, Clock clock) {
        this.notifications = notifications;
        this.clock = clock;
    }

    public record NotificationResponse(UUID id, Notification.Type type, String title, String message, String link,
                                       boolean read, Instant createdAt) {
    }

    public record NotificationList(List<NotificationResponse> items, long unreadCount) {
    }

    @Transactional(readOnly = true)
    public NotificationList list(UUID userId) {
        List<NotificationResponse> items = notifications.recent(userId, PageRequest.of(0, 30)).stream()
                .map(n -> new NotificationResponse(n.getId(), n.getType(), n.getTitle(), n.getMessage(), n.getLink(),
                        n.getReadAt() != null, n.getCreatedAt()))
                .toList();
        return new NotificationList(items, notifications.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public void markRead(UUID userId, UUID id) {
        notifications.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("Notification"))
                .markRead(Instant.now(clock));
    }

    @Transactional
    public void markAllRead(UUID userId) {
        notifications.markAllRead(userId, Instant.now(clock));
    }
}
