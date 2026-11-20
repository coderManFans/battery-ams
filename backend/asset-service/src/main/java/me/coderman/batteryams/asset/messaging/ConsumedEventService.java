package me.coderman.batteryams.asset.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ConsumedEventService {
  private final JdbcTemplate jdbc;
  public ConsumedEventService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Transactional
  public void record(JsonNode event, String rawPayload) {
    String eventId = required(event, "eventId");
    long tenantId = event.path("tenantId").asLong(-1);
    if (tenantId <= 0) throw new IllegalArgumentException("Invalid tenantId in domain event");
    String eventType = required(event, "eventType");
    String aggregateType = required(event, "aggregateType");
    String aggregateId = required(event, "aggregateId");

    // Insert inbox first. Its unique key is the final defense against duplicate delivery.
    jdbc.update("INSERT INTO consumed_event(event_id,tenant_id,event_type,aggregate_type,aggregate_id,payload) VALUES(?,?,?,?,?,?)",
      eventId, tenantId, eventType, aggregateType, aggregateId, rawPayload);

    JsonNode data = event.path("data");
    switch (eventType) {
      case "warehouse.inbound.completed" -> applyInbound(tenantId, data);
      case "warehouse.transfer.completed" -> applyTransfer(tenantId, data);
      // Dispatch destination is not yet modeled by the warehouse command, so do not
      // invent a station/location. The event is recorded until that contract exists.
      case "warehouse.outbound.completed",
           "warehouse.inventory.reserved",
           "warehouse.inventory.reservation.released",
           "warehouse.inventory.adjusted" -> { /* inbox-only until asset projection semantics are defined */ }
      default -> throw new IllegalArgumentException("Unsupported warehouse eventType: " + eventType);
    }
  }

  private void applyInbound(long tenantId, JsonNode data) {
    long warehouseId = requiredLong(data, "warehouseId");
    JsonNode codes = data.path("batteryCodes");
    if (!codes.isArray() || codes.isEmpty()) throw new IllegalArgumentException("Inbound event batteryCodes must not be empty");
    for (JsonNode codeNode : codes) {
      String code = codeNode.asText("");
      if (code.isBlank()) throw new IllegalArgumentException("Inbound event contains blank batteryCode");
      int changed = jdbc.update(
        "UPDATE battery_asset SET current_location_type='WAREHOUSE',current_location_id=?,lifecycle_status=CASE WHEN lifecycle_status='REGISTERED' THEN 'AVAILABLE' ELSE lifecycle_status END,version=version+1,updated_at=CURRENT_TIMESTAMP(3) " +
        "WHERE tenant_id=? AND battery_code=? AND lifecycle_status IN ('REGISTERED','AVAILABLE')",
        warehouseId, tenantId, code);
      if (changed != 1) throw new IllegalStateException("Inbound event cannot reconcile asset: " + code);
    }
  }

  private void applyTransfer(long tenantId, JsonNode data) {
    long toWarehouseId = requiredLong(data, "toWarehouseId");
    JsonNode codes = data.path("batteryCodes");
    if (!codes.isArray() || codes.isEmpty()) throw new IllegalArgumentException("Transfer event batteryCodes must not be empty");
    for (JsonNode codeNode : codes) {
      String code = codeNode.asText("");
      if (code.isBlank()) throw new IllegalArgumentException("Transfer event contains blank batteryCode");
      int changed = jdbc.update(
        "UPDATE battery_asset SET current_location_type='WAREHOUSE',current_location_id=?,version=version+1,updated_at=CURRENT_TIMESTAMP(3) " +
        "WHERE tenant_id=? AND battery_code=? AND lifecycle_status='AVAILABLE'",
        toWarehouseId, tenantId, code);
      if (changed != 1) throw new IllegalStateException("Transfer event cannot reconcile asset: " + code);
    }
  }

  private long requiredLong(JsonNode node, String field) {
    long value = node.path(field).asLong(-1);
    if (value <= 0) throw new IllegalArgumentException("Missing or invalid " + field + " in domain event");
    return value;
  }

  private String required(JsonNode event, String field) {
    String value = event.path(field).asText("");
    if (value.isBlank()) throw new IllegalArgumentException("Missing " + field + " in domain event");
    return value;
  }
}
