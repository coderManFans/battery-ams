# DDD 领域模型

## Asset Context
聚合根 BatteryAsset；值对象 BatteryCode、BatteryStatus、AssetOwner；实体 BatteryModel、BatteryStatusHistory、BatteryLocationHistory。行为：register、activate、putIntoService、freeze、sendToRepair、restore、retire。

不变量：租户内资产编码唯一；型号有效；退役不可恢复；冻结资产不能普通出库；状态变化记录原因、操作者、时间；并发更新使用 version 乐观锁。

## Warehouse Context
聚合根 Warehouse、Inventory、InboundOrder、OutboundOrder、TransferOrder、StocktakeOrder。库存变化必须有流水，电池级明细防止重复预占/出库。

## Workorder Context
聚合根 WorkOrder；任务、分配、审批、操作日志为内部实体。类型 REPAIR、INSPECTION、MAINTENANCE、EXCEPTION。

## Workorder Service API（第一版）\n- `GET /api/v1/work-orders`：分页查询，支持状态和类型过滤。\n- `GET /api/v1/work-orders/{id}`：工单详情及流转日志。\n- `POST /api/v1/work-orders`：创建维修、检测、巡检、异常工单。\n- `POST /api/v1/work-orders/{id}/assign`：分派处理人。\n- `POST /api/v1/work-orders/{id}/transitions`：按状态机推进工单。\n\n状态：PENDING → ASSIGNED → ACCEPTED → IN_PROGRESS → WAITING_REVIEW → COMPLETED；允许按规则取消，审核退回可从 WAITING_REVIEW 回到 IN_PROGRESS。\n\n## Preservation Context
RiskEvent、AssetFreeze、InspectionRecord，负责风险识别、冻结请求和处置，不直接修改资产库。

## Preservation Service API（第一版）\n- `GET /api/v1/risk-events`：风险事件分页查询。\n- `POST /api/v1/risk-events`：登记风险事件。\n- `POST /api/v1/risk-events/{id}/freeze`：对关联资产创建冻结记录。\n- `POST /api/v1/risk-events/{id}/resolve`：处置风险并释放对应冻结记录。\n- `GET/POST /api/v1/inspections`：巡检记录查询与登记。\n\n说明：保全服务维护自己的风险和冻结记录，不直接写 Asset Service 数据库；跨服务冻结状态同步将在后续领域事件联调阶段完成。\n\n## Telemetry Context
设备与遥测时间序列独立建模。SOC、SOH、温度、电压是观测数据，不是资产主表状态。

领域事件：BatteryRegistered、BatteryStatusChanged、BatteryFrozen、InventoryReceived、InventoryDispatched、WorkOrderCreated、RiskDetected、TelemetryAlarmRaised。