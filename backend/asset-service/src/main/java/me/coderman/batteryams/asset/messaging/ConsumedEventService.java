package me.coderman.batteryams.asset.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.dao.DuplicateKeyException;
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
    jdbc.update("INSERT INTO consumed_event(event_id,tenant_id,event_type,aggregate_type,aggregate_id,payload) VALUES(?,?,?,?,?,?)",
      eventId, tenantId, eventType, aggregateType, aggregateId, rawPayload);
    // Domain projections are deliberately handled by event-specific handlers.
    // Persisting the inbox record and projection changes must share this transaction.
  }

  private String required(JsonNode event, String field) {
    String value = event.path(field).asText("");
    if (value.isBlank()) throw new IllegalArgumentException("Missing " + field + " in domain event");
    return value;
  }
}
