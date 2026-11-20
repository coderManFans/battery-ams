package me.coderman.batteryams.asset.messaging;

import java.time.Duration;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Best-effort distributed mutex. The database unique key remains the final idempotency guard. */
@Component
public class RedisEventLock {
  private static final DefaultRedisScript<Long> UNLOCK = new DefaultRedisScript<>(
    "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
    Long.class);
  private final StringRedisTemplate redis;

  public RedisEventLock(StringRedisTemplate redis) { this.redis = redis; }

  public String tryAcquire(String eventId, Duration ttl) {
    String key = "battery-ams:consumer-lock:asset:" + eventId;
    String token = UUID.randomUUID().toString();
    Boolean acquired = redis.opsForValue().setIfAbsent(key, token, ttl);
    if (!Boolean.TRUE.equals(acquired)) return null;
    return key + "|" + token;
  }

  public void release(String lockHandle) {
    if (lockHandle == null) return;
    int separator = lockHandle.lastIndexOf('|');
    if (separator < 0) return;
    String key = lockHandle.substring(0, separator);
    String token = lockHandle.substring(separator + 1);
    redis.execute(UNLOCK, java.util.List.of(key), token);
  }
}
