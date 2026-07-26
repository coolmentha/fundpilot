package com.fundpilot.backend.market.client;

import java.util.List;

/** 提供全量基金产品目录的外部数据能力。 */
public interface FundCatalogSource {

    List<FundDictEntry> fetchFundDict();
}
