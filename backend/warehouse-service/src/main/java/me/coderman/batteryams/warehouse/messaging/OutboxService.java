package me.coderman.batteryams.warehouse.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.UUID;

@Service
public class OutboxService {
  private final JdbcTemplate jdbc;
  private final ObjectMapper mapper;
  public OutboxService(JdbcTemplate jdbc,ObjectMapper mapper) {
    this.jdbc=jdbc;
    this.mapper=mapper;
  }

  public void append(long tenantId,String aggregateType,String aggregateId,String eventType,String topic,String tag,Map<String,Object> data) {
    try {
      String eventId=UUID.randomUUID().toString();
      String payload=mapper.writeValueAsString(Map.of(
        "eventId",eventId,"eventType",eventType,"eventVersion",1,
        "occurredAt",java.time.Instant.now().toString(),
        "tenantId",tenantId,"aggregateType",aggregateType,"aggregateId",aggregateId,
        "data",data
      ));
      jdbc.update("INSERT INTO outbox_event(event_id,tenant_id,aggregate_type,aggregate_id,event_type,topic,tag,payload,status) VALUES(?,?,?,?,?,?,?,?,'PENDING')",
        eventId,tenantId,aggregateType,aggregateId,eventType,topic,tag,payload);
    } catch(JsonProcessingException e) {
      throw new IllegalStateException("Cannot serialize outbox event",e);
    }
  }
}
