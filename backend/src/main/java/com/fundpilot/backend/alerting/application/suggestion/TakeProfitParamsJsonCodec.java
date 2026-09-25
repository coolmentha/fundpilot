package com.fundpilot.backend.alerting.application.suggestion;

import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitParams;
import tools.jackson.databind.json.JsonMapper;

/**
 * 建议型规则参数的 JSON 编解码：只有回撤止盈需要六个参数，通用规则与逻辑破坏止损为空。
 *
 * <p>与条件快照一样放在应用层，领域层不感知序列化方式。
 */
public final class TakeProfitParamsJsonCodec {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private TakeProfitParamsJsonCodec() {
    }

    public static String write(TakeProfitParams params) {
        return params == null ? null : JSON.writeValueAsString(params);
    }

    /** 空串或 null 视为没有参数；解析失败由调用方按非法配置处理。 */
    public static TakeProfitParams read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.readValue(json, TakeProfitParams.class);
    }
}