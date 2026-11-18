-- Apply once before deploying household-ledger to a ddl-auto=validate environment.
-- Existing financial data is preserved. NULL means no opening principal registered.
ALTER TABLE asset_plan ADD COLUMN opening_principal BIGINT NULL,
                       ADD COLUMN opening_date DATE NULL;
