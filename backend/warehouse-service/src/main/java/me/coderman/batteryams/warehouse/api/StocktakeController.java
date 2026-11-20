package me.coderman.batteryams.warehouse.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stocktakes")
public class StocktakeController {
  private final JdbcTemplate jdbc;
  public StocktakeController(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String,Object> create(@Valid @RequestBody CreateStocktakeRequest r) {
    if (jdbc.queryForObject("SELECT COUNT(*) FROM warehouse WHERE id=? AND tenant_id=? AND status='ACTIVE'",Integer.class,r.warehouseId(),r.tenantId()) == 0)
      throw new ResponseStatusException(HttpStatus.NOT_FOUND,"仓库不存在或已停用");
    String no="ST"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase();
    jdbc.update("INSERT INTO stocktake_session(tenant_id,stocktake_no,warehouse_id,status,operator_id) VALUES(?,?,?,'OPEN',?)",
      r.tenantId(),no,r.warehouseId(),r.operatorId());
    Long expected=jdbc.queryForObject("SELECT COUNT(*) FROM inventory_item WHERE tenant_id=? AND warehouse_id=? AND status IN ('AVAILABLE','RESERVED')",Long.class,r.tenantId(),r.warehouseId());
    return Map.of("data",Map.of("stocktakeNo",no,"status","OPEN","expectedItemCount",expected==null?0:expected));
  }

  @PostMapping("/{stocktakeNo}/scans")
  @Transactional
  public Map<String,Object> scan(@PathVariable String stocktakeNo,@Valid @RequestBody ScanRequest r) {
    Map<String,Object> session=lockSession(r.tenantId(),stocktakeNo);
    if (!"OPEN".equals(String.valueOf(session.get("status"))))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"盘点单已关闭");
    long warehouseId=((Number)session.get("warehouse_id")).longValue();
    List<Map<String,Object>> found=jdbc.queryForList("SELECT id,warehouse_id,status FROM inventory_item WHERE tenant_id=? AND battery_code=?",r.tenantId(),r.batteryCode());
    String result;
    Long inventoryId=null;
    if(found.isEmpty()) result="UNREGISTERED";
    else if(((Number)found.get(0).get("warehouse_id")).longValue()!=warehouseId) result="WRONG_WAREHOUSE";
    else if(!List.of("AVAILABLE","RESERVED").contains(String.valueOf(found.get(0).get("status")))) result="NOT_COUNTABLE";
    else { result="MATCHED"; inventoryId=((Number)found.get(0).get("id")).longValue(); }
    try {
      jdbc.update("INSERT INTO stocktake_scan(tenant_id,stocktake_id,battery_code,inventory_item_id,scan_result,scanned_by) VALUES(?,?,?,?,?,?)",
        r.tenantId(),session.get("id"),r.batteryCode(),inventoryId,result,r.operatorId());
    } catch(org.springframework.dao.DuplicateKeyException e) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,"该电池已在本次盘点中扫描");
    }
    return Map.of("data",Map.of("batteryCode",r.batteryCode(),"scanResult",result));
  }

  @PostMapping("/{stocktakeNo}/complete")
  @Transactional
  public Map<String,Object> complete(@PathVariable String stocktakeNo,@Valid @RequestBody CompleteRequest r) {
    Map<String,Object> session=lockSession(r.tenantId(),stocktakeNo);
    if (!"OPEN".equals(String.valueOf(session.get("status"))))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"盘点单已关闭");
    long warehouseId=((Number)session.get("warehouse_id")).longValue();
    Long expected=jdbc.queryForObject("SELECT COUNT(*) FROM inventory_item WHERE tenant_id=? AND warehouse_id=? AND status IN ('AVAILABLE','RESERVED')",Long.class,r.tenantId(),warehouseId);
    Long matched=jdbc.queryForObject("SELECT COUNT(*) FROM stocktake_scan WHERE tenant_id=? AND stocktake_id=? AND scan_result='MATCHED'",Long.class,r.tenantId(),session.get("id"));
    Long missing=jdbc.queryForObject("SELECT COUNT(*) FROM inventory_item i WHERE i.tenant_id=? AND i.warehouse_id=? AND i.status IN ('AVAILABLE','RESERVED') AND NOT EXISTS (SELECT 1 FROM stocktake_scan s WHERE s.tenant_id=i.tenant_id AND s.stocktake_id=? AND s.inventory_item_id=i.id AND s.scan_result='MATCHED')",
      Long.class,r.tenantId(),warehouseId,session.get("id"));
    Long exceptions=jdbc.queryForObject("SELECT COUNT(*) FROM stocktake_scan WHERE tenant_id=? AND stocktake_id=? AND scan_result<>'MATCHED'",Long.class,r.tenantId(),session.get("id"));
    jdbc.update("UPDATE stocktake_session SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND id=? AND status='OPEN'",r.tenantId(),session.get("id"));
    return Map.of("data",Map.of("stocktakeNo",stocktakeNo,"status","COMPLETED","expectedCount",n(expected),"matchedCount",n(matched),"missingCount",n(missing),"exceptionScanCount",n(exceptions)));
  }

  @GetMapping("/{stocktakeNo}")
  public Map<String,Object> detail(@PathVariable String stocktakeNo,@RequestParam(defaultValue="1") long tenantId) {
    Map<String,Object> session=lockSession(tenantId,stocktakeNo);
    List<Map<String,Object>> scans=jdbc.queryForList("SELECT battery_code AS batteryCode,scan_result AS scanResult,scanned_by AS scannedBy,scanned_at AS scannedAt FROM stocktake_scan WHERE tenant_id=? AND stocktake_id=? ORDER BY id",
      tenantId,session.get("id"));
    return Map.of("data",Map.of("session",session,"scans",scans));
  }


  @PostMapping("/{stocktakeNo}/adjustments")
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String,Object> requestAdjustment(@PathVariable String stocktakeNo,@Valid @RequestBody AdjustmentRequest r) {
    Map<String,Object> session=lockSession(r.tenantId(),stocktakeNo);
    if (!"COMPLETED".equals(String.valueOf(session.get("status"))))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"只有已完成的盘点单可以发起差异调整");
    if (!"MARK_LOST".equals(r.adjustmentType()))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"当前仅支持 MARK_LOST 差异调整");
    List<Map<String,Object>> items=jdbc.queryForList("SELECT id,battery_id,battery_code,warehouse_id,status FROM inventory_item WHERE tenant_id=? AND id=?",
      r.tenantId(),r.inventoryItemId());
    if(items.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"库存记录不存在");
    Map<String,Object> item=items.get(0);
    if(((Number)item.get("warehouse_id")).longValue()!=((Number)session.get("warehouse_id")).longValue())
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"库存记录不属于本次盘点仓库");
    if(!"AVAILABLE".equals(String.valueOf(item.get("status"))))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"仅允许对 AVAILABLE 库存发起盘亏调整");
    String no="AD"+UUID.randomUUID().toString().replace("-","").substring(0,16).toUpperCase();
    jdbc.update("INSERT INTO inventory_adjustment(tenant_id,adjustment_no,stocktake_id,inventory_item_id,battery_id,battery_code,warehouse_id,adjustment_type,reason,status,requested_by) VALUES(?,?,?,?,?,?,?,'MARK_LOST',?,'PENDING',?)",
      r.tenantId(),no,session.get("id"),item.get("id"),item.get("battery_id"),item.get("battery_code"),item.get("warehouse_id"),r.reason(),r.operatorId());
    return Map.of("data",Map.of("adjustmentNo",no,"batteryCode",item.get("battery_code"),"status","PENDING"));
  }

  @PostMapping("/adjustments/{adjustmentNo}/approve")
  @Transactional
  public Map<String,Object> approveAdjustment(@PathVariable String adjustmentNo,@Valid @RequestBody ReviewRequest r) {
    List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM inventory_adjustment WHERE tenant_id=? AND adjustment_no=? FOR UPDATE",r.tenantId(),adjustmentNo);
    if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"调整单不存在");
    Map<String,Object> adjustment=rows.get(0);
    if(!"PENDING".equals(String.valueOf(adjustment.get("status"))))
      throw new ResponseStatusException(HttpStatus.CONFLICT,"调整单不是待审核状态");
    int changed=jdbc.update("UPDATE inventory_item SET status='LOST',updated_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND id=? AND status='AVAILABLE'",
      r.tenantId(),adjustment.get("inventory_item_id"));
    if(changed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"库存状态已变化，无法执行盘亏调整");
    String transactionNo=adjustmentNo+"-ADJ";
    jdbc.update("INSERT INTO inventory_transaction(tenant_id,transaction_no,transaction_type,battery_id,battery_code,warehouse_id,order_no,operator_id) VALUES(?,?,'ADJUSTMENT',?,?,?, ?,?)",
      r.tenantId(),transactionNo,adjustment.get("battery_id"),adjustment.get("battery_code"),adjustment.get("warehouse_id"),adjustmentNo,r.operatorId());
    jdbc.update("UPDATE inventory_adjustment SET status='APPROVED',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND id=? AND status='PENDING'",
      r.operatorId(),r.tenantId(),adjustment.get("id"));
    return Map.of("data",Map.of("adjustmentNo",adjustmentNo,"status","APPROVED","batteryCode",adjustment.get("battery_code")));
  }

  @PostMapping("/adjustments/{adjustmentNo}/reject")
  @Transactional
  public Map<String,Object> rejectAdjustment(@PathVariable String adjustmentNo,@Valid @RequestBody ReviewRequest r) {
    int changed=jdbc.update("UPDATE inventory_adjustment SET status='REJECTED',reviewed_by=?,reviewed_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND adjustment_no=? AND status='PENDING'",
      r.operatorId(),r.tenantId(),adjustmentNo);
    if(changed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"调整单不存在或已审核");
    return Map.of("data",Map.of("adjustmentNo",adjustmentNo,"status","REJECTED"));
  }

  private Map<String,Object> lockSession(long tenantId,String no) {
    List<Map<String,Object>> rows=jdbc.queryForList("SELECT id,warehouse_id,status,started_at,completed_at FROM stocktake_session WHERE tenant_id=? AND stocktake_no=? FOR UPDATE",
      tenantId,no);
    if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"盘点单不存在");
    return rows.get(0);
  }
  private long n(Long value) { return value==null?0:value; }

  public record CreateStocktakeRequest(@NotNull Long tenantId,@NotNull Long warehouseId,@NotNull Long operatorId) {}
  public record ScanRequest(@NotNull Long tenantId,@NotBlank String batteryCode,@NotNull Long operatorId) {}
  public record CompleteRequest(@NotNull Long tenantId,@NotNull Long operatorId) {}\n  public record AdjustmentRequest(@NotNull Long tenantId,@NotNull Long inventoryItemId,@NotBlank String adjustmentType,@NotBlank String reason,@NotNull Long operatorId) {}\n  public record ReviewRequest(@NotNull Long tenantId,@NotNull Long operatorId) {}
}
