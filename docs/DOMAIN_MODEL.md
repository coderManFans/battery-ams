# DDD 领域模型

## Asset Context
聚合根 BatteryAsset；值对象 BatteryCode、BatteryStatus、AssetOwner；实体 BatteryModel、BatteryStatusHistory、BatteryLocationHistory。行为：register、activate、putIntoService、freeze、sendToRepair、restore、retire。

不变量：租户内资产编码唯一；型号有效；退役不可恢复；冻结资产不能普通出库；状态变化记录原因、操作者、时间；并发更新使用 version 乐观锁。

## Warehouse Context
聚合根 Warehouse、Inventory、InboundOrder、OutboundOrder、TransferOrder、StocktakeOrder。库存变化必须有流水，电池级明细防止重复预占/出库。

## Workorder Context
聚合根 WorkOrder；任务、分配、审批、操作日志为内部实体。类型 REPAIR、INSPECTION、MAINTENANCE、EXCEPTION。

## Preservation Context
RiskEvent、AssetFreeze、InspectionRecord，负责风险识别、冻结请求和处置，不直接修改资产库。

## Telemetry Context
设备与遥测时间序列独立建模。SOC、SOH、温度、电压是观测数据，不是资产主表状态。

领域事件：BatteryRegistered、BatteryStatusChanged、BatteryFrozen、InventoryReceived、InventoryDispatched、WorkOrderCreated、RiskDetected、TelemetryAlarmRaised。