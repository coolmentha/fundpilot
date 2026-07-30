package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import com.fundpilot.backend.platform.observability.MarketDataMetrics;

import java.util.ArrayList;
import java.util.List;

/**
 * 行情数据源降级链(issue #7):按顺序尝试多个 {@link MarketDataSource},
 * 首个成功即返回;全部失败时抛 {@code BusinessException(MARKET_DATA_ALL_SOURCES_FAILED)},
 * 不允许 fallback 零值。
 * <p>降级语义:数据源 A 抛异常 → 尝试 B;B 也抛 → 抛业务异常。
 * 空结果表示当前源无可用数据，继续尝试下一个数据源。
 *
 * @param sources 数据源列表(按优先级排序,如 [东方财富, 同花顺])
 */
@Slf4j
public class MarketDataSourceChain implements MarketDataSource {

    private final List<MarketDataSource> sources;
    private final MarketDataMetrics metrics;

    public MarketDataSourceChain(List<MarketDataSource> sources) {
        this(sources, null);
    }

    public MarketDataSourceChain(List<MarketDataSource> sources, MarketDataMetrics metrics) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个数据源");
        }
        this.sources = List.copyOf(sources);
        this.metrics = metrics;
    }

    @Override
    public List<FundNavSnapshot> fetchNavHistory(String fundCode) {
        return tryEach("fetchNavHistory", fundCode, source -> source.fetchNavHistory(fundCode));
    }

    @Override
    public List<FundDictEntry> fetchFundDict() {
        return tryEach("fetchFundDict", null, source -> source.fetchFundDict());
    }

    @Override
    public IndexKline fetchIndexKline(String indexCode, String range) {
        return tryEach("fetchIndexKline", indexCode, source -> source.fetchIndexKline(indexCode, range));
    }

    @Override
    public IndexKline fetchIndexKlineWithPeriod(String indexCode, String klt, String lmt) {
        // 必须 override:接口 default 会忽略 klt 降级为日K(恒 101),导致日/周/月 K 都一样。
        // 透传 klt 到各 source 的 fetchIndexKlineWithPeriod。
        return tryEach("fetchIndexKlineWithPeriod", indexCode,
                source -> source.fetchIndexKlineWithPeriod(indexCode, klt, lmt));
    }

    /**
     * 按顺序尝试每个数据源;首个成功即返回,失败则记日志继续下一个。
     * 全部失败抛 {@link ErrorCode#MARKET_DATA_ALL_SOURCES_FAILED}。
     */
    private <T> T tryEach(String operation, String code, SourceFunction<T> fn) {
        List<Exception> failures = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            MarketDataSource source = sources.get(i);
            String sourceName = source.getClass().getSimpleName();
            long startedAt = System.nanoTime();
            try {
                T result = fn.apply(source);
                if (isEmpty(result)) {
                    record(sourceName, operation, "empty", startedAt);
                    log.debug("数据源[{}] {} 返回空结果 code={},继续降级", source.getClass().getSimpleName(), operation, code);
                    continue;
                }
                record(sourceName, operation, "success", startedAt);
                return result;
            } catch (UnsupportedOperationException ex) {
                record(sourceName, operation, "unsupported", startedAt);
                // 该源不支持此操作(如 CsindexMarketDataSource 不做净值/字典)——非真实失败,
                // 静默跳过回退下一源,不记 warn 日志,避免链首专用源污染日志。
                continue;
            } catch (Exception ex) {
                record(sourceName, operation, metricResult(ex), startedAt);
                log.warn("数据源[{}] {} 失败 code={}: {}", source.getClass().getSimpleName(), operation, code, ex.getMessage());
                failures.add(ex);
            }
        }
        throw new BusinessException(ErrorCode.MARKET_DATA_ALL_SOURCES_FAILED,
                "所有数据源均失败 " + operation + (code != null ? " code=" + code : "")
                        + " 失败数=" + failures.size());
    }

    private void record(String source, String operation, String result, long startedAt) {
        if (metrics != null) {
            metrics.record(source, operation, result, startedAt);
        }
    }

    private static String metricResult(Exception ex) {
        if (ex instanceof feign.RetryableException) {
            return "timeout";
        }
        if (ex instanceof IllegalStateException) {
            return "parse_error";
        }
        return "failure";
    }

    private static boolean isEmpty(Object result) {
        if (result == null) {
            return true;
        }
        if (result instanceof java.util.Collection<?> collection) {
            return collection.isEmpty();
        }
        return result instanceof IndexKline kline && (kline.bars() == null || kline.bars().isEmpty());
    }

    @FunctionalInterface
    private interface SourceFunction<T> {
        T apply(MarketDataSource source) throws Exception;
    }
}
