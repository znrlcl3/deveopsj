-- Apply once after prior trade import migrations, before deploying with ddl-auto=validate.
ALTER TABLE asset_trade ADD COLUMN import_details VARCHAR(2000) NULL;
