package com.fundpilot.backend.alerting.application.grid;

import com.fundpilot.backend.alerting.domain.grid.GridParams;
import tools.jackson.databind.json.JsonMapper;

/**
 * 网格参数的 JSON 编解码，与 {@code TakeProfitParamsJsonCodec} 同构。
 *
 * <p>与条件快照一样放在应用层，领域层不感知序列化方式。反序列化会经过 {@link GridParams} 的
 * 紧凑构造器，因此库里存的非法比例会在读取时直接暴露，而不是被静默带入判定。
 */
public final class GridParamsJsonCodec {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private GridParamsJsonCodec() {
    }

    public static String write(GridParams params) {
        return params == null ? null : JSON.writeValueAsString(params);
    }

    /** 空串或 null 视为没有参数；解析失败由调用方按非法配置处理。 */
    public static GridParams read(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return JSON.readValue(json, GridParams.class);
    }
}
