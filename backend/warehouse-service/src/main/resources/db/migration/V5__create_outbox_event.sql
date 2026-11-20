CREATE TABLE outbox_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 event_id VARCHAR(64) NOT NULL,
 tenant_id BIGINT NOT NULL,
 aggregate_type VARCHAR(64) NOT NULL,
 aggregate_id VARCHAR(64) NOT NULL,
 event_type VARCHAR(128) NOT NULL,
 topic VARCHAR(128) NOT NULL,
 tag VARCHAR(128) NOT NULL,
 payload JSON NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
 attempts INT NOT NULL DEFAULT 0,
 next_attempt_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 last_error VARCHAR(1000),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 published_at DATETIME(3),
 UNIQUE KEY uk_outbox_event_id(event_id),
 KEY idx_outbox_pending(status,next_attempt_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
