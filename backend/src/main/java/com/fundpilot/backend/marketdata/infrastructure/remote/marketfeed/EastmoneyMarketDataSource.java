package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 东方财富行情数据源聚合(实现 {@link MarketDataSource}):组合 fund 域名的
 * {@link EastmoneyClient}(净值+字典)与 push2his 域名的 {@link EastmoneyKlineClient}(指数 K 线)。
 * <p>两域名不同,故拆两个 Feign client;本类把它们聚合为单一 {@code MarketDataSource},
 * 供 {@link MarketDataSourceChain} 降级链使用。
 * <p>K 线 {@code range} 参数(历史占位值 "6")不再使用,改用固定 lmt=400(约一年多交易日)。
 * <p>K 线拉取瞬时失败(push2his 偶发 "Unexpected end of file")时重试一次,避免直接降级净值走势。
 */
@Component
@RequiredArgsConstructor
public class EastmoneyMarketDataSource implements MarketDataSource {

    private static final String LEGACY_KLINE_LIMIT = "400";

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
        String limit = "6".equals(range) ? LEGACY_KLINE_LIMIT : range;
        String raw = eastmoneyKlineClient.fetchKlineRaw(indexCode, limit);
        return EastmoneyJsParser.parseIndexKline(raw);
    }

    @Override
    public IndexKline fetchIndexKlineWithPeriod(String indexCode, String klt, String lmt) {
        // 用参数化 klt 的重载,支持日/周/月 K 切换
        String raw = eastmoneyKlineClient.fetchKlineRaw(indexCode, klt, lmt);
        return EastmoneyJsParser.parseIndexKline(raw);
    }
}
