package me.coderman.batteryams.asset.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

@Component
@RocketMQMessageListener(
  topic = "${battery.messaging.warehouse-topic:battery-warehouse-events}",
  consumerGroup = "${rocketmq.consumer.group:battery-asset-warehouse-consumer}",
  selectorExpression = "*")
public class WarehouseEventConsumer implements RocketMQListener<String> {
  private static final Duration LOCK_TTL = Duration.ofSeconds(60);
  private final ObjectMapper mapper;
  private final RedisEventLock lock;
  private final ConsumedEventService consumedEvents;

  public WarehouseEventConsumer(ObjectMapper mapper, RedisEventLock lock, ConsumedEventService consumedEvents) {
    this.mapper = mapper;
    this.lock = lock;
    this.consumedEvents = consumedEvents;
  }

  @Override
  public void onMessage(String payload) {
    final JsonNode event;
    try {
      event = mapper.readTree(payload);
    } catch (Exception ex) {
      throw new IllegalArgumentException("Invalid warehouse event JSON", ex);
    }
    String eventId = event.path("eventId").asText("");
    if (eventId.isBlank()) throw new IllegalArgumentException("Warehouse event missing eventId");

    // Redis suppresses concurrent duplicate work; MySQL UNIQUE(event_id) is authoritative.
    String lockHandle = lock.tryAcquire(eventId, LOCK_TTL);
    if (lockHandle == null) {
      throw new IllegalStateException("Event is being processed by another consumer: " + eventId);
    }
    try {
      try {
        consumedEvents.record(event, payload);
      } catch (DuplicateKeyException duplicate) {
        // Already committed by a previous delivery: acknowledge this duplicate.
      }
    } finally {
      lock.release(lockHandle);
    }
  }
}
