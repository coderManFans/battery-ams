# Battery AMS

新能源换电电池资产管理平台（Demo）。

## 项目目标

围绕换电电池全生命周期，逐步实现资产档案、仓储库存、工单运维、资产保全、设备遥测和运营分析。

## 技术路线

- 前端：Vue 3 + TypeScript + Vite + Element Plus
- 后端：Java 21 + Spring Boot 3.5 + Maven
- 数据：MySQL 8，按微服务边界隔离
- 中间件：Redis、RabbitMQ
- 本地部署：Docker Compose

## 微服务边界（规划）

| 服务 | 职责 |
|---|---|
| gateway-service | 统一入口、路由和认证接入 |
| system-service | 租户、组织、用户、角色、权限 |
| asset-service | 电池档案、型号、生命周期和状态历史 |
| warehouse-service | 仓库、库位、库存、出入库、调拨和盘点 |
| workorder-service | 维修、检测、巡检工单及任务流转 |
| preservation-service | 风险事件、冻结、巡检和资产轨迹 |
| telemetry-service | BMS 遥测接入、数据记录和告警 |
| analytics-service | 运营指标、报表和读模型 |

## 领域建模原则

电池资产状态、仓储库存状态、健康状态和设备运行状态分开建模。服务只拥有自己的数据，禁止跨服务直接访问数据库。跨服务流程通过 API、领域事件、Outbox、幂等消费和 Saga 补偿协作。

## 开发阶段

1. P0：工程骨架、基础设施、架构与领域文档
2. P1：电池资产档案及生命周期状态机
3. P2：仓储入库、出库、调拨、盘点
4. P3：维修、检测、巡检工单
5. P4：风险识别、资产冻结与保全
6. P5：模拟 BMS 遥测及告警
7. P6：运营分析、端到端集成测试

## 重要说明

这是用于学习和演示的项目，不连接真实 BMS、换电站 PLC、车辆或生产业务系统。所有演示数据均为模拟数据。
