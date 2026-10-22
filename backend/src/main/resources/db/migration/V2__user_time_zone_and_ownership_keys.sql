-- V2: per-user time zone, and database-enforced ownership for child rows.
-- Compatible with PostgreSQL and H2 (PostgreSQL mode). Existing data already satisfies every new constraint,
-- because the application has always written child rows with their parent's user_id.

-- "Today" and "the current month" (budgets, alerts, goal dates, dashboard defaults) are evaluated in the user's
-- own IANA time zone instead of the server's. Existing accounts default to UTC, matching previous behaviour.
ALTER TABLE users ADD COLUMN time_zone VARCHAR(64) DEFAULT 'UTC' NOT NULL;

-- A budget line must belong to the same user as its monthly budget.
ALTER TABLE monthly_budgets ADD CONSTRAINT uk_monthly_budgets_id_user UNIQUE (id, user_id);
ALTER TABLE category_budgets DROP CONSTRAINT fk_category_budgets_budget;
ALTER TABLE category_budgets ADD CONSTRAINT fk_category_budgets_budget
    FOREIGN KEY (monthly_budget_id, user_id) REFERENCES monthly_budgets (id, user_id) ON DELETE CASCADE;

-- A contribution must belong to the same user as its savings goal.
ALTER TABLE savings_goals ADD CONSTRAINT uk_savings_goals_id_user UNIQUE (id, user_id);
ALTER TABLE goal_contributions DROP CONSTRAINT fk_goal_contributions_goal;
ALTER TABLE goal_contributions ADD CONSTRAINT fk_goal_contributions_goal
    FOREIGN KEY (goal_id, user_id) REFERENCES savings_goals (id, user_id) ON DELETE CASCADE;
