package me.coderman.batteryams.asset.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import me.coderman.batteryams.asset.domain.BatteryStatus;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;

@RestController @RequestMapping("/api/v1/assets")
public class AssetController {
  private final JdbcTemplate jdbc;
  public AssetController(JdbcTemplate jdbc){this.jdbc=jdbc;}
  @GetMapping public Map<String,Object> list(){return Map.of("data",jdbc.queryForList("SELECT id,tenant_id AS tenantId,battery_code AS batteryCode,model_code AS modelCode,lifecycle_status AS status,health_status AS healthStatus,current_location_type AS locationType,current_location_id AS locationId,soc,soh,version,created_at AS createdAt FROM battery_asset ORDER BY id DESC LIMIT 200"));}
  @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@Valid @RequestBody CreateAssetRequest r){try{if(r.soc()<0||r.soc()>100||r.soh()<0||r.soh()>100)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"SOC/SOH 必须在 0 到 100 之间");jdbc.update("INSERT INTO battery_asset(tenant_id,battery_code,serial_no,model_code,owner_org_id,lifecycle_status,health_status,soc,soh,version) VALUES(?,?,?,?,?,'REGISTERED','NORMAL',?,?,0)",r.tenantId(),r.batteryCode(),r.serialNo(),r.modelCode(),r.ownerOrgId(),r.soc(),r.soh());}catch(org.springframework.dao.DuplicateKeyException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"电池编码或序列号已存在");}return Map.of("data",jdbc.queryForMap("SELECT id,battery_code AS batteryCode,lifecycle_status AS status FROM battery_asset WHERE tenant_id=? AND battery_code=?",r.tenantId(),r.batteryCode()));}
  @Transactional
  @PostMapping("/{id}/transitions") public Map<String,Object> transition(@PathVariable long id,@Valid @RequestBody TransitionRequest r){return jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<Map<String,Object>>) c->{try(var ps=c.prepareStatement("SELECT tenant_id,lifecycle_status,version FROM battery_asset WHERE id=? FOR UPDATE")){ps.setLong(1,id);try(var rs=ps.executeQuery()){if(!rs.next())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"电池资产不存在");String from=rs.getString("lifecycle_status");long version=rs.getLong("version");if(!allowed(from,r.toStatus().name()))throw new ResponseStatusException(HttpStatus.CONFLICT,"非法状态流转: "+from+" -> "+r.toStatus());try(var up=c.prepareStatement("UPDATE battery_asset SET lifecycle_status=?,version=version+1 WHERE id=? AND version=?")){up.setString(1,r.toStatus().name());up.setLong(2,id);up.setLong(3,version);up.executeUpdate();}try(var h=c.prepareStatement("INSERT INTO battery_status_history(battery_id,from_status,to_status,reason,operator_id) VALUES(?,?,?,?,?)")){h.setLong(1,id);h.setString(2,from);h.setString(3,r.toStatus().name());h.setString(4,r.reason());h.setLong(5,r.operatorId());h.executeUpdate();}return Map.of("data",Map.of("id",id,"fromStatus",from,"toStatus",r.toStatus().name()));}}}});}
  private boolean allowed(String f,String t){return switch(f){case "REGISTERED"->t.equals("AVAILABLE");case "AVAILABLE"->List.of("IN_SERVICE","FROZEN","UNDER_REPAIR","RETIRED").contains(t);case "IN_SERVICE"->List.of("FROZEN","UNDER_REPAIR").contains(t);case "FROZEN"->List.of("AVAILABLE","UNDER_REPAIR","RETIRED").contains(t);case "UNDER_REPAIR"->List.of("AVAILABLE","RETIRED").contains(t);default->false;};}
  public record CreateAssetRequest(@NotNull Long tenantId,@NotBlank String batteryCode,@NotBlank String serialNo,@NotBlank String modelCode,@NotNull Long ownerOrgId,@NotNull Integer soc,@NotNull Double soh){}
  public record TransitionRequest(@NotNull BatteryStatus toStatus,@NotBlank String reason,@NotNull Long operatorId){}
}
