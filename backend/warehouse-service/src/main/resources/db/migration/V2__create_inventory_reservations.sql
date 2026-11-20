CREATE TABLE inventory_reservation (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 reservation_no VARCHAR(32) NOT NULL,
 warehouse_id BIGINT NOT NULL,
 status VARCHAR(24) NOT NULL,
 operator_id BIGINT NOT NULL,
 expires_at DATETIME(3),
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_reservation_no(tenant_id,reservation_no),
 KEY idx_reservation_status(tenant_id,status,expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE inventory_reservation_item (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 reservation_id BIGINT NOT NULL,
 inventory_item_id BIGINT NOT NULL,
 battery_id BIGINT NOT NULL,
 battery_code VARCHAR(64) NOT NULL,
 status VARCHAR(24) NOT NULL DEFAULT 'RESERVED',
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 KEY idx_reserved_inventory(tenant_id,inventory_item_id),
 KEY idx_reservation_items(tenant_id,reservation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

ALTER TABLE inventory_item
 MODIFY COLUMN status VARCHAR(24) NOT NULL DEFAULT 'AVAILABLE';
