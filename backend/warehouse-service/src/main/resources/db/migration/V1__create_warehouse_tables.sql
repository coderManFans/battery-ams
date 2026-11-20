CREATE TABLE warehouse (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 warehouse_code VARCHAR(64) NOT NULL,
 name VARCHAR(128) NOT NULL,
 address VARCHAR(500),
 status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_tenant_warehouse_code(tenant_id,warehouse_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE warehouse_location (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 warehouse_id BIGINT NOT NULL,
 location_code VARCHAR(64) NOT NULL,
 name VARCHAR(128) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_warehouse_location(tenant_id,warehouse_id,location_code),
 KEY idx_location_warehouse(tenant_id,warehouse_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inbound_order (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 order_no VARCHAR(32) NOT NULL,
 warehouse_id BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL,
 operator_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_inbound_order(tenant_id,order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE outbound_order (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 order_no VARCHAR(32) NOT NULL,
 warehouse_id BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL,
 operator_id BIGINT NOT NULL,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_outbound_order(tenant_id,order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inventory_item (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 battery_id BIGINT NOT NULL,
 battery_code VARCHAR(64) NOT NULL,
 model_code VARCHAR(64) NOT NULL,
 warehouse_id BIGINT NOT NULL,
 location_id BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE',
 received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_tenant_battery(tenant_id,battery_id),
 KEY idx_inventory_query(tenant_id,warehouse_id,status),
 KEY idx_inventory_code(tenant_id,battery_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inventory_transaction (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 transaction_no VARCHAR(64) NOT NULL,
 transaction_type VARCHAR(24) NOT NULL,
 battery_id BIGINT NOT NULL,
 battery_code VARCHAR(64) NOT NULL,
 warehouse_id BIGINT NOT NULL,
 from_location_id BIGINT,
 to_location_id BIGINT,
 order_no VARCHAR(32) NOT NULL,
 operator_id BIGINT NOT NULL,
 occurred_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_inventory_txn(tenant_id,transaction_no),
 KEY idx_inventory_txn_battery(tenant_id,battery_id,occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
