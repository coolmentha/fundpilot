package com.fundpilot.backend.alerting.domain.notification;

import java.time.Instant;
import java.util.List;

/** 提醒记录持久化端口。 */
public interface AlertNotificationRepository {

    AlertNotification save(AlertNotification notification);

    /** 该规则在指定交易日（UTC 日期截断）已成功发送的次数，用于当日幂等判断。 */
    int countSentByRuleAndTradingDate(long alertRuleId, Instant tradingDate);

    List<AlertNotification> findLatestByOwnerId(long ownerId, int limit);

    /** 该用户全部规则在指定交易日的发送概况，供规则列表展示「今日是否已提醒」。 */
    List<RuleDailyStatus> findDailyStatusByOwnerId(long ownerId, Instant tradingDate);

    record RuleDailyStatus(long alertRuleId, int sentCount, Instant lastSentAt) {
    }
}
