-- Apply before starting with ddl-auto=validate. No existing records are changed.
CREATE TABLE IF NOT EXISTS asset_balance (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    name VARCHAR(80) NOT NULL,
    kind ENUM('DEPOSIT', 'SAVINGS', 'CASH') NOT NULL,
    amount BIGINT NOT NULL,
    as_of_date DATE NOT NULL,
    maturity_date DATE NULL,
    version BIGINT NOT NULL DEFAULT 0,
    INDEX idx_asset_balance_member (member_id)
);
