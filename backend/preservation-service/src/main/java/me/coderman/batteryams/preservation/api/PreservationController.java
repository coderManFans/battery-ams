package me.coderman.batteryams.preservation.api;

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
@RequestMapping("/api/v1")
public class PreservationController {
  private final JdbcTemplate jdbc;
  public PreservationController(JdbcTemplate jdbc){this.jdbc=jdbc;}

  @GetMapping("/risk-events")
  public Map<String,Object> risks(@RequestParam(defaultValue="1") long tenantId,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){
    if(page<1||size<1||size>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分页参数无效");
    String where=" WHERE tenant_id=?"+(status==null||status.isBlank()?"":" AND status=?");
    Object[] args=status==null||status.isBlank()?new Object[]{tenantId}:new Object[]{tenantId,status};
    Long total=jdbc.queryForObject("SELECT COUNT(*) FROM risk_event"+where,args,Long.class);
    var rows=jdbc.queryForList("SELECT id,risk_no AS riskNo,battery_id AS batteryId,battery_code AS batteryCode,risk_type AS riskType,severity,source,description,status,reported_by AS reportedBy,created_at AS createdAt FROM risk_event"+where+" ORDER BY created_at DESC LIMIT ? OFFSET ?",append(args,size,(page-1)*size));
    return Map.of("data",rows,"page",page,"size",size,"total",total==null?0:total);
  }

  @PostMapping("/risk-events") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Map<String,Object> createRisk(@Valid @RequestBody RiskRequest r){
    if(!List.of("LOW","MEDIUM","HIGH","CRITICAL").contains(r.severity()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"风险等级无效");
    if(!List.of("BMS","MANUAL","INSPECTION","SYSTEM").contains(r.source()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"风险来源无效");
    String no="RK"+UUID.randomUUID().toString().replace("-","").substring(0,18).toUpperCase();
    jdbc.update("INSERT INTO risk_event(tenant_id,risk_no,battery_id,battery_code,risk_type,severity,source,description,reported_by) VALUES(?,?,?,?,?,?,?,?,?)",r.tenantId(),no,r.batteryId(),r.batteryCode(),r.riskType(),r.severity(),r.source(),r.description(),r.reportedBy());
    long id=jdbc.queryForObject("SELECT id FROM risk_event WHERE tenant_id=? AND risk_no=?",Long.class,r.tenantId(),no);
    return Map.of("data",Map.of("id",id,"riskNo",no,"status","OPEN"));
  }

  @PostMapping("/risk-events/{id}/freeze") @Transactional
  public Map<String,Object> freeze(@PathVariable long id,@Valid @RequestBody FreezeRequest r){
    var risks=jdbc.queryForList("SELECT battery_id,battery_code,status FROM risk_event WHERE tenant_id=? AND id=? FOR UPDATE",r.tenantId(),id);
    if(risks.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"风险事件不存在");
    var risk=risks.get(0);if(risk.get("battery_id")==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"该风险事件未关联电池资产");
    if(!"OPEN".equals(String.valueOf(risk.get("status"))))throw new ResponseStatusException(HttpStatus.CONFLICT,"风险事件不是待处理状态");
    jdbc.update("INSERT INTO asset_freeze_record(tenant_id,battery_id,battery_code,risk_event_id,freeze_reason,frozen_by) VALUES(?,?,?,?,?,?)",r.tenantId(),risk.get("battery_id"),risk.get("battery_code"),id,r.reason(),r.operatorId());
    jdbc.update("UPDATE risk_event SET status='FROZEN' WHERE tenant_id=? AND id=?",r.tenantId(),id);
    return Map.of("data",Map.of("riskEventId",id,"batteryId",risk.get("battery_id"),"status","FROZEN"));
  }

  @PostMapping("/risk-events/{id}/resolve") @Transactional
  public Map<String,Object> resolve(@PathVariable long id,@Valid @RequestBody ResolveRequest r){
    int changed=jdbc.update("UPDATE risk_event SET status='RESOLVED',resolved_by=?,resolved_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND id=? AND status IN ('OPEN','FROZEN')",r.operatorId(),r.tenantId(),id);
    if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"风险事件不存在或已处理");
    jdbc.update("UPDATE asset_freeze_record SET status='RELEASED',released_by=?,released_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND risk_event_id=? AND status='ACTIVE'",r.operatorId(),r.tenantId(),id);
    return Map.of("data",Map.of("riskEventId",id,"status","RESOLVED"));
  }

  @PostMapping("/inspections") @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> inspect(@Valid @RequestBody InspectionRequest r){
    if(!List.of("PASS","ATTENTION","FAIL").contains(r.result()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"检查结果无效");
    jdbc.update("INSERT INTO inspection_record(tenant_id,battery_id,battery_code,inspection_type,result,findings,inspected_by) VALUES(?,?,?,?,?,?,?)",r.tenantId(),r.batteryId(),r.batteryCode(),r.inspectionType(),r.result(),r.findings(),r.inspectedBy());
    return Map.of("data",Map.of("batteryId",r.batteryId(),"batteryCode",r.batteryCode(),"result",r.result()));
  }

  @GetMapping("/inspections")
  public Map<String,Object> inspections(@RequestParam(defaultValue="1") long tenantId,@RequestParam(required=false) String batteryCode){
    if(batteryCode==null||batteryCode.isBlank())return Map.of("data",jdbc.queryForList("SELECT id,battery_id AS batteryId,battery_code AS batteryCode,inspection_type AS inspectionType,result,findings,inspected_by AS inspectedBy,inspected_at AS inspectedAt FROM inspection_record WHERE tenant_id=? ORDER BY inspected_at DESC LIMIT 200",tenantId));
    return Map.of("data",jdbc.queryForList("SELECT id,battery_id AS batteryId,battery_code AS batteryCode,inspection_type AS inspectionType,result,findings,inspected_by AS inspectedBy,inspected_at AS inspectedAt FROM inspection_record WHERE tenant_id=? AND battery_code=? ORDER BY inspected_at DESC LIMIT 200",tenantId,batteryCode));
  }

  private Object[] append(Object[] a,Object... b){Object[] c=java.util.Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,c,a.length,b.length);return c;}
  public record RiskRequest(@NotNull Long tenantId,Long batteryId,String batteryCode,@NotBlank String riskType,@NotBlank String severity,@NotBlank String source,@NotBlank String description,@NotNull Long reportedBy){}
  public record FreezeRequest(@NotNull Long tenantId,@NotBlank String reason,@NotNull Long operatorId){}
  public record ResolveRequest(@NotNull Long tenantId,@NotNull Long operatorId){}
  public record InspectionRequest(@NotNull Long tenantId,@NotNull Long batteryId,@NotBlank String batteryCode,@NotBlank String inspectionType,@NotBlank String result,String findings,@NotNull Long inspectedBy){}
}
