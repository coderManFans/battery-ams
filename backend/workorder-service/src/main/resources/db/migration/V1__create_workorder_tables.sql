CREATE TABLE work_order (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 work_order_no VARCHAR(40) NOT NULL,
 work_order_type VARCHAR(24) NOT NULL,
 battery_id BIGINT,
 battery_code VARCHAR(64),
 title VARCHAR(160) NOT NULL,
 description TEXT,
 priority VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
 status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
 assignee_id BIGINT,
 reporter_id BIGINT NOT NULL,
 due_at DATETIME(3),
 version BIGINT NOT NULL DEFAULT 0,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_workorder_no(tenant_id,work_order_no),
 KEY idx_workorder_status(tenant_id,status,priority,created_at),
 KEY idx_workorder_battery(tenant_id,battery_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE work_order_log (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 work_order_id BIGINT NOT NULL,
 action VARCHAR(32) NOT NULL,
 from_status VARCHAR(24),
 to_status VARCHAR(24),
 comment VARCHAR(1000),
 operator_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_workorder_log(tenant_id,work_order_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
