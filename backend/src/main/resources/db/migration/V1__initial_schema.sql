-- SpendSmart Expenses initial schema.
-- Written in the common subset of PostgreSQL and H2 (PostgreSQL mode) so the same migration runs in production and tests.
-- Derived figures (totals, budget usage, goal progress) are intentionally NOT stored; they are computed from rows below.

CREATE TABLE users (
    id                      UUID                     NOT NULL,
    email                   VARCHAR(254)             NOT NULL,
    password_hash           VARCHAR(100)             NOT NULL,
    display_name            VARCHAR(80)              NOT NULL,
    default_currency        VARCHAR(3)               NOT NULL,
    locale                  VARCHAR(20)              NOT NULL,
    theme                   VARCHAR(10)              NOT NULL,
    budget_alerts_enabled   BOOLEAN                  NOT NULL DEFAULT TRUE,
    budget_alert_threshold  INTEGER                  NOT NULL DEFAULT 80,
    -- Incremented on password change; sessions created under an older value are rejected.
    credential_version      INTEGER                  NOT NULL DEFAULT 0,
    created_at              TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at              TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version                 BIGINT                   NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT ck_users_theme CHECK (theme IN ('SYSTEM', 'LIGHT', 'DARK')),
    CONSTRAINT ck_users_alert_threshold CHECK (budget_alert_threshold BETWEEN 50 AND 100)
);

CREATE TABLE categories (
    id               UUID         NOT NULL,
    user_id          UUID         NOT NULL,
    name             VARCHAR(50)  NOT NULL,
    normalized_name  VARCHAR(50)  NOT NULL,
    type             VARCHAR(10)  NOT NULL,
    icon             VARCHAR(30)  NOT NULL,
    is_default       BOOLEAN      NOT NULL DEFAULT FALSE,
    archived         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version          BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT pk_categories PRIMARY KEY (id),
    CONSTRAINT fk_categories_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_categories_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT uk_categories_user_type_name UNIQUE (user_id, type, normalized_name),
    -- Target for composite foreign keys: lets the database guarantee that a transaction's category
    -- belongs to the same user and has the same INCOME/EXPENSE type.
    CONSTRAINT uk_categories_id_user_type UNIQUE (id, user_id, type)
);

CREATE TABLE transactions (
    id                UUID          NOT NULL,
    user_id           UUID          NOT NULL,
    category_id       UUID          NOT NULL,
    type              VARCHAR(10)   NOT NULL,
    amount            NUMERIC(19, 4) NOT NULL,
    currency          VARCHAR(3)    NOT NULL,
    description       VARCHAR(140)  NOT NULL,
    merchant          VARCHAR(100),
    payment_method    VARCHAR(20),
    notes             VARCHAR(1000),
    transaction_date  DATE          NOT NULL,
    is_sample         BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT fk_transactions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_transactions_category FOREIGN KEY (category_id, user_id, type)
        REFERENCES categories (id, user_id, type),
    CONSTRAINT ck_transactions_type CHECK (type IN ('INCOME', 'EXPENSE')),
    CONSTRAINT ck_transactions_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transactions_payment_method CHECK (payment_method IS NULL OR payment_method IN
        ('CASH', 'DEBIT_CARD', 'CREDIT_CARD', 'UPI', 'BANK_TRANSFER', 'WALLET', 'OTHER'))
);

CREATE INDEX ix_transactions_user_date ON transactions (user_id, transaction_date);
CREATE INDEX ix_transactions_user_currency_date ON transactions (user_id, currency, transaction_date);
CREATE INDEX ix_transactions_category ON transactions (category_id);

CREATE TABLE monthly_budgets (
    id             UUID          NOT NULL,
    user_id        UUID          NOT NULL,
    period_month   DATE          NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    total_limit    NUMERIC(19, 4),
    is_sample      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_monthly_budgets PRIMARY KEY (id),
    CONSTRAINT fk_monthly_budgets_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_monthly_budgets_user_month_currency UNIQUE (user_id, period_month, currency),
    CONSTRAINT ck_monthly_budgets_first_of_month CHECK (EXTRACT(DAY FROM period_month) = 1),
    CONSTRAINT ck_monthly_budgets_total_positive CHECK (total_limit IS NULL OR total_limit > 0)
);

CREATE TABLE category_budgets (
    id                 UUID          NOT NULL,
    monthly_budget_id  UUID          NOT NULL,
    user_id            UUID          NOT NULL,
    category_id        UUID          NOT NULL,
    -- Always 'EXPENSE'; present so the composite FK can prove the category is an expense category.
    category_type      VARCHAR(10)   NOT NULL DEFAULT 'EXPENSE',
    limit_amount       NUMERIC(19, 4) NOT NULL,
    created_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_category_budgets PRIMARY KEY (id),
    CONSTRAINT fk_category_budgets_budget FOREIGN KEY (monthly_budget_id) REFERENCES monthly_budgets (id) ON DELETE CASCADE,
    CONSTRAINT fk_category_budgets_category FOREIGN KEY (category_id, user_id, category_type)
        REFERENCES categories (id, user_id, type) ON DELETE CASCADE,
    CONSTRAINT uk_category_budgets_budget_category UNIQUE (monthly_budget_id, category_id),
    CONSTRAINT ck_category_budgets_expense CHECK (category_type = 'EXPENSE'),
    CONSTRAINT ck_category_budgets_limit_positive CHECK (limit_amount > 0)
);

CREATE INDEX ix_category_budgets_category ON category_budgets (category_id);

CREATE TABLE savings_goals (
    id             UUID          NOT NULL,
    user_id        UUID          NOT NULL,
    name           VARCHAR(80)   NOT NULL,
    target_amount  NUMERIC(19, 4) NOT NULL,
    currency       VARCHAR(3)    NOT NULL,
    target_date    DATE,
    notes          VARCHAR(500),
    is_sample      BOOLEAN       NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    version        BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT pk_savings_goals PRIMARY KEY (id),
    CONSTRAINT fk_savings_goals_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_savings_goals_target_positive CHECK (target_amount > 0)
);

CREATE INDEX ix_savings_goals_user ON savings_goals (user_id);

CREATE TABLE goal_contributions (
    id                 UUID          NOT NULL,
    goal_id            UUID          NOT NULL,
    user_id            UUID          NOT NULL,
    type               VARCHAR(10)   NOT NULL,
    amount             NUMERIC(19, 4) NOT NULL,
    contribution_date  DATE          NOT NULL,
    note               VARCHAR(200),
    created_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_goal_contributions PRIMARY KEY (id),
    CONSTRAINT fk_goal_contributions_goal FOREIGN KEY (goal_id) REFERENCES savings_goals (id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_contributions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_goal_contributions_type CHECK (type IN ('DEPOSIT', 'WITHDRAWAL')),
    CONSTRAINT ck_goal_contributions_amount_positive CHECK (amount > 0)
);

CREATE INDEX ix_goal_contributions_goal_date ON goal_contributions (goal_id, contribution_date);

CREATE TABLE notifications (
    id          UUID          NOT NULL,
    user_id     UUID          NOT NULL,
    type        VARCHAR(30)   NOT NULL,
    title       VARCHAR(120)  NOT NULL,
    message     VARCHAR(300)  NOT NULL,
    link        VARCHAR(200),
    -- One alert per (budget line, month, level): prevents duplicate budget alerts.
    dedupe_key  VARCHAR(160)  NOT NULL,
    read_at     TIMESTAMP(6) WITH TIME ZONE,
    created_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT uk_notifications_user_dedupe UNIQUE (user_id, dedupe_key),
    CONSTRAINT ck_notifications_type CHECK (type IN ('BUDGET_THRESHOLD', 'BUDGET_EXCEEDED'))
);

CREATE INDEX ix_notifications_user_created ON notifications (user_id, created_at);
