CREATE TABLE recurring_savings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    plan_id BIGINT NULL,
    goal_id BIGINT NULL,
    deposit_type VARCHAR(10) NOT NULL,
    amount BIGINT NOT NULL,
    payment_day INT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NULL,
    active BIT NOT NULL,
    memo VARCHAR(200) NULL,
    create_date DATETIME(6) NULL,
    create_by VARCHAR(255) NULL,
    update_date DATETIME(6) NULL,
    update_by VARCHAR(255) NULL,
    disable_date DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_recurring_savings_member FOREIGN KEY (member_id) REFERENCES members (member_id),
    CONSTRAINT fk_recurring_savings_plan FOREIGN KEY (plan_id) REFERENCES asset_plan (id),
    CONSTRAINT fk_recurring_savings_goal FOREIGN KEY (goal_id) REFERENCES goals (id),
    CONSTRAINT chk_recurring_savings_payment_day CHECK (payment_day BETWEEN 1 AND 31),
    CONSTRAINT chk_recurring_savings_amount CHECK (amount > 0),
    CONSTRAINT chk_recurring_savings_target CHECK (
        (deposit_type = 'PLAN' AND plan_id IS NOT NULL AND goal_id IS NOT NULL)
        OR (deposit_type = 'EXTRA' AND plan_id IS NULL AND goal_id IS NOT NULL)
    )
);

ALTER TABLE asset_savings
    ADD COLUMN recurring_savings_id BIGINT NULL,
    ADD COLUMN recurring_year_month VARCHAR(7) NULL,
    ADD CONSTRAINT fk_asset_savings_recurring_savings
        FOREIGN KEY (recurring_savings_id) REFERENCES recurring_savings (id),
    ADD CONSTRAINT uk_asset_savings_recurring_month
        UNIQUE (recurring_savings_id, recurring_year_month);
