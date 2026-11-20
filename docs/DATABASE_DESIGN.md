# 数据库设计

## Schema
system_db、asset_db、warehouse_db、workorder_db、preservation_db、telemetry_db、analytics_db。开发环境可由一个 MySQL 容器承载多个 schema，生产隔离服务账号和实例。

## asset_db.battery_asset
| 字段 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| tenant_id | BIGINT | 租户 |
| battery_code | VARCHAR(64) | 租户内唯一编码 |
| serial_no | VARCHAR(128) | 租户内唯一序列号 |
| model_code | VARCHAR(64) | 型号编码 |
| owner_org_id | BIGINT | 权属组织 |
| lifecycle_status | VARCHAR(32) | 生命周期状态 |
| health_status | VARCHAR(32) | 健康状态 |
| current_location_type/id | VARCHAR/BIGINT | 外部位置引用 |
| version | BIGINT | 乐观锁版本 |
| created_at/updated_at | DATETIME(3) | 审计时间 |

唯一索引 tenant_id+battery_code、tenant_id+serial_no；查询索引 tenant_id+status。battery_status_history 记录前后状态、原因、操作者、时间、关联 ID。其他服务不建立跨库外键；遥测大表与资产主表分离。