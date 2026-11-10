-- Apply once. Original upload bytes are member/plan scoped and deduplicated by SHA-256.
CREATE TABLE namuh_import_archive (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 member_id BIGINT NOT NULL, plan_id BIGINT NOT NULL,
 file_name VARCHAR(200) NOT NULL, digest VARCHAR(64) NOT NULL,
 size_bytes BIGINT NOT NULL, content LONGBLOB NOT NULL,
 create_date DATETIME(6),create_by VARCHAR(255),update_date DATETIME(6),update_by VARCHAR(255),disable_date DATETIME(6),
 CONSTRAINT uk_archive_member_plan_digest UNIQUE(member_id,plan_id,digest),
 FOREIGN KEY(member_id) REFERENCES members(member_id),FOREIGN KEY(plan_id) REFERENCES asset_plan(id)
);
