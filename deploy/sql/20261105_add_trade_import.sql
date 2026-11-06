-- Apply once before starting the application with ddl-auto=validate.
ALTER TABLE asset_trade
    ADD COLUMN import_key VARCHAR(64) NULL,
    ADD COLUMN history_only BIT NOT NULL DEFAULT 0,
    ADD CONSTRAINT uk_asset_trade_import_key UNIQUE (import_key);
