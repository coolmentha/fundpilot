package com.fundpilot.backend.market.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 东方财富行情数据源聚合(实现 {@link MarketDataSource}):组合 fund 域名的
 * {@link EastmoneyClient}(净值+字典)与 push2his 域名的 {@link EastmoneyKlineClient}(指数 K 线)。
 * <p>两域名不同,故拆两个 Feign client;本类把它们聚合为单一 {@code MarketDataSource},
 * 供 {@link MarketDataSourceChain} 降级链使用。
 * <p>K 线 {@code range} 参数(历史占位值 "6")不再使用,改用固定 lmt=400(约一年多交易日)。
 */
@Component
@RequiredArgsConstructor
public class EastmoneyMarketDataSource implements MarketDataSource {

    /** K 线返回条数(约一年多交易日,够算量能状态)。 */
    private static final String KLINE_LIMIT = "400";

    private final EastmoneyClient eastmoneyClient;
    private final EastmoneyKlineClient eastmoneyKlineClient;

    @Override
    public List<FundNavSnapshot> fetchNavHistory(String fundCode) {
        return eastmoneyClient.fetchNavHistory(fundCode);
    }

    @Override
    public List<FundDictEntry> fetchFundDict() {
        return eastmoneyClient.fetchFundDict();
    }

    @Override
    public IndexKline fetchIndexKline(String indexCode, String range) {
        // indexCode 是 secid 格式(如 "1.000300");range 不再用,固定 lmt
        String raw = eastmoneyKlineClient.fetchKlineRaw(indexCode, KLINE_LIMIT);
        return EastmoneyJsParser.parseIndexKline(raw);
    }
}
