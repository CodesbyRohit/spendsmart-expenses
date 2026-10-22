package com.spendsmart.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An in-app alert. Nothing is ever emailed or pushed: no external provider is configured. */
@Entity
@Table(name = "notifications")
public class Notification {

    public enum Type { BUDGET_THRESHOLD, BUDGET_EXCEEDED }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 30, updatable = false)
    private Type type;

    @Column(nullable = false, length = 120, updatable = false)
    private String title;

    @Column(nullable = false, length = 300, updatable = false)
    private String message;

    @Column(length = 200, updatable = false)
    private String link;

    @Column(name = "dedupe_key", nullable = false, length = 160, updatable = false)
    private String dedupeKey;

    @Column(name = "read_at")
    private Instant readAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Notification() {
    }

    public Notification(UUID userId, Type type, String title, String message, String link, String dedupeKey) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.link = link;
        this.dedupeKey = dedupeKey;
    }

    public UUID getId() {
        return id;
    }

    public Type getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getLink() {
        return link;
    }

    public String getDedupeKey() {
        return dedupeKey;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void markRead(Instant when) {
        if (readAt == null) {
            readAt = when;
        }
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
