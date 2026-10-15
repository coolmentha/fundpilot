package com.fundpilot.backend.marketdata.application.command.indicatorrefresh;

import com.fundpilot.backend.marketdata.application.command.indexkline.IndexKlineCommandHandler;
import com.fundpilot.backend.marketdata.application.command.indexvaluation.IndexValuationCommandHandler;
import com.fundpilot.backend.marketdata.application.command.navpublishing.NavPublishingCommandHandler;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.PublishedIndexKlineSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.PublishedIndexValuationSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.PublishedNavSourceGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway;
import com.fundpilot.backend.marketdata.application.query.indexkline.IndexKlineQueryHandler;
import com.fundpilot.backend.marketdata.application.query.indexvaluation.IndexValuationQueryHandler;
import com.fundpilot.backend.marketdata.domain.indexkline.IndexBar;
import com.fundpilot.backend.sharedkernel.time.ChinaTradingDate;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketIndicatorRefreshCommandHandler {
    private static final int TOTAL_BATCHES = 3;
    private static final String FULL_KLINE_LIMIT = "400";
    private static final String INCREMENTAL_KLINE_LIMIT = "10";
    // 数据库日期用 UTC 零点标签，北京时间 17:00 对应标签加 9 小时。
    private static final int CLOSING_REFRESH_UTC_HOUR = 9;

    private final TrackedNavProductGateway products;
    private final PublishedNavSourceGateway navSource;
    private final PublishedIndexKlineSourceGateway klineSource;
    private final NavPublishingCommandHandler navPublisher;
    private final IndexKlineCommandHandler klineCommands;
    private final IndexKlineQueryHandler klineQueries;
    private final Clock clock;
    private final PublishedIndexValuationSourceGateway valuationSource;
    private final IndexValuationCommandHandler valuationCommands;
    private final IndexValuationQueryHandler valuationQueries;

    public void refreshBatch(int batchNumber) {
        refresh(products.findAll().stream().filter(product ->
                Math.floorMod(Long.hashCode(product.fundProductId()), TOTAL_BATCHES) == batchNumber).toList());
    }

    public void refreshAll() {
        refresh(products.findAll());
    }

    /** 收盘补拉只写指数 K 线，不刷新净值、指标快照或发布信号事件。 */
    public void refreshClosingKlines(Instant tradeDate) {
        // ponytail: 数据源无终版标志，以 17:00 后的有效日 K 判定完成；源支持终版标志后再接入。
        Instant refreshedAfter = tradeDate.plus(CLOSING_REFRESH_UTC_HOUR, ChronoUnit.HOURS);
        if (clock.instant().isBefore(refreshedAfter)) return;
        Set<String> complete = klineQueries.completeCodesForDate(tradeDate, refreshedAfter);
        Set<String> existing = klineQueries.existingCodes();
        List<String> codes = products.findAll().stream()
                .map(TrackedNavProductGateway.TrackedProduct::benchmarkIndexCode)
                .filter(code -> code != null && !code.isBlank()).map(String::trim).distinct().toList();
        int updated = 0;
        int skipped = 0;
        int pending = 0;
        for (String code : codes) {
            if (complete.contains(code)) {
                skipped++;
                continue;
            }
            try {
                String limit = existing.contains(code) ? INCREMENTAL_KLINE_LIMIT : FULL_KLINE_LIMIT;
                var result = klineSource.fetch(toSecid(code), limit);
                List<IndexBar> bars = result == null || result.bars() == null ? List.of()
                        : result.bars().stream().filter(bar -> bar != null && bar.tradeDate() != null)
                        .map(bar -> new IndexBar(code, bar.tradeDate(), bar.open(), bar.high(), bar.low(),
                                bar.close(), bar.volume()))
                        .filter(IndexBar::isComplete).filter(bar -> !bar.tradeDate().isAfter(tradeDate)).toList();
                if (bars.stream().noneMatch(bar -> bar.tradeDate().equals(tradeDate))) {
                    pending++;
                    log.info("指数 {} 目标交易日 {} 完整 K 线尚未取得，等待下一轮补拉", code, tradeDate);
                    continue;
                }
                klineCommands.upsert(code, bars.stream().map(bar -> new IndexKlineCommandHandler.Bar(
                        bar.tradeDate(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume())).toList());
                updated++;
            } catch (RuntimeException ex) {
                pending++;
                log.warn("指数 {} 收盘 K 线补拉失败，等待下一轮补拉", code, ex);
            }
        }
        log.info("交易日 {} 收盘 K 线补拉完成: 更新 {}，已完整跳过 {}，待补拉 {}", tradeDate, updated, skipped, pending);
    }

    public void refreshOne(long legacyFundId) {
        products.findByLegacyFundId(legacyFundId).ifPresent(product ->
                refreshOne(product, new HashMap<>(), new HashSet<>(), new HashSet<>()));
    }

    public void refreshOneForPortfolioFund(long portfolioFundId) {
        var product = products.findByPortfolioFundId(portfolioFundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "组合基金不存在或已作废"));
        if (!supportsStandardNav(product)) {
            throw new BusinessException(ErrorCode.MARKET_DATA_ALL_SOURCES_FAILED,
                    "该产品不支持标准净值行情刷新");
        }
        refreshOne(product, new HashMap<>(), new HashSet<>(), new HashSet<>(), true);
    }

    public void refreshOne(RefreshTarget target) {
        refreshOne(new TrackedNavProductGateway.TrackedProduct(target.legacyFundId(), target.fundProductId(),
                target.fundCode(), target.fundName(), target.benchmarkIndexCode(), target.investmentTarget()),
                new HashMap<>(), new HashSet<>(), new HashSet<>());
    }

    private void refresh(List<TrackedNavProductGateway.TrackedProduct> targets) {
        Map<String, PublishedIndexKlineSourceGateway.IndexKline> klineCache = new HashMap<>();
        Set<String> persistedKlines = new HashSet<>();
        Set<String> refreshedValuations = new HashSet<>();
        int success = 0;
        int failure = 0;
        for (var target : targets) {
            try {
                if (supportsStandardNav(target)) {
                    refreshOne(target, klineCache, persistedKlines, refreshedValuations);
                    success++;
                }
            } catch (RuntimeException ex) {
                failure++;
                log.warn("拉取产品 {} 行情指标失败，跳过当日 snapshot", target.fundProductId(), ex);
            }
        }
        log.info("行情指标刷新完成: 成功 {} 只，失败 {} 只", success, failure);
    }

    private void refreshOne(TrackedNavProductGateway.TrackedProduct target,
                            Map<String, PublishedIndexKlineSourceGateway.IndexKline> klineCache,
                            Set<String> persistedKlines, Set<String> refreshedValuations) {
        refreshOne(target, klineCache, persistedKlines, refreshedValuations, false);
    }

    private void refreshOne(TrackedNavProductGateway.TrackedProduct target,
                            Map<String, PublishedIndexKlineSourceGateway.IndexKline> klineCache,
                            Set<String> persistedKlines, Set<String> refreshedValuations, boolean failOnPartialRefresh) {
        if (!supportsStandardNav(target)) return;
        List<PublishedNavSourceGateway.NavSnapshot> history = navSource.fetchHistory(target.fundCode());
        if (history == null || history.isEmpty()) {
            throw new IllegalStateException("fund_code=" + target.fundCode() + " 净值历史为空");
        }
        List<PublishedNavSourceGateway.NavSnapshot> ordered = history.stream()
                .sorted(Comparator.comparing(PublishedNavSourceGateway.NavSnapshot::navDate)).toList();
        String indexCode = target.benchmarkIndexCode();
        PublishedIndexKlineSourceGateway.IndexKline kline = null;
        if (indexCode != null && !indexCode.isBlank()) {
            try {
                String limit = klineQueries.exists(indexCode) ? INCREMENTAL_KLINE_LIMIT : FULL_KLINE_LIMIT;
                kline = klineCache.computeIfAbsent(indexCode,
                        key -> klineSource.fetch(toSecid(key), limit));
            } catch (RuntimeException ex) {
                if (failOnPartialRefresh) throw ex;
                log.warn("产品 {} 指数 K 线拉取失败，跳过本产品 K 线更新", target.fundProductId(), ex);
            }
        }
        navPublisher.publishNewer(target.legacyFundId(), target.fundProductId(), target.fundCode(), ordered.stream()
                .map(nav -> new NavPublishingCommandHandler.NavCandidate(nav.navDate(), nav.unitNav(), nav.accumulatedNav()))
                .toList());
        if (kline != null && persistedKlines.add(indexCode) && !kline.bars().isEmpty()) {
            klineCommands.upsert(indexCode, kline.bars().stream().map(bar ->
                    new IndexKlineCommandHandler.Bar(bar.tradeDate(), bar.open(), bar.high(), bar.low(),
                            bar.close(), bar.volume())).toList());
        }
        refreshValuation(indexCode, refreshedValuations, failOnPartialRefresh);
    }

    /** 估值请求在任何写事务外完成；失败只影响低估策略，不影响既有行情指标刷新。 */
    private void refreshValuation(String indexCode, Set<String> refreshedValuations, boolean failOnPartialRefresh) {
        if (indexCode == null || indexCode.isBlank()) return;
        if (!refreshedValuations.add(indexCode)) return;
        String source = "CSINDEX_INDEX_CSI_DS_PE_PEG";
        LocalDate end = clock.instant().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        LocalDate start = valuationQueries.latest(indexCode, source)
                .map(value -> value.tradeDate().atZone(java.time.ZoneOffset.UTC).toLocalDate().plusDays(1))
                .orElse(LocalDate.of(2000, 1, 1));
        if (start.isAfter(end)) return;
        try {
            var values = valuationSource.fetch(indexCode,
                    start.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                    end.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE));
            if (!values.isEmpty()) {
                valuationCommands.upsert(values.stream().map(value -> new IndexValuationCommandHandler.Input(indexCode,
                        value.tradeDate(), value.peRatio(), source)).toList());
            }
        } catch (RuntimeException ex) {
            if (failOnPartialRefresh) throw ex;
            log.warn("指数 {} PE 历史刷新失败，低估策略本期可能跳过", indexCode, ex);
        }
    }

    private static boolean supportsStandardNav(TrackedNavProductGateway.TrackedProduct product) {
        if (product.investmentTarget() == TrackedNavProductGateway.InvestmentTarget.MONEY_MARKET
                || product.investmentTarget() == TrackedNavProductGateway.InvestmentTarget.REIT) return false;
        String name = product.fundName();
        return name == null || (!name.contains("货币") && !name.toUpperCase(Locale.ROOT).contains("REIT")
                && !name.contains("不动产投资信托"));
    }

    private static String toSecid(String indexCode) {
        int dot = indexCode.indexOf('.');
        if (dot <= 0 || dot >= indexCode.length() - 1) return indexCode;
        String prefix = switch (indexCode.substring(dot + 1).toUpperCase(Locale.ROOT)) {
            case "SH" -> "1.";
            case "SZ" -> "0.";
            case "CSI" -> "2.";
            default -> null;
        };
        return prefix == null ? indexCode : prefix + indexCode.substring(0, dot);
    }

    public record RefreshTarget(Long legacyFundId, long fundProductId, String fundCode, String fundName,
                                String benchmarkIndexCode,
                                TrackedNavProductGateway.InvestmentTarget investmentTarget) {}
}
