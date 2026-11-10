-- Apply once before deploying duplicate-confirmation support with ddl-auto=validate.
-- Existing import_key values are kept; legacy rows remain detectable without backfill.
ALTER TABLE asset_trade ADD COLUMN import_group_key VARCHAR(64) NULL;
ALTER TABLE income_record ADD COLUMN import_group_key VARCHAR(64) NULL;
ALTER TABLE account_transfer ADD COLUMN import_group_key VARCHAR(64) NULL;
CREATE INDEX idx_account_transfer_import_group ON account_transfer(import_group_key);
