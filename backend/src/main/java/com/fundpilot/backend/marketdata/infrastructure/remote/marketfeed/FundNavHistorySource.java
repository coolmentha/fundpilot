package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import java.util.List;

/** 提供已公布基金净值历史的外部数据能力。 */
public interface FundNavHistorySource {

    List<FundNavSnapshot> fetchNavHistory(String fundCode);
}
