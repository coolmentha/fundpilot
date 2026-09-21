package com.fundpilot.backend.alerting.infrastructure.persistence.notification;

import com.fundpilot.backend.alerting.domain.notification.AlertNotification;
import com.fundpilot.backend.alerting.domain.notification.AlertNotificationRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class AlertNotificationRepositoryImpl implements AlertNotificationRepository {

    /** 与部分唯一索引 uq_alert_notification_rule_day_sent 的表达式保持一致，便于命中索引。 */
    private static final String UTC_DATE = "(trading_date AT TIME ZONE 'UTC')::date";

    private final AlertNotificationJpaRepository notifications;
    private final JdbcTemplate jdbc;

    @Override
    public AlertNotification save(AlertNotification notification) {
        return AlertNotificationPersistenceMapper.toDomain(
                notifications.save(AlertNotificationPersistenceMapper.toEntity(notification)));
    }

    @Override
    public int countSentByRuleAndTradingDate(long alertRuleId, Instant tradingDate) {
        Integer count = jdbc.queryForObject("""
                SELECT count(*) FROM alert_notification
                WHERE alert_rule_id = ? AND status = 'SENT' AND deleted_date IS NULL
                  AND %s = ?
                """.formatted(UTC_DATE), Integer.class, alertRuleId, utcDate(tradingDate));
        return count == null ? 0 : count;
    }

    @Override
    public List<AlertNotification> findLatestByOwnerId(long ownerId, int limit) {
        return notifications.findByOwnerIdOrderByIdDesc(ownerId, PageRequest.of(0, limit)).stream()
                .map(AlertNotificationPersistenceMapper::toDomain)
                .toList();
    }

    @Override
    public List<RuleDailyStatus> findDailyStatusByOwnerId(long ownerId, Instant tradingDate) {
        return jdbc.query("""
                SELECT alert_rule_id, count(*) AS sent_count, max(sent_at) AS last_sent_at
                FROM alert_notification
                WHERE owner_id = ? AND status = 'SENT' AND deleted_date IS NULL
                  AND %s = ?
                GROUP BY alert_rule_id
                """.formatted(UTC_DATE), (rs, row) -> new RuleDailyStatus(rs.getLong("alert_rule_id"),
                rs.getInt("sent_count"), instant(rs.getTimestamp("last_sent_at"))), ownerId, utcDate(tradingDate));
    }

    private static java.sql.Date utcDate(Instant dateLabel) {
        return java.sql.Date.valueOf(dateLabel.atZone(ZoneOffset.UTC).toLocalDate());
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
