package me.coderman.batteryams.asset.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ConsumedEventServiceTest {
  private JdbcTemplate jdbc;
  private ConsumedEventService service;
  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    jdbc = mock(JdbcTemplate.class);
    mapper = new ObjectMapper();
    service = new ConsumedEventService(jdbc);
  }

  @Test
  void inboundEventUpdatesAssetWarehouseAndActivatesRegisteredAsset() throws Exception {
    var event = mapper.readTree("""
      {"eventId":"evt-1","tenantId":1,"eventType":"warehouse.inbound.completed",
       "aggregateType":"INBOUND_ORDER","aggregateId":"IN-1",
       "data":{"warehouseId":12,"batteryCodes":["BAT-1"]}}
      """);

    service.record(event, event.toString());

    verify(jdbc).update(contains("INSERT INTO consumed_event"), eq("evt-1"), eq(1L),
      eq("warehouse.inbound.completed"), eq("INBOUND_ORDER"), eq("IN-1"), anyString());
    verify(jdbc).update(contains("UPDATE battery_asset"), eq(12L), eq(1L), eq("BAT-1"));
  }

  @Test
  void transferEventUpdatesAssetWarehouseLocation() throws Exception {
    var event = mapper.readTree("""
      {"eventId":"evt-2","tenantId":1,"eventType":"warehouse.transfer.completed",
       "aggregateType":"TRANSFER_ORDER","aggregateId":"TR-1",
       "data":{"fromWarehouseId":10,"toWarehouseId":12,"batteryCodes":["BAT-2"]}}
      """);

    service.record(event, event.toString());

    verify(jdbc).update(contains("UPDATE battery_asset"), eq(12L), eq(1L), eq("BAT-2"));
  }

  @Test
  void unsupportedEventFailsSoRocketMqCanRetryOrRouteToDlq() throws Exception {
    var event = mapper.readTree("""
      {"eventId":"evt-3","tenantId":1,"eventType":"warehouse.unknown",
       "aggregateType":"UNKNOWN","aggregateId":"X","data":{}}
      """);

    org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
      () -> service.record(event, event.toString()));
  }
}
