ALTER TABLE daily_spending
    ADD COLUMN import_source VARCHAR(20) NULL,
    ADD COLUMN import_transaction_key VARCHAR(64) NULL;

ALTER TABLE daily_spending
    ADD CONSTRAINT uk_daily_spending_card_import
    UNIQUE (member_id, import_source, import_transaction_key);
