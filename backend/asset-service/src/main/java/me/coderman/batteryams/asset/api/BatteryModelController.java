package me.coderman.batteryams.asset.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/models")
public class BatteryModelController {
  private final JdbcTemplate jdbc;
  public BatteryModelController(JdbcTemplate jdbc){this.jdbc=jdbc;}

  @GetMapping
  public Map<String,Object> list(@RequestParam(defaultValue="1") long tenantId){
    return Map.of("data",jdbc.queryForList("SELECT id,model_code AS modelCode,model_name AS modelName,manufacturer,chemistry,nominal_voltage AS nominalVoltage,nominal_capacity_ah AS nominalCapacityAh,rated_energy_wh AS ratedEnergyWh,cycle_life AS cycleLife,enabled FROM battery_model WHERE tenant_id=? ORDER BY id",tenantId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String,Object> create(@Valid @RequestBody CreateModelRequest r){
    if(r.nominalVoltage().signum()<=0||r.nominalCapacityAh().signum()<=0||r.ratedEnergyWh().signum()<=0||r.cycleLife()<=0)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"电池额定参数必须大于 0");
    try{
      jdbc.update("INSERT INTO battery_model(tenant_id,model_code,model_name,manufacturer,chemistry,nominal_voltage,nominal_capacity_ah,rated_energy_wh,cycle_life,enabled) VALUES(?,?,?,?,?,?,?,?,?,1)",
        r.tenantId(),r.modelCode(),r.modelName(),r.manufacturer(),r.chemistry(),r.nominalVoltage(),r.nominalCapacityAh(),r.ratedEnergyWh(),r.cycleLife());
    }catch(org.springframework.dao.DuplicateKeyException e){
      throw new ResponseStatusException(HttpStatus.CONFLICT,"该租户下电池型号编码已存在");
    }
    return Map.of("data",jdbc.queryForMap("SELECT id,model_code AS modelCode,model_name AS modelName FROM battery_model WHERE tenant_id=? AND model_code=?",r.tenantId(),r.modelCode()));
  }

  public record CreateModelRequest(@NotNull Long tenantId,@NotBlank String modelCode,@NotBlank String modelName,
    @NotBlank String manufacturer,@NotBlank String chemistry,@NotNull BigDecimal nominalVoltage,
    @NotNull BigDecimal nominalCapacityAh,@NotNull BigDecimal ratedEnergyWh,@NotNull Integer cycleLife){}
}
