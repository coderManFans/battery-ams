package me.coderman.batteryams.warehouse.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/warehouses")
public class WarehouseController {
  private final JdbcTemplate jdbc;
  public WarehouseController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @GetMapping
  public Map<String,Object> warehouses(@RequestParam(defaultValue="1") long tenantId) {
    return Map.of("data", jdbc.queryForList("SELECT id,tenant_id AS tenantId,warehouse_code AS warehouseCode,name,status FROM warehouse WHERE tenant_id=? ORDER BY id DESC",tenantId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> createWarehouse(@Valid @RequestBody CreateWarehouseRequest r) {
    try {
      jdbc.update("INSERT INTO warehouse(tenant_id,warehouse_code,name,address,status) VALUES(?,?,?,?,'ACTIVE')",
        r.tenantId(),r.warehouseCode(),r.name(),r.address());
    } catch(org.springframework.dao.DuplicateKeyException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,"仓库编码已存在");
    }
    return Map.of("data",jdbc.queryForMap("SELECT id,warehouse_code AS warehouseCode,name,status FROM warehouse WHERE tenant_id=? AND warehouse_code=?",r.tenantId(),r.warehouseCode()));
  }

  @GetMapping("/{warehouseId}/locations")
  public Map<String,Object> locations(@PathVariable long warehouseId,@RequestParam(defaultValue="1") long tenantId) {
    return Map.of("data",jdbc.queryForList("SELECT id,location_code AS locationCode,name,status FROM warehouse_location WHERE tenant_id=? AND warehouse_id=? ORDER BY id",tenantId,warehouseId));
  }

  @PostMapping("/{warehouseId}/locations")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> createLocation(@PathVariable long warehouseId,@Valid @RequestBody CreateLocationRequest r) {
    requireWarehouse(r.tenantId(),warehouseId);
    try {
      jdbc.update("INSERT INTO warehouse_location(tenant_id,warehouse_id,location_code,name,status) VALUES(?,?,?,?,'ACTIVE')",
        r.tenantId(),warehouseId,r.locationCode(),r.name());
    } catch(org.springframework.dao.DuplicateKeyException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,"库位编码已存在");
    }
    return Map.of("data",jdbc.queryForMap("SELECT id,location_code AS locationCode,name,status FROM warehouse_location WHERE tenant_id=? AND warehouse_id=? AND location_code=?",r.tenantId(),warehouseId,r.locationCode()));
  }

  @GetMapping("/inventory")
  public Map<String,Object> inventory(@RequestParam(defaultValue="1") long tenantId,
      @RequestParam(required=false) Long warehouseId,@RequestParam(required=false) String status) {
    StringBuilder sql=new StringBuilder("SELECT i.id,i.battery_id AS batteryId,i.battery_code AS batteryCode,i.model_code AS modelCode,i.status,w.warehouse_code AS warehouseCode,l.location_code AS locationCode,i.received_at AS receivedAt FROM inventory_item i JOIN warehouse w ON w.id=i.warehouse_id LEFT JOIN warehouse_location l ON l.id=i.location_id WHERE i.tenant_id=?");
    java.util.ArrayList<Object> args=new java.util.ArrayList<>(); args.add(tenantId);
    if(warehouseId!=null){sql.append(" AND i.warehouse_id=?");args.add(warehouseId);}
    if(status!=null&&!status.isBlank()){sql.append(" AND i.status=?");args.add(status);}
    sql.append(" ORDER BY i.id DESC LIMIT 500");
    return Map.of("data",jdbc.queryForList(sql.toString(),args.toArray()));
  }

  @PostMapping("/inbound")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String,Object> inbound(@Valid @RequestBody InboundRequest r) {
    validateInboundItems(r.items());
    requireWarehouse(r.tenantId(),r.warehouseId());
    if(jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_location WHERE id=? AND warehouse_id=? AND tenant_id=? AND status='ACTIVE'",Integer.class,r.locationId(),r.warehouseId(),r.tenantId())==0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"库位不存在或不可用");
    String orderNo="IN"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase();
    jdbc.update("INSERT INTO inbound_order(tenant_id,order_no,warehouse_id,status,operator_id) VALUES(?,?,?,'COMPLETED',?)",r.tenantId(),orderNo,r.warehouseId(),r.operatorId());
    for(BatteryLine line:r.items()) {
      try {
        jdbc.update("INSERT INTO inventory_item(tenant_id,battery_id,battery_code,model_code,warehouse_id,location_id,status) VALUES(?,?,?,?,?,?,'AVAILABLE')",
          r.tenantId(),line.batteryId(),line.batteryCode(),line.modelCode(),r.warehouseId(),r.locationId());
      } catch(org.springframework.dao.DuplicateKeyException e) {
        throw new ResponseStatusException(HttpStatus.CONFLICT,"电池已存在于库存或重复入库: "+line.batteryCode());
      }
      jdbc.update("INSERT INTO inventory_transaction(tenant_id,transaction_no,transaction_type,battery_id,battery_code,warehouse_id,to_location_id,order_no,operator_id) VALUES(?,?,'INBOUND',?,?,?,?,?,?)",
        r.tenantId(),orderNo+"-"+line.batteryCode(),line.batteryId(),line.batteryCode(),r.warehouseId(),r.locationId(),orderNo,r.operatorId());
    }
    return Map.of("data",Map.of("orderNo",orderNo,"status","COMPLETED","itemCount",r.items().size()));
  }

  @PostMapping("/outbound")
  @Transactional
  public Map<String,Object> outbound(@Valid @RequestBody OutboundRequest r) {
    requireWarehouse(r.tenantId(),r.warehouseId());
    validateBatteryCodes(r.batteryCodes());
    String orderNo="OUT"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase();
    jdbc.update("INSERT INTO outbound_order(tenant_id,order_no,warehouse_id,status,operator_id) VALUES(?,?,?,'PROCESSING',?)",r.tenantId(),orderNo,r.warehouseId(),r.operatorId());
    for(String batteryCode:r.batteryCodes()) {
      List<Map<String,Object>> found=jdbc.queryForList("SELECT id,battery_id FROM inventory_item WHERE tenant_id=? AND warehouse_id=? AND battery_code=? AND status='AVAILABLE' FOR UPDATE",r.tenantId(),r.warehouseId(),batteryCode);
      if(found.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"电池不在该仓库可用库存中: "+batteryCode);
      long itemId=((Number)found.get(0).get("id")).longValue();
      long batteryId=((Number)found.get(0).get("battery_id")).longValue();
      jdbc.update("UPDATE inventory_item SET status='DISPATCHED',updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND status='AVAILABLE'",itemId);
      jdbc.update("INSERT INTO inventory_transaction(tenant_id,transaction_no,transaction_type,battery_id,battery_code,warehouse_id,order_no,operator_id) VALUES(?,?,'OUTBOUND',?,?,?, ?,?)",
        r.tenantId(),orderNo+"-"+batteryCode,batteryId,batteryCode,r.warehouseId(),orderNo,r.operatorId());
    }
    jdbc.update("UPDATE outbound_order SET status='COMPLETED' WHERE tenant_id=? AND order_no=?",r.tenantId(),orderNo);
    return Map.of("data",Map.of("orderNo",orderNo,"status","COMPLETED","itemCount",r.batteryCodes().size()));
  }


  @PostMapping("/transfer")
  @Transactional
  public Map<String,Object> transfer(@Valid @RequestBody TransferRequest r) {
    requireWarehouse(r.tenantId(),r.fromWarehouseId());
    requireWarehouse(r.tenantId(),r.toWarehouseId());
    if(jdbc.queryForObject("SELECT COUNT(*) FROM warehouse_location WHERE id=? AND warehouse_id=? AND tenant_id=? AND status='ACTIVE'",Integer.class,r.toLocationId(),r.toWarehouseId(),r.tenantId())==0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"调入库位不存在或不可用");
    validateBatteryCodes(r.batteryCodes());
    String orderNo="TR"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase();
    for(String batteryCode:r.batteryCodes()) {
      List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,battery_id,location_id FROM inventory_item WHERE tenant_id=? AND warehouse_id=? AND battery_code=? AND status='AVAILABLE' FOR UPDATE",r.tenantId(),r.fromWarehouseId(),batteryCode);
      if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"电池不在调出仓库的可用库存中: "+batteryCode);
      long itemId=((Number)rows.get(0).get("id")).longValue();
      long batteryId=((Number)rows.get(0).get("battery_id")).longValue();
      long fromLocationId=((Number)rows.get(0).get("location_id")).longValue();
      jdbc.update("UPDATE inventory_item SET warehouse_id=?,location_id=?,updated_at=CURRENT_TIMESTAMP(3) WHERE id=? AND status='AVAILABLE'",r.toWarehouseId(),r.toLocationId(),itemId);
      jdbc.update("INSERT INTO inventory_transaction(tenant_id,transaction_no,transaction_type,battery_id,battery_code,warehouse_id,from_location_id,to_location_id,order_no,operator_id) VALUES(?,?,'TRANSFER',?,?,?,?,?,?,?)",
        r.tenantId(),orderNo+"-"+batteryCode,batteryId,batteryCode,r.fromWarehouseId(),fromLocationId,r.toLocationId(),orderNo,r.operatorId());
    }
    return Map.of("data",Map.of("orderNo",orderNo,"status","COMPLETED","itemCount",r.batteryCodes().size(),"fromWarehouseId",r.fromWarehouseId(),"toWarehouseId",r.toWarehouseId()));
  }

  private void validateInboundItems(List<BatteryLine> items) {
    if(items==null||items.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"入库明细不能为空");
    if(items.size()>200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"单次入库最多 200 块电池");
    java.util.HashSet<Long> ids=new java.util.HashSet<>();
    java.util.HashSet<String> codes=new java.util.HashSet<>();
    for(BatteryLine item:items) {
      if(!ids.add(item.batteryId())||!codes.add(item.batteryCode()))
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"同一入库单不能包含重复电池");
    }
  }

  private void validateBatteryCodes(List<String> codes) {
    if(codes==null||codes.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"电池编号不能为空");
    if(codes.size()>200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"单次操作最多 200 块电池");
    if(new java.util.HashSet<>(codes).size()!=codes.size())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"电池编号不能重复");
  }

  private void requireWarehouse(long tenantId,long warehouseId) {
    if(jdbc.queryForObject("SELECT COUNT(*) FROM warehouse WHERE id=? AND tenant_id=? AND status='ACTIVE'",Integer.class,warehouseId,tenantId)==0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,"仓库不存在或已停用");
  }

  public record CreateWarehouseRequest(@NotNull Long tenantId,@NotBlank String warehouseCode,@NotBlank String name,String address) {}
  public record CreateLocationRequest(@NotNull Long tenantId,@NotBlank String locationCode,@NotBlank String name) {}
  public record BatteryLine(@NotNull Long batteryId,@NotBlank String batteryCode,@NotBlank String modelCode) {}
  public record InboundRequest(@NotNull Long tenantId,@NotNull Long warehouseId,@NotNull Long locationId,@NotNull Long operatorId,@NotEmpty List<@Valid BatteryLine> items) {}
  public record OutboundRequest(@NotNull Long tenantId,@NotNull Long warehouseId,@NotNull Long operatorId,@NotEmpty List<@NotBlank String> batteryCodes) {}
  public record TransferRequest(@NotNull Long tenantId,@NotNull Long fromWarehouseId,@NotNull Long toWarehouseId,@NotNull Long toLocationId,@NotNull Long operatorId,@NotEmpty List<@NotBlank String> batteryCodes) {}
}