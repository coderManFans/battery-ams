CREATE TABLE battery_model (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 tenant_id BIGINT NOT NULL,
 model_code VARCHAR(64) NOT NULL,
 model_name VARCHAR(128) NOT NULL,
 manufacturer VARCHAR(128) NOT NULL,
 chemistry VARCHAR(32) NOT NULL,
 nominal_voltage DECIMAL(8,2) NOT NULL,
 nominal_capacity_ah DECIMAL(8,2) NOT NULL,
 rated_energy_wh DECIMAL(10,2) NOT NULL,
 cycle_life INT NOT NULL,
 enabled TINYINT(1) NOT NULL DEFAULT 1,
 created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
 UNIQUE KEY uk_model_tenant_code(tenant_id,model_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO battery_model(tenant_id,model_code,model_name,manufacturer,chemistry,nominal_voltage,nominal_capacity_ah,rated_energy_wh,cycle_life,enabled)
VALUES
(1,'LFP-73','73Ah 磷酸铁锂电池包','Demo Battery Co.','LFP',48.00,73.00,3504.00,2000,1),
(1,'LFP-52','52Ah 磷酸铁锂电池包','Demo Battery Co.','LFP',48.00,52.00,2496.00,1800,1);

ALTER TABLE battery_asset
 ADD CONSTRAINT fk_asset_model FOREIGN KEY(tenant_id,model_code)
 REFERENCES battery_model(tenant_id,model_code);
