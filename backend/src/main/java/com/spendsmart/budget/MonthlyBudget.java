package com.spendsmart.budget;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A user's spending plan for one calendar month in one currency: an optional overall limit plus category limits. */
@Entity
@Table(name = "monthly_budgets")
public class MonthlyBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** Always the first day of the month (enforced by a CHECK constraint). */
    @Column(name = "period_month", nullable = false, updatable = false)
    private LocalDate periodMonth;

    @Column(nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "total_limit", precision = 19, scale = 4)
    private BigDecimal totalLimit;

    @Column(name = "is_sample", nullable = false, updatable = false)
    private boolean sample;

    @OneToMany(mappedBy = "monthlyBudget", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CategoryBudget> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected MonthlyBudget() {
    }

    public MonthlyBudget(UUID userId, YearMonth month, String currency, boolean sample) {
        this.userId = userId;
        this.periodMonth = month.atDay(1);
        this.currency = currency;
        this.sample = sample;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public YearMonth getMonth() {
        return YearMonth.from(periodMonth);
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getTotalLimit() {
        return totalLimit;
    }

    public void setTotalLimit(BigDecimal totalLimit) {
        this.totalLimit = totalLimit;
    }

    public boolean isSample() {
        return sample;
    }

    public List<CategoryBudget> getLines() {
        return lines;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
