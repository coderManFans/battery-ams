package me.coderman.batteryams.workorder.api;

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
@RequestMapping("/api/v1/work-orders")
public class WorkOrderController {
  private final JdbcTemplate jdbc;
  public WorkOrderController(JdbcTemplate jdbc){this.jdbc=jdbc;}

  @GetMapping
  public Map<String,Object> list(@RequestParam(defaultValue="1") long tenantId,@RequestParam(required=false) String status,@RequestParam(required=false) String type,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){
    if(page<1||size<1||size>200) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分页参数无效");
    StringBuilder where=new StringBuilder(" WHERE tenant_id=?"); java.util.ArrayList<Object> args=new java.util.ArrayList<>();args.add(tenantId);
    if(status!=null&&!status.isBlank()){where.append(" AND status=?");args.add(status);}
    if(type!=null&&!type.isBlank()){where.append(" AND work_order_type=?");args.add(type);}
    Long total=jdbc.queryForObject("SELECT COUNT(*) FROM work_order"+where,args.toArray(),Long.class);
    args.add(size);args.add((page-1)*size);
    return Map.of("data",jdbc.queryForList("SELECT id,work_order_no AS workOrderNo,work_order_type AS workOrderType,battery_id AS batteryId,battery_code AS batteryCode,title,description,priority,status,assignee_id AS assigneeId,reporter_id AS reporterId,due_at AS dueAt,version,created_at AS createdAt,updated_at AS updatedAt FROM work_order"+where+" ORDER BY created_at DESC LIMIT ? OFFSET ?",args.toArray()),"page",page,"size",size,"total",total==null?0:total);
  }

  @GetMapping("/{id}")
  public Map<String,Object> detail(@PathVariable long id,@RequestParam(defaultValue="1") long tenantId){
    var rows=jdbc.queryForList("SELECT id,work_order_no AS workOrderNo,work_order_type AS workOrderType,battery_id AS batteryId,battery_code AS batteryCode,title,description,priority,status,assignee_id AS assigneeId,reporter_id AS reporterId,due_at AS dueAt,version,created_at AS createdAt,updated_at AS updatedAt FROM work_order WHERE tenant_id=? AND id=?",tenantId,id);
    if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"工单不存在");
    return Map.of("data",rows.get(0),"logs",jdbc.queryForList("SELECT action,from_status AS fromStatus,to_status AS toStatus,comment,operator_id AS operatorId,created_at AS createdAt FROM work_order_log WHERE tenant_id=? AND work_order_id=? ORDER BY id",tenantId,id));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String,Object> create(@Valid @RequestBody CreateRequest r){
    if(!List.of("REPAIR","INSPECTION","MAINTENANCE","EXCEPTION").contains(r.type())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"不支持的工单类型");
    if(!List.of("LOW","NORMAL","HIGH","URGENT").contains(r.priority())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"不支持的优先级");
    String no="WO"+UUID.randomUUID().toString().replace("-","").substring(0,18).toUpperCase();
    jdbc.update("INSERT INTO work_order(tenant_id,work_order_no,work_order_type,battery_id,battery_code,title,description,priority,status,reporter_id,due_at) VALUES(?,?,?,?,?,?,?,?,'PENDING',?,?)",r.tenantId(),no,r.type(),r.batteryId(),r.batteryCode(),r.title(),r.description(),r.priority(),r.reporterId(),r.dueAt());
    long id=jdbc.queryForObject("SELECT id FROM work_order WHERE tenant_id=? AND work_order_no=?",Long.class,r.tenantId(),no);
    log(r.tenantId(),id,"CREATED",null,"PENDING","工单创建",r.reporterId());
    return Map.of("data",Map.of("id",id,"workOrderNo",no,"status","PENDING"));
  }

  @PostMapping("/{id}/assign")
  @Transactional
  public Map<String,Object> assign(@PathVariable long id,@Valid @RequestBody AssignRequest r){
    var rows=jdbc.queryForList("SELECT status FROM work_order WHERE tenant_id=? AND id=? FOR UPDATE",r.tenantId(),id);
    if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"工单不存在");
    String from=String.valueOf(rows.get(0).get("status"));
    if(!List.of("PENDING","ASSIGNED").contains(from)) throw new ResponseStatusException(HttpStatus.CONFLICT,"当前状态不能分派");
    jdbc.update("UPDATE work_order SET assignee_id=?,status='ASSIGNED',version=version+1 WHERE tenant_id=? AND id=?",r.assigneeId(),r.tenantId(),id);
    log(r.tenantId(),id,"ASSIGNED",from,"ASSIGNED",r.comment(),r.operatorId());
    return Map.of("data",Map.of("id",id,"status","ASSIGNED","assigneeId",r.assigneeId()));
  }

  @PostMapping("/{id}/transitions")
  @Transactional
  public Map<String,Object> transition(@PathVariable long id,@Valid @RequestBody TransitionRequest r){
    var rows=jdbc.queryForList("SELECT status,version FROM work_order WHERE tenant_id=? AND id=? FOR UPDATE",r.tenantId(),id);
    if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"工单不存在");
    String from=String.valueOf(rows.get(0).get("status")); long version=((Number)rows.get(0).get("version")).longValue();
    if(!allowed(from,r.toStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"非法工单状态流转: "+from+" -> "+r.toStatus());
    int changed=jdbc.update("UPDATE work_order SET status=?,version=version+1 WHERE tenant_id=? AND id=? AND version=?",r.toStatus(),r.tenantId(),id,version);
    if(changed!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"工单版本冲突");
    log(r.tenantId(),id,"STATUS_CHANGED",from,r.toStatus(),r.comment(),r.operatorId());
    return Map.of("data",Map.of("id",id,"fromStatus",from,"toStatus",r.toStatus()));
  }

  private boolean allowed(String from,String to){return switch(from){case "PENDING"->List.of("CANCELLED").contains(to);case "ASSIGNED"->List.of("ACCEPTED","CANCELLED").contains(to);case "ACCEPTED"->List.of("IN_PROGRESS","CANCELLED").contains(to);case "IN_PROGRESS"->List.of("WAITING_REVIEW","CANCELLED").contains(to);case "WAITING_REVIEW"->List.of("COMPLETED","IN_PROGRESS").contains(to);default->false;};}
  private void log(long tenantId,long id,String action,String from,String to,String comment,long operatorId){jdbc.update("INSERT INTO work_order_log(tenant_id,work_order_id,action,from_status,to_status,comment,operator_id) VALUES(?,?,?,?,?,?,?)",tenantId,id,action,from,to,comment,operatorId);}
  public record CreateRequest(@NotNull Long tenantId,@NotBlank String type,Long batteryId,String batteryCode,@NotBlank String title,String description,@NotBlank String priority,@NotNull Long reporterId,java.time.LocalDateTime dueAt){}
  public record AssignRequest(@NotNull Long tenantId,@NotNull Long assigneeId,@NotNull Long operatorId,String comment){}
  public record TransitionRequest(@NotNull Long tenantId,@NotBlank String toStatus,@NotNull Long operatorId,String comment){}
}
