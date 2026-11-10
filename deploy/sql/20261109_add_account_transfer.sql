-- Apply before starting with ddl-auto=validate. Does not modify existing trades or income.
CREATE TABLE IF NOT EXISTS account_transfer (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 plan_id BIGINT NOT NULL,
 type ENUM('DEPOSIT','WITHDRAWAL') NOT NULL,
 transfer_date DATE NOT NULL,
 booking_date DATE NOT NULL,
 amount BIGINT NOT NULL,
 gross_amount BIGINT NOT NULL,
 fee BIGINT NOT NULL,
 tax BIGINT NOT NULL,
 description VARCHAR(100) NOT NULL,
 source_name VARCHAR(100) NOT NULL,
 import_key VARCHAR(64) NOT NULL UNIQUE,
 create_date DATETIME(6), create_by VARCHAR(255),
 update_date DATETIME(6), update_by VARCHAR(255), disable_date DATETIME(6),
 INDEX idx_transfer_plan_date (plan_id,transfer_date),
 CONSTRAINT fk_account_transfer_plan FOREIGN KEY (plan_id) REFERENCES asset_plan(id)
);
