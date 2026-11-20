package me.coderman.batteryams.asset.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WarehouseEventConsumerTest {
  private RedisEventLock lock;
  private ConsumedEventService consumed;
  private WarehouseEventConsumer consumer;

  @BeforeEach
  void setUp() {
    lock = mock(RedisEventLock.class);
    consumed = mock(ConsumedEventService.class);
    consumer = new WarehouseEventConsumer(new ObjectMapper(), lock, consumed);
  }

  @Test
  void successfulDeliveryRecordsEventAndReleasesRedisLock() {
    String payload = """
      {"eventId":"evt-100","tenantId":1,"eventType":"warehouse.inbound.completed",
       "aggregateType":"INBOUND_ORDER","aggregateId":"IN-100","data":{"warehouseId":2,"batteryCodes":["BAT-1"]}}
      """;
    when(lock.tryAcquire(eq("evt-100"), any())).thenReturn("redis-key|token");

    consumer.onMessage(payload);

    verify(consumed).record(any(), eq(payload));
    verify(lock).release("redis-key|token");
  }

  @Test
  void duplicateDatabaseEventIsAcknowledgedAndLockReleased() {
    String payload = """
      {"eventId":"evt-101","tenantId":1,"eventType":"warehouse.inbound.completed",
       "aggregateType":"INBOUND_ORDER","aggregateId":"IN-101","data":{"warehouseId":2,"batteryCodes":["BAT-1"]}}
      """;
    when(lock.tryAcquire(eq("evt-101"), any())).thenReturn("redis-key|token");
    doThrow(new DuplicateKeyException("duplicate")).when(consumed).record(any(), eq(payload));

    assertDoesNotThrow(() -> consumer.onMessage(payload));
    verify(lock).release("redis-key|token");
  }

  @Test
  void lockContentionFailsDeliverySoBrokerCanRetry() {
    String payload = """
      {"eventId":"evt-102","tenantId":1,"eventType":"warehouse.inbound.completed",
       "aggregateType":"INBOUND_ORDER","aggregateId":"IN-102","data":{"warehouseId":2,"batteryCodes":["BAT-1"]}}
      """;
    when(lock.tryAcquire(eq("evt-102"), any())).thenReturn(null);

    assertThrows(IllegalStateException.class, () -> consumer.onMessage(payload));
    verifyNoInteractions(consumed);
    verify(lock, never()).release(anyString());
  }

  @Test
  void malformedPayloadFailsDelivery() {
    assertThrows(IllegalArgumentException.class, () -> consumer.onMessage("{"));
    verifyNoInteractions(consumed);
  }
}
