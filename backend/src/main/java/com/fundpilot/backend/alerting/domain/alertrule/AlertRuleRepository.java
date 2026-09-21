package com.fundpilot.backend.alerting.domain.alertrule;

import java.util.List;
import java.util.Optional;

/** 提醒规则持久化端口。 */
public interface AlertRuleRepository {

    Optional<AlertRule> findById(long id);

    List<AlertRule> findByOwnerId(long ownerId);

    /** 全部启用中的规则，供定时评估使用。 */
    List<AlertRule> findAllEnabled();

    AlertRule save(AlertRule rule);

    void softDelete(long id);
}
