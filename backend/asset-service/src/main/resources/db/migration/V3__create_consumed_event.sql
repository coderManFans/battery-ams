CREATE TABLE consumed_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 event_id VARCHAR(64) NOT NULL,
 tenant_id BIGINT NOT NULL,
 event_type VARCHAR(128) NOT NULL,
 aggregate_type VARCHAR(64) NOT NULL,
 aggregate_id VARCHAR(64) NOT NULL,
 payload JSON NOT NULL,
 consumed_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_consumed_event_id(event_id),
 KEY idx_consumed_tenant_time(tenant_id,consumed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
