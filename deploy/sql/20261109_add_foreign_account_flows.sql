-- Apply once after USD trades and symbol mapping migration.
ALTER TABLE asset_trade ADD COLUMN reference_exchange_rate DECIMAL(19,6) NULL;
CREATE TABLE foreign_account_entry (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 plan_id BIGINT NOT NULL,
 type ENUM('DIVIDEND','INTEREST','DEPOSIT','WITHDRAWAL') NOT NULL,
 entry_date DATE NOT NULL,
 amount DECIMAL(19,4) NOT NULL,
 currency VARCHAR(3) NOT NULL,
 import_key VARCHAR(64) NOT NULL UNIQUE,
 import_group_key VARCHAR(64) NOT NULL,
 source_details VARCHAR(2000),
 create_date DATETIME(6), create_by VARCHAR(255),
 update_date DATETIME(6), update_by VARCHAR(255), disable_date DATETIME(6),
 INDEX idx_foreign_entry_group(import_group_key),
 INDEX idx_foreign_entry_plan_date(plan_id,entry_date),
 FOREIGN KEY(plan_id) REFERENCES asset_plan(id)
);
