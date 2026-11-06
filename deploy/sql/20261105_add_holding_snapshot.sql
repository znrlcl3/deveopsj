-- MySQL: 기존 BUY/SELL을 보존하며 SNAPSHOT 유형 허용. 앱 중단/백업 후 새 버전 실행 전에 적용.
-- trade_type이 기존 VARCHAR인 환경에도 적용 가능. 데이터 삭제 없음, 재실행 가능.
ALTER TABLE asset_trade MODIFY COLUMN trade_type ENUM('BUY', 'SELL', 'SNAPSHOT') NOT NULL;
