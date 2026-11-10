-- Apply once after prior trade import SQLs. Existing KRW values are preserved.
ALTER TABLE asset_trade
 MODIFY COLUMN exchange_rate DECIMAL(19,6) NULL,
 MODIFY COLUMN fee_krw BIGINT NULL,
 MODIFY COLUMN tax_krw BIGINT NULL,
 MODIFY COLUMN settlement_amount_krw BIGINT NULL,
 ADD COLUMN foreign_gross DECIMAL(19,4) NULL,
 ADD COLUMN foreign_fee DECIMAL(19,4) NULL,
 ADD COLUMN foreign_tax DECIMAL(19,4) NULL,
 ADD COLUMN foreign_settlement DECIMAL(19,4) NULL;
CREATE TABLE namuh_symbol_mapping (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 member_id BIGINT NOT NULL,
 source_name VARCHAR(100) NOT NULL,
 asset_id BIGINT NOT NULL,
 CONSTRAINT uk_namuh_member_name UNIQUE(member_id,source_name),
 FOREIGN KEY(member_id) REFERENCES members(member_id),
 FOREIGN KEY(asset_id) REFERENCES investment_asset(id)
);
