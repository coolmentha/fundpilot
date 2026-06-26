package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.market.client.IndexKline;
import com.fundpilot.backend.market.client.MarketDataSource;
import com.fundpilot.backend.market.service.support.SecidFormat;
import com.fundpilot.backend.strategy.service.support.BenchmarkMetrics;
import com.fundpilot.backend.strategy.service.support.MaxDrawdownCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link Hs300BenchmarkProvider} 默认实现(issue #11):通过 {@link EastmoneyClient} 拉沪深300 指数日 K,
 * 按回测窗口过滤,算同期收益与最大回撤。
 *
 * <h3>缓存</h3>
 * 单用户场景回测不频繁,按窗口 {@code (start,end)} 缓存最近结果,避免 calibrate 重跑时重复请求东方财富。
 *
 * <h3>降级</h3>
 * 数据源拉取通过 {@link MarketDataSource} 降级链,全失败抛 {@code MARKET_DATA_ALL_SOURCES_FAILED};
 * 窗口内数据不足(<2 条)时返回零指标(收益 0、回撤 0),不阻断回测留痕。
 */
@Service
@RequiredArgsConstructor
public class DefaultHs300BenchmarkProvider implements Hs300BenchmarkProvider {

    private static final MathContext MATH = MathContext.DECIMAL64;
    /** 沪深300 指数代码(人类可读格式,经 {@link SecidFormat} 转 secid)。 */
    private static final String HS300_INDEX_CODE = "000300.SH";
    /** 东方财富 push2his range:6 = 近一年日 K(与 {@code MarketDataFetchService} 一致,issue #11 窗口为过去一年)。 */
    private static final String INDEX_KLINE_RANGE = "6";

    private final MarketDataSource marketDataSource;
    private final ConcurrentHashMap<String, BenchmarkMetrics> cache = new ConcurrentHashMap<>();

    @Override
    public BenchmarkMetrics fetch(Instant start, Instant end) {
        String cacheKey = start + "|" + end;
        return cache.computeIfAbsent(cacheKey, k -> doFetch(start, end));
    }

    private BenchmarkMetrics doFetch(Instant start, Instant end) {
        String secid = SecidFormat.fromIndexCode(HS300_INDEX_CODE).orElse(HS300_INDEX_CODE);
        IndexKline kline = marketDataSource.fetchIndexKline(secid, INDEX_KLINE_RANGE);
        if (kline == null || kline.bars() == null || kline.bars().size() < 2) {
            return zeroMetrics();
        }
        List<BigDecimal> closes = new ArrayList<>();
        for (IndexKline.Bar bar : kline.bars()) {
            if (bar.date() == null || bar.close() == null) {
                continue;
            }
            if (!bar.date().isBefore(start) && !bar.date().isAfter(end)) {
                closes.add(bar.close());
            }
        }
        if (closes.size() < 2) {
            return zeroMetrics();
        }
        BigDecimal first = closes.get(0);
        BigDecimal last = closes.get(closes.size() - 1);
        BigDecimal returnRate = last.divide(first, MATH).subtract(BigDecimal.ONE);
        BigDecimal maxDrawdown = MaxDrawdownCalculator.calculate(closes);
        return new BenchmarkMetrics(returnRate, maxDrawdown);
    }

    private static BenchmarkMetrics zeroMetrics() {
        return new BenchmarkMetrics(BigDecimal.ZERO, BigDecimal.ZERO);
    }
}
