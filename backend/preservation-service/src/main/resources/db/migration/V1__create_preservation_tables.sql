CREATE TABLE risk_event (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 risk_no VARCHAR(40) NOT NULL,
 battery_id BIGINT,
 battery_code VARCHAR(64),
 risk_type VARCHAR(32) NOT NULL,
 severity VARCHAR(16) NOT NULL,
 source VARCHAR(32) NOT NULL,
 description VARCHAR(1000) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
 reported_by BIGINT NOT NULL,
 resolved_by BIGINT,
 resolved_at DATETIME(3),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_risk_no(tenant_id,risk_no),
 KEY idx_risk_status(tenant_id,status,severity,created_at),
 KEY idx_risk_battery(tenant_id,battery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE asset_freeze_record (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 battery_id BIGINT NOT NULL,
 battery_code VARCHAR(64) NOT NULL,
 risk_event_id BIGINT NOT NULL,
 freeze_reason VARCHAR(500) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
 frozen_by BIGINT NOT NULL,
 frozen_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 released_by BIGINT,
 released_at DATETIME(3),
 UNIQUE KEY uk_active_freeze_event(tenant_id,risk_event_id),
 KEY idx_freeze_battery(tenant_id,battery_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE inspection_record (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 battery_id BIGINT NOT NULL,
 battery_code VARCHAR(64) NOT NULL,
 inspection_type VARCHAR(32) NOT NULL,
 result VARCHAR(16) NOT NULL,
 findings VARCHAR(1000),
 inspected_by BIGINT NOT NULL,
 inspected_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_inspection_battery(tenant_id,battery_id,inspected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
