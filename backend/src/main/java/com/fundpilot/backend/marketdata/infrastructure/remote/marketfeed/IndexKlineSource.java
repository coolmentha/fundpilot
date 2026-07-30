package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

/** 提供指数日线和指定周期 K 线的外部数据能力。 */
public interface IndexKlineSource {

    IndexKline fetchIndexKline(String indexCode, String range);

    default IndexKline fetchIndexKlineWithPeriod(String indexCode, String klt, String lmt) {
        return fetchIndexKline(indexCode, "6");
    }
}
