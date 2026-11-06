-- Apply once before starting with ddl-auto=validate. Existing manual income remains unchanged.
ALTER TABLE income_record
    ADD COLUMN import_key VARCHAR(64) NULL,
    ADD COLUMN source_plan_id BIGINT NULL,
    ADD COLUMN source_plan_name VARCHAR(100) NULL,
    ADD COLUMN source_security_name VARCHAR(100) NULL,
    ADD COLUMN source_description VARCHAR(100) NULL,
    ADD COLUMN source_booking_date DATE NULL,
    ADD COLUMN source_gross_amount BIGINT NULL,
    ADD COLUMN source_fee BIGINT NULL,
    ADD COLUMN source_tax BIGINT NULL,
    ADD CONSTRAINT uk_income_import_key UNIQUE (import_key);
