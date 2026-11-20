# 微服务架构

| 服务 | 职责 | 数据库 |
|---|---|---|
| gateway-service | 统一入口、路由、认证接入 | 无业务库 |
| system-service | 租户、组织、用户、角色、权限 | system_db |
| asset-service | 电池档案、型号、生命周期、状态历史 | asset_db |
| warehouse-service | 仓库、库位、库存、出入库、调拨、盘点 | warehouse_db |
| workorder-service | 工单、任务、派单、执行、审批 | workorder_db |
| preservation-service | 风险、冻结、巡检、资产轨迹 | preservation_db |
| telemetry-service | BMS 适配、遥测、告警 | telemetry_db |
| analytics-service | 指标聚合、报表、读模型 | analytics_db |

同步 REST 用于即时查询和命令；Apache RocketMQ 领域事件用于异步协作。事件至少一次投递，消费者必须幂等。生产采用 Outbox + Relay；当前仓储服务使用 RocketMQ Topic `battery-warehouse-events`，标签区分 INBOUND_COMPLETED、OUTBOUND_COMPLETED、TRANSFER_COMPLETED。Outbox 事件至少一次投递，事件包含 eventId/eventType/eventVersion/tenantId/aggregateId，消费者必须按 eventId 幂等。Asset Service 使用 Redis SET NX + TTL 作为第一层并发互斥，并以 asset_db.consumed_event 的 UNIQUE(event_id) 作为最终幂等约束；Redis 锁释放使用 Lua compare-and-delete，避免误删其他消费者持有的锁。Redis 锁不是正确性的唯一保障，Redis 不可用时消费者失败并等待 RocketMQ 重试。跨服务流程使用 Saga/补偿。每服务独立拥有数据库和凭据，禁止跨服务 SQL JOIN。开发可用一个 MySQL 容器承载多个 schema，生产按服务隔离实例/集群。先交付真实业务垂直切片，不制造空壳服务。