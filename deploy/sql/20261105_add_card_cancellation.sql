-- Back up first; apply once before deploying the new application.
ALTER TABLE daily_spending ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE card_cancellation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    transaction_key VARCHAR(64) NOT NULL,
    spending_id BIGINT NOT NULL,
    refund_amount BIGINT NOT NULL,
    before_amount BIGINT NOT NULL,
    after_amount BIGINT NOT NULL,
    cancellation_date DATE NOT NULL,
    applied_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_card_cancellation_member_key UNIQUE (member_id, transaction_key)
);
