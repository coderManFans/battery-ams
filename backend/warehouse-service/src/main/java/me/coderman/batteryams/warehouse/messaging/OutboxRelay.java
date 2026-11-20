package me.coderman.batteryams.warehouse.messaging;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

/**
 * At-least-once Outbox relay. Consumers must deduplicate by eventId because a
 * broker send may succeed while the database acknowledgement fails.
 */
@Component
public class OutboxRelay {
  private final JdbcTemplate jdbc;
  private final RocketMQTemplate rocketMQTemplate;

  public OutboxRelay(JdbcTemplate jdbc,RocketMQTemplate rocketMQTemplate) {
    this.jdbc=jdbc;
    this.rocketMQTemplate=rocketMQTemplate;
  }

  @Scheduled(fixedDelayString = "${warehouse.outbox-relay.delay-ms:2000}")
  @Transactional
  public void publishPendingEvents() {
    List<Map<String,Object>> events=jdbc.queryForList(
      "SELECT id,event_id,topic,tag,payload,attempts FROM outbox_event " +
      "WHERE status='PENDING' AND next_attempt_at<=CURRENT_TIMESTAMP(3) " +
      "ORDER BY id LIMIT 50 FOR UPDATE SKIP LOCKED"
    );
    for(Map<String,Object> event:events) {
      long id=((Number)event.get("id")).longValue();
      int attempts=((Number)event.get("attempts")).intValue();
      String destination=event.get("topic")+":"+event.get("tag");
      try {
        rocketMQTemplate.convertAndSend(destination,event.get("payload"));
        jdbc.update("UPDATE outbox_event SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP(3),last_error=NULL WHERE id=? AND status='PENDING'",id);
      } catch(Exception ex) {
        String message=ex.getMessage()==null?ex.getClass().getSimpleName():ex.getMessage();
        if(message.length()>950) message=message.substring(0,950);
        jdbc.update("UPDATE outbox_event SET attempts=attempts+1,next_attempt_at=DATE_ADD(CURRENT_TIMESTAMP(3),INTERVAL LEAST(300,POW(2,LEAST(attempts,8))) SECOND),last_error=? WHERE id=? AND status='PENDING'",
          message,id);
      }
    }
  }
}
