# AI 研发测试平台集成方案

## 集成目标
将 Battery AMS 注册为测试平台中的一个被测应用。测试平台通过 HTTP 调用业务 API，导入 OpenAPI 文档，关联需求/技术方案，生成并执行接口用例、业务场景用例，最后产出执行报告。

## 当前测试环境
- 服务：asset-service
- Base URL：`http://localhost:8081`
- OpenAPI：`http://localhost:8081/v3/api-docs`
- Swagger UI：`http://localhost:8081/swagger-ui/index.html`
- 健康检查：`http://localhost:8081/actuator/health`
- 当前认证：Demo 阶段暂未启用认证；接入平台前应通过测试环境网关或后续 JWT/OAuth2 配置保护，不要将无认证服务暴露到公网。
- 当前租户：演示数据 tenantId=1，operatorId=1001。

## 在测试平台中的配置建议
1. 创建应用：Battery AMS。
2. 创建端点：asset-service，Base URL 填测试环境地址。
3. 导入接口文档：使用 OpenAPI URL `/v3/api-docs`。
4. 建立需求：电池资产建档、状态流转、状态历史查询、租户数据隔离。
5. 建立技术方案：资产服务以 battery_asset 为聚合根持久化资产；状态变更与历史记录在同一事务内完成；通过 tenantId 做当前版本的数据隔离。
6. 选择端点：asset-service。
7. 生成接口测试用例，再补充下文业务场景用例。

## API 契约
| 方法 | 路径 | 用途 |
|---|---|---|
| GET | /api/v1/assets?tenantId=1&page=1&size=20 | 分页查询资产 |
| POST | /api/v1/assets | 创建资产 |
| GET | /api/v1/assets/{id}?tenantId=1 | 资产详情 |
| GET | /api/v1/assets/{id}/history?tenantId=1 | 状态变更历史 |
| POST | /api/v1/assets/{id}/transitions | 生命周期状态迁移 |

### 创建资产
```json
{
  "tenantId": 1,
  "batteryCode": "BAT-TEST-10001",
  "serialNo": "SN-TEST-10001",
  "modelCode": "LFP-73",
  "ownerOrgId": 100,
  "soc": 85,
  "soh": 98.5
}
```
预期：HTTP 201，初始状态 REGISTERED。

### 状态迁移
```json
{
  "tenantId": 1,
  "toStatus": "AVAILABLE",
  "reason": "入库验收合格",
  "operatorId": 1001
}
```
预期：HTTP 200，返回 fromStatus / toStatus；资产状态和历史表在同一事务内更新。

## 建议优先生成的测试场景
| 场景编号 | 场景 | 核心断言 |
|---|---|---|
| ASSET-001 | 创建一块新电池 | 201；状态 REGISTERED |
| ASSET-002 | 相同租户重复 batteryCode | 409；不得产生第二条资产 |
| ASSET-003 | 相同租户重复 serialNo | 409 |
| ASSET-004 | SOC 小于 0 或大于 100 | 400 |
| ASSET-005 | REGISTERED → AVAILABLE | 成功；历史新增一条 |
| ASSET-006 | REGISTERED → IN_SERVICE | 409；状态和历史均不变化 |
| ASSET-007 | AVAILABLE → FROZEN → UNDER_REPAIR | 两次迁移成功；历史顺序正确 |
| ASSET-008 | RETIRED 后尝试恢复 AVAILABLE | 409 |
| ASSET-009 | 查询不存在的资产详情 | 404 |
| ASSET-010 | tenant A 查询 tenant B 的资产 | 404，不泄漏资产信息 |
| ASSET-011 | 分页 size 超过 200 | 400 |
| ASSET-012 | 资产列表按状态/关键字过滤 | 返回数据符合过滤条件 |

## 端到端业务链路（后续服务完成后）
电池建档 → 仓库入库 → 入库验收 → 上架 → 出库调拨 → 换电站接收 → 投入运营 → BMS 告警 → 资产冻结 → 创建维修工单 → 维修验收 → 恢复可用 → 重新投放。

每一步验证：业务状态、关联单据、库存数量、操作历史、事件投递、重复请求幂等和异常补偿。

## 测试数据策略
- 每条用例使用唯一 batteryCode / serialNo（建议用测试运行 ID 作为后缀）。
- 不依赖固定自增 ID；通过创建接口响应提取 id，再传给后续步骤。
- 测试结束后执行清理脚本或调用专用测试数据清理接口；禁止直接清理共享环境的业务数据。
- 业务测试与接口测试分层：接口测试验证单 API 契约，业务场景测试验证跨 API 状态链路。
- 报告保留 request/response、traceId、执行时间、断言结果和测试数据标识。

## 当前限制与接入前置事项
- 当前仅 asset-service 已实现；仓储、工单、遥测等服务仍在规划/开发中。
- 当前 tenantId 是请求参数，不是安全边界。正式多租户上线前必须由认证上下文注入租户身份，并禁止客户端任意指定 tenantId。
- 当前 Demo 暂未实现幂等键、审计 traceId、消息 Outbox 和跨服务补偿；后续迭代补齐。
- API 结构会持续演进，平台用例应从 OpenAPI 导入并纳入契约回归。
