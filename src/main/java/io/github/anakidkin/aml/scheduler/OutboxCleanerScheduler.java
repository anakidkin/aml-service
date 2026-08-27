package io.github.anakidkin.aml.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class OutboxCleanerScheduler {

  private final JdbcTemplate jdbcTemplate;

  @Scheduled(cron = "0 0 * * * *")
  @Transactional
  public void cleanupProcessedEvents() {
    // check debezium delay - compare with 10MB
    Boolean isDebeziumCaughtUp = jdbcTemplate.queryForObject(
        """
            SELECT (pg_wal_lsn_diff(pg_current_wal_lsn(), confirmed_flush_lsn) < 10485760)
            FROM pg_replication_slots
            WHERE slot_name = 'debezium_slot'
            """,
        Boolean.class
    );

    if (Boolean.FALSE.equals(isDebeziumCaughtUp)) {
      log.warn("Debezium slot is laggy or inactive. Skipping outbox cleanup.");
      return;
    }

    int deletedRows = jdbcTemplate.update(
        "DELETE FROM outbox_events WHERE created_at < NOW() - INTERVAL '1 hour'"
    );

    log.info("Successfully cleaned up {} processed outbox events", deletedRows);
  }
}