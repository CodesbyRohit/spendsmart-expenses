package com.spendsmart.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** An immutable entry in a goal's history. Corrections are made by deleting the entry and recording a new one. */
@Entity
@Table(name = "goal_contributions")
public class GoalContribution {

    public enum Type { DEPOSIT, WITHDRAWAL }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "goal_id", nullable = false, updatable = false)
    private UUID goalId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10, updatable = false)
    private Type type;

    @Column(nullable = false, precision = 19, scale = 4, updatable = false)
    private BigDecimal amount;

    @Column(name = "contribution_date", nullable = false, updatable = false)
    private LocalDate contributionDate;

    @Column(length = 200, updatable = false)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected GoalContribution() {
    }

    public GoalContribution(UUID goalId, UUID userId, Type type, BigDecimal amount, LocalDate contributionDate,
                            String note) {
        this.goalId = goalId;
        this.userId = userId;
        this.type = type;
        this.amount = amount;
        this.contributionDate = contributionDate;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getGoalId() {
        return goalId;
    }

    public Type getType() {
        return type;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    /** Positive for deposits, negative for withdrawals. */
    public BigDecimal signedAmount() {
        return type == Type.DEPOSIT ? amount : amount.negate();
    }

    public LocalDate getContributionDate() {
        return contributionDate;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
