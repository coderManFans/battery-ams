# 仓储服务 API 与业务验收

仓储服务地址：`http://localhost:8082`。OpenAPI JSON：`/v3/api-docs`；Swagger UI：`/swagger-ui.html`。

## 当前 API

| Method | Path | 说明 |
|---|---|---|
| GET | /api/v1/warehouses?tenantId=1 | 仓库列表 |
| POST | /api/v1/warehouses | 创建仓库 |
| GET | /api/v1/warehouses/{id}/locations?tenantId=1 | 库位列表 |
| POST | /api/v1/warehouses/{id}/locations | 创建库位 |
| GET | /api/v1/warehouses/inventory?tenantId=1 | 库存查询 |
| POST | /api/v1/warehouses/inbound | 电池入库 |
| POST | /api/v1/warehouses/outbound | 电池出库 |
| POST | /api/v1/warehouses/transfer | 仓库间调拨 |

## 入库请求示例

```json
{
  "tenantId": 1,
  "warehouseId": 1,
  "locationId": 1,
  "operatorId": 1001,
  "items": [
    { "batteryId": 20001, "batteryCode": "BAT-20001", "modelCode": "LFP-73" }
  ]
}
```

入库完成后创建入库单、库存明细和 INBOUND 流水。重复入库会触发唯一约束并回滚整个请求。

## 出库请求示例

```json
{
  "tenantId": 1,
  "warehouseId": 1,
  "operatorId": 1001,
  "batteryCodes": ["BAT-20001"]
}
```

只允许 AVAILABLE 库存出库。库存行使用 FOR UPDATE 锁定，出库单、库存状态和 OUTBOUND 流水在同一事务中提交；任意一块电池不可出库时整单回滚。

## 调拨请求示例

```json
{
  "tenantId": 1,
  "fromWarehouseId": 1,
  "toWarehouseId": 2,
  "toLocationId": 8,
  "operatorId": 1001,
  "batteryCodes": ["BAT-20001"]
}
```

调拨会锁定调出仓库中的可用库存行，在同一事务中更新仓库/库位归属并记录 TRANSFER 流水；任意一块电池不可调拨时整单回滚。调入库位必须属于目标仓库且处于 ACTIVE 状态。

## 第一批业务验收场景

1. 创建仓库和库位，列表能够查询到。
2. 入库一块电池，库存数量增加且产生 INBOUND 流水。
3. 同一电池重复入库，返回冲突，库存和流水不增加。
4. 对不存在的库位入库，返回参数错误。
5. 正常出库，库存状态变为 DISPATCHED，产生 OUTBOUND 流水。
6. 对已出库电池再次出库，返回冲突。
7. 批量出库中混入一块不可出库电池，整单回滚，不允许部分成功。
8. 不同 tenantId 之间不可查询或操作对方仓库和库存。

## 当前边界与下一步

目前入库 API 接收资产服务传入的 batteryId、batteryCode、modelCode，但尚未通过服务间 API 验证电池是否真实存在、是否允许入库；待统一认证和服务间调用配置完成后补齐。当前 tenantId/operatorId 是演示参数，不是可信身份凭据。后续补充盘点、预占、释放、幂等键、库存事件 Outbox 和端到端测试。
