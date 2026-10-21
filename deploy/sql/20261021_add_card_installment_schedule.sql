-- Apply once before deploying code that maps this column (production uses ddl-auto=validate).
-- Existing recurring expenses keep NULL keys and are unaffected.
ALTER TABLE recurring_expense
    ADD COLUMN card_installment_key VARCHAR(64) NULL,
    ADD CONSTRAINT uk_recurring_card_installment UNIQUE (member_id, card_installment_key);
