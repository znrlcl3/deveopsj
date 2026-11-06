-- 새 앱 실행 전 적용. 기존 개인 데이터 변경 없음.
CREATE TABLE IF NOT EXISTS namuh_connection (
    member_id BIGINT NOT NULL PRIMARY KEY,
    encrypted_key VARCHAR(2048) NOT NULL,
    encrypted_secret VARCHAR(2048) NOT NULL,
    encrypted_token VARCHAR(4096),
    token_expires_at DATETIME(6),
    version BIGINT NOT NULL DEFAULT 0
);
