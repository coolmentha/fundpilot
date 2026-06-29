package com.fundpilot.backend.market.client;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 行情数据源降级链(issue #7):按顺序尝试多个 {@link MarketDataSource},
 * 首个成功即返回;全部失败时抛 {@code BusinessException(MARKET_DATA_ALL_SOURCES_FAILED)},
 * 不允许 fallback 零值。
 * <p>降级语义:数据源 A 抛异常 → 尝试 B;B 也抛 → 抛业务异常。
 * 空结果(如某基金净值历史为空)不算"失败",由调用方决定如何处理。
 *
 * @param sources 数据源列表(按优先级排序,如 [东方财富, 同花顺])
 */
@Slf4j
public class MarketDataSourceChain implements MarketDataSource {

    private final List<MarketDataSource> sources;

    public MarketDataSourceChain(List<MarketDataSource> sources) {
        if (sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("至少需要一个数据源");
        }
        this.sources = List.copyOf(sources);
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

    /**
     * 按顺序尝试每个数据源;首个成功即返回,失败则记日志继续下一个。
     * 全部失败抛 {@link ErrorCode#MARKET_DATA_ALL_SOURCES_FAILED}。
     */
    private <T> T tryEach(String operation, String code, SourceFunction<T> fn) {
        List<Exception> failures = new ArrayList<>();
        for (int i = 0; i < sources.size(); i++) {
            MarketDataSource source = sources.get(i);
            try {
                return fn.apply(source);
            } catch (Exception ex) {
                log.warn("数据源[{}] {} 失败 code={}: {}", source.getClass().getSimpleName(), operation, code, ex.getMessage());
                failures.add(ex);
            }
        }
        throw new BusinessException(ErrorCode.MARKET_DATA_ALL_SOURCES_FAILED,
                "所有数据源均失败 " + operation + (code != null ? " code=" + code : "")
                        + " 失败数=" + failures.size());
    }

    @FunctionalInterface
    private interface SourceFunction<T> {
        T apply(MarketDataSource source) throws Exception;
    }
}
