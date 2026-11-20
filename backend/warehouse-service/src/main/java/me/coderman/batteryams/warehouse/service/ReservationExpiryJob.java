package me.coderman.batteryams.warehouse.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Map;

/**
 * Releases expired reservations in bounded batches. Reservation rows are locked
 * before inventory rows so expiry cannot race a dispatch/release operation.
 */
@Component
public class ReservationExpiryJob {
  private final JdbcTemplate jdbc;
  public ReservationExpiryJob(JdbcTemplate jdbc) { this.jdbc = jdbc; }

  @Scheduled(fixedDelayString = "${warehouse.reservation-expiry.delay-ms:30000}")
  @Transactional
  public void releaseExpiredReservations() {
    List<Map<String,Object>> expired = jdbc.queryForList(
      "SELECT id,tenant_id FROM inventory_reservation WHERE status='ACTIVE' AND expires_at<=CURRENT_TIMESTAMP(3) ORDER BY expires_at LIMIT 100 FOR UPDATE"
    );
    for (Map<String,Object> reservation : expired) {
      long reservationId = ((Number) reservation.get("id")).longValue();
      long tenantId = ((Number) reservation.get("tenant_id")).longValue();
      jdbc.update(
        "UPDATE inventory_item i JOIN inventory_reservation_item ri ON ri.inventory_item_id=i.id AND ri.tenant_id=i.tenant_id " +
        "SET i.status='AVAILABLE',i.updated_at=CURRENT_TIMESTAMP(3) " +
        "WHERE ri.tenant_id=? AND ri.reservation_id=? AND ri.status='RESERVED' AND i.status='RESERVED'",
        tenantId,reservationId
      );
      jdbc.update("UPDATE inventory_reservation_item SET status='EXPIRED' WHERE tenant_id=? AND reservation_id=? AND status='RESERVED'",
        tenantId,reservationId);
      jdbc.update("UPDATE inventory_reservation SET status='EXPIRED' WHERE tenant_id=? AND id=? AND status='ACTIVE'",
        tenantId,reservationId);
    }
  }
}
