package com.fundpilot.backend.market.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.Supplier;

/**
 * 东方财富行情数据源聚合(实现 {@link MarketDataSource}):组合 fund 域名的
 * {@link EastmoneyClient}(净值+字典)与 push2his 域名的 {@link EastmoneyKlineClient}(指数 K 线)。
 * <p>两域名不同,故拆两个 Feign client;本类把它们聚合为单一 {@code MarketDataSource},
 * 供 {@link MarketDataSourceChain} 降级链使用。
 * <p>K 线 {@code range} 参数(历史占位值 "6")不再使用,改用固定 lmt=400(约一年多交易日)。
 * <p>K 线拉取瞬时失败(push2his 偶发 "Unexpected end of file")时重试一次,避免直接降级净值走势。
 */
@Slf4j
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
        String raw = fetchKlineWithRetry(() -> eastmoneyKlineClient.fetchKlineRaw(indexCode, KLINE_LIMIT));
        return EastmoneyJsParser.parseIndexKline(raw);
    }

    @Override
    public IndexKline fetchIndexKlineWithPeriod(String indexCode, String klt, String lmt) {
        // 用参数化 klt 的重载,支持日/周/月 K 切换
        String raw = fetchKlineWithRetry(() -> eastmoneyKlineClient.fetchKlineRaw(indexCode, klt, lmt));
        return EastmoneyJsParser.parseIndexKline(raw);
    }

    /**
     * K 线原始响应拉取 + 重试一次。push2his 偶发连接中断("Unexpected end of file from server"),
     * Feign 默认 0 重试会直接抛 → 降级净值走势。重试一次覆盖瞬时抖动,仍失败则抛(交降级链)。
     */
    private String fetchKlineWithRetry(Supplier<String> fetch) {
        try {
            return fetch.get();
        } catch (RuntimeException first) {
            log.warn("东方财富 K 线拉取首次失败,重试一次: {}", first.getMessage());
            return fetch.get();
        }
    }
}
