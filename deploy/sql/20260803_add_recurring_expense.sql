CREATE TABLE recurring_expense (
    id BIGINT NOT NULL AUTO_INCREMENT,
    member_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    category_code VARCHAR(20) NOT NULL,
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
    CONSTRAINT fk_recurring_expense_member FOREIGN KEY (member_id) REFERENCES members (member_id),
    CONSTRAINT chk_recurring_expense_payment_day CHECK (payment_day BETWEEN 1 AND 31),
    CONSTRAINT chk_recurring_expense_amount CHECK (amount > 0)
);

ALTER TABLE daily_spending
    ADD COLUMN recurring_expense_id BIGINT NULL,
    ADD COLUMN recurring_year_month VARCHAR(7) NULL,
    ADD CONSTRAINT fk_daily_spending_recurring_expense
        FOREIGN KEY (recurring_expense_id) REFERENCES recurring_expense (id),
    ADD CONSTRAINT uk_daily_spending_recurring_month
        UNIQUE (recurring_expense_id, recurring_year_month);
