package com.spendsmart.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "default_currency", nullable = false, length = 3)
    private String defaultCurrency;

    @Column(nullable = false, length = 20)
    private String locale;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 10)
    private Theme theme = Theme.SYSTEM;

    @Column(name = "budget_alerts_enabled", nullable = false)
    private boolean budgetAlertsEnabled = true;

    @Column(name = "budget_alert_threshold", nullable = false)
    private int budgetAlertThreshold = 80;

    /** IANA zone id used for "today" and "the current month"; see UserClock. */
    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = UserClock.DEFAULT_ZONE;

    @Column(name = "credential_version", nullable = false)
    private int credentialVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected User() {
    }

    public User(String email, String passwordHash, String displayName, String defaultCurrency, String locale) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        this.defaultCurrency = defaultCurrency;
        this.locale = locale;
    }

    public void changePassword(String newHash) {
        this.passwordHash = newHash;
        this.credentialVersion++;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) {
        this.defaultCurrency = defaultCurrency;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    public boolean isBudgetAlertsEnabled() {
        return budgetAlertsEnabled;
    }

    public void setBudgetAlertsEnabled(boolean budgetAlertsEnabled) {
        this.budgetAlertsEnabled = budgetAlertsEnabled;
    }

    public int getBudgetAlertThreshold() {
        return budgetAlertThreshold;
    }

    public void setBudgetAlertThreshold(int budgetAlertThreshold) {
        this.budgetAlertThreshold = budgetAlertThreshold;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public void setTimeZone(String timeZone) {
        this.timeZone = timeZone;
    }

    public int getCredentialVersion() {
        return credentialVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
