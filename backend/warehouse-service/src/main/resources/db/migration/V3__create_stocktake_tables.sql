CREATE TABLE stocktake_session (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 stocktake_no VARCHAR(32) NOT NULL,
 warehouse_id BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
 operator_id BIGINT NOT NULL,
 started_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 completed_at DATETIME(3),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_stocktake_no(tenant_id,stocktake_no),
 KEY idx_stocktake_warehouse(tenant_id,warehouse_id,status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE stocktake_scan (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 stocktake_id BIGINT NOT NULL,
 battery_code VARCHAR(64) NOT NULL,
 inventory_item_id BIGINT,
 scan_result VARCHAR(24) NOT NULL,
 scanned_by BIGINT NOT NULL,
 scanned_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_stocktake_scan(tenant_id,stocktake_id,battery_code),
 KEY idx_stocktake_scan_result(tenant_id,stocktake_id,scan_result)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
