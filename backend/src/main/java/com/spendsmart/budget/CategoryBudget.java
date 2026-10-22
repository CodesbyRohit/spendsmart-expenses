package com.spendsmart.budget;

import com.spendsmart.category.Category;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/** A limit for one expense category within a {@link MonthlyBudget}. */
@Entity
@Table(name = "category_budgets")
public class CategoryBudget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "monthly_budget_id", nullable = false, updatable = false)
    private MonthlyBudget monthlyBudget;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id", nullable = false, updatable = false)
    private Category category;

    /** Constant; lets the composite foreign key prove the category is an expense category. */
    @Column(name = "category_type", nullable = false, length = 10, updatable = false)
    private String categoryType = "EXPENSE";

    @Column(name = "limit_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal limitAmount;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CategoryBudget() {
    }

    public CategoryBudget(MonthlyBudget monthlyBudget, Category category, BigDecimal limitAmount) {
        this.monthlyBudget = monthlyBudget;
        this.userId = monthlyBudget.getUserId();
        this.category = category;
        this.limitAmount = limitAmount;
    }

    public UUID getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public BigDecimal getLimitAmount() {
        return limitAmount;
    }

    public void setLimitAmount(BigDecimal limitAmount) {
        this.limitAmount = limitAmount;
    }
}
