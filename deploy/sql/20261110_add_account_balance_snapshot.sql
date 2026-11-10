-- Run once on the intended DB. No existing financial rows are changed.
CREATE TABLE account_balance_snapshot (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 plan_id BIGINT NOT NULL,
 balance_date DATE NOT NULL,
 imported_at DATETIME(6) NOT NULL,
 digest VARCHAR(64) NOT NULL,
 file_name VARCHAR(200) NOT NULL,
 format_version INT NOT NULL,
 payload LONGTEXT NOT NULL,
 original_file LONGBLOB NOT NULL,
 CONSTRAINT uk_balance_file UNIQUE(plan_id,balance_date,digest),
 CONSTRAINT fk_balance_plan FOREIGN KEY(plan_id) REFERENCES asset_plan(id)
);
