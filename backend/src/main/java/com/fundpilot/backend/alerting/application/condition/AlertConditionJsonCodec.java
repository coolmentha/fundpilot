package com.fundpilot.backend.alerting.application.condition;

import com.fundpilot.backend.alerting.domain.condition.ConditionGroup;
import tools.jackson.databind.json.JsonMapper;

/**
 * 条件组的 JSON 编解码：规则配置与提醒记录的「条件快照」共用同一份格式。
 *
 * <p>放在应用层是为了让写规则的命令与两个持久化映射都能复用同一实现；领域层不感知序列化方式。
 */
public final class AlertConditionJsonCodec {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private AlertConditionJsonCodec() {
    }

    public static String write(ConditionGroup group) {
        return JSON.writeValueAsString(group);
    }

    public static ConditionGroup read(String json) {
        return JSON.readValue(json, ConditionGroup.class);
    }

    /** 空串或 null 视为没有条件：回撤止盈的判定由参数与状态机决定。 */
    public static ConditionGroup readOrNull(String json) {
        return json == null || json.isBlank() ? null : read(json);
    }
}