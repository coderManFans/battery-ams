package me.coderman.batteryams.telemetry.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class TelemetryController {
  private final JdbcTemplate jdbc;
  public TelemetryController(JdbcTemplate jdbc){this.jdbc=jdbc;}

  @PostMapping("/readings") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Map<String,Object> ingest(@Valid @RequestBody ReadingRequest r){
    jdbc.update("INSERT INTO telemetry_reading(tenant_id,battery_code,reported_at,soc,soh,voltage,current_amp,temperature_c,cycle_count,source) VALUES(?,?,?,?,?,?,?,?,?,?)",r.tenantId(),r.batteryCode(),r.reportedAt(),r.soc(),r.soh(),r.voltage(),r.currentAmp(),r.temperatureC(),r.cycleCount(),r.source()==null?"SIMULATOR":r.source());
    Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class);
    createThresholdAlarms(r);
    return Map.of("data",Map.of("id",id==null?0:id,"batteryCode",r.batteryCode(),"reportedAt",r.reportedAt()));
  }

  @PostMapping("/readings/batch") @ResponseStatus(HttpStatus.CREATED) @Transactional
  public Map<String,Object> batch(@Valid @RequestBody BatchRequest r){
    if(r.readings()==null||r.readings().isEmpty()||r.readings().size()>500)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"批量数据条数必须为1-500");
    for(ReadingRequest item:r.readings()) ingest(r.withTenant(item));
    return Map.of("data",Map.of("accepted",r.readings().size()));
  }

  @GetMapping("/readings/latest")
  public Map<String,Object> latest(@RequestParam(defaultValue="1") long tenantId,@RequestParam(required=false) String batteryCode){
    if(batteryCode==null||batteryCode.isBlank())return Map.of("data",jdbc.queryForList("SELECT t.battery_code AS batteryCode,t.soc,t.soh,t.voltage,t.current_amp AS currentAmp,t.temperature_c AS temperatureC,t.cycle_count AS cycleCount,t.reported_at AS reportedAt FROM telemetry_reading t JOIN (SELECT battery_code,MAX(id) id FROM telemetry_reading WHERE tenant_id=? GROUP BY battery_code) x ON x.id=t.id WHERE t.tenant_id=? ORDER BY t.reported_at DESC LIMIT 500",tenantId,tenantId));
    var rows=jdbc.queryForList("SELECT battery_code AS batteryCode,soc,soh,voltage,current_amp AS currentAmp,temperature_c AS temperatureC,cycle_count AS cycleCount,reported_at AS reportedAt FROM telemetry_reading WHERE tenant_id=? AND battery_code=? ORDER BY reported_at DESC LIMIT 1",tenantId,batteryCode);
    return Map.of("data",rows);
  }

  @GetMapping("/readings/history")
  public Map<String,Object> history(@RequestParam(defaultValue="1") long tenantId,@RequestParam @NotBlank String batteryCode,@RequestParam(defaultValue="100") int limit){
    if(limit<1||limit>1000)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"limit必须为1-1000");
    return Map.of("data",jdbc.queryForList("SELECT soc,soh,voltage,current_amp AS currentAmp,temperature_c AS temperatureC,cycle_count AS cycleCount,reported_at AS reportedAt FROM telemetry_reading WHERE tenant_id=? AND battery_code=? ORDER BY reported_at DESC LIMIT ?",tenantId,batteryCode,limit));
  }

  @GetMapping("/alarms")
  public Map<String,Object> alarms(@RequestParam(defaultValue="1") long tenantId,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int size){
    if(page<1||size<1||size>200)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"分页参数无效");
    String where=" WHERE tenant_id=?"+(status==null||status.isBlank()?"":" AND status=?");Object[] args=status==null||status.isBlank()?new Object[]{tenantId}:new Object[]{tenantId,status};
    Long total=jdbc.queryForObject("SELECT COUNT(*) FROM telemetry_alarm"+where,args,Long.class);
    Object[] queryArgs=java.util.Arrays.copyOf(args,args.length+2);queryArgs[args.length]=size;queryArgs[args.length+1]=(page-1)*size;
    return Map.of("data",jdbc.queryForList("SELECT id,alarm_no AS alarmNo,battery_code AS batteryCode,alarm_type AS alarmType,severity,metric_value AS metricValue,threshold_value AS thresholdValue,description,status,reported_at AS reportedAt FROM telemetry_alarm"+where+" ORDER BY reported_at DESC LIMIT ? OFFSET ?",queryArgs),"page",page,"size",size,"total",total==null?0:total);
  }

  @PostMapping("/alarms/{id}/acknowledge") @Transactional
  public Map<String,Object> acknowledge(@PathVariable long id,@Valid @RequestBody AlarmAction r){
    int changed=jdbc.update("UPDATE telemetry_alarm SET status='ACKNOWLEDGED',acknowledged_by=?,acknowledged_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND id=? AND status='OPEN'",r.operatorId(),r.tenantId(),id);
    if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"告警不存在或已确认");
    return Map.of("data",Map.of("id",id,"status","ACKNOWLEDGED"));
  }

  @PostMapping("/alarms/{id}/resolve") @Transactional
  public Map<String,Object> resolve(@PathVariable long id,@Valid @RequestBody AlarmAction r){
    int changed=jdbc.update("UPDATE telemetry_alarm SET status='RESOLVED',resolved_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND id=? AND status='ACKNOWLEDGED'",r.tenantId(),id);
    if(changed!=1)throw new ResponseStatusException(HttpStatus.CONFLICT,"告警必须先确认再关闭");
    return Map.of("data",Map.of("id",id,"status","RESOLVED"));
  }

  private void createThresholdAlarms(ReadingRequest r){
    if(r.temperatureC()!=null&&r.temperatureC().compareTo(new BigDecimal("55"))>=0)alarm(r,"HIGH_TEMPERATURE","CRITICAL",r.temperatureC(),new BigDecimal("55"),"电池温度达到告警阈值");
    if(r.soc()!=null&&r.soc().compareTo(new BigDecimal("5"))<=0)alarm(r,"LOW_SOC","MEDIUM",r.soc(),new BigDecimal("5"),"电池SOC低于告警阈值");
    if(r.soh()!=null&&r.soh().compareTo(new BigDecimal("70"))<0)alarm(r,"LOW_SOH","HIGH",r.soh(),new BigDecimal("70"),"电池SOH低于健康阈值");
    if(r.voltage()!=null&&r.voltage().compareTo(new BigDecimal("40"))<0)alarm(r,"LOW_VOLTAGE","HIGH",r.voltage(),new BigDecimal("40"),"电池电压低于演示阈值");
  }
  private void alarm(ReadingRequest r,String type,String severity,BigDecimal value,BigDecimal threshold,String description){
    String no="AL"+UUID.randomUUID().toString().replace("-","").substring(0,18).toUpperCase();
    jdbc.update("INSERT INTO telemetry_alarm(tenant_id,alarm_no,battery_code,alarm_type,severity,metric_value,threshold_value,description,reported_at) VALUES(?,?,?,?,?,?,?,?,?)",r.tenantId(),no,r.batteryCode(),type,severity,value,threshold,description,r.reportedAt());
  }
  public record ReadingRequest(@NotNull Long tenantId,@NotBlank String batteryCode,@NotNull LocalDateTime reportedAt,@DecimalMin("0") @DecimalMax("100") BigDecimal soc,@DecimalMin("0") @DecimalMax("100") BigDecimal soh,BigDecimal voltage,BigDecimal currentAmp,BigDecimal temperatureC,Integer cycleCount,String source){}
  public record BatchRequest(@NotNull Long tenantId,List<@Valid ReadingRequest> readings){ReadingRequest withTenant(ReadingRequest r){return new ReadingRequest(tenantId,r.batteryCode(),r.reportedAt(),r.soc(),r.soh(),r.voltage(),r.currentAmp(),r.temperatureC(),r.cycleCount(),r.source());}}
  public record AlarmAction(@NotNull Long tenantId,@NotNull Long operatorId){}
}
