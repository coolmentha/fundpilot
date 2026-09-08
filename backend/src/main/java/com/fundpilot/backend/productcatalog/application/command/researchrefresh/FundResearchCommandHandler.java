package com.fundpilot.backend.productcatalog.application.command.researchrefresh;

import com.fundpilot.backend.productcatalog.application.gateway.researchrefresh.FundResearchSourceGateway;
import com.fundpilot.backend.productcatalog.application.gateway.researchrefresh.FundResearchSourceGateway.SourceSnapshot;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Holding;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.HoldingKind;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Holdings;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.LookThroughQuality;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Source;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.WeightedName;
import com.fundpilot.backend.productcatalog.domain.research.FundResearchRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FundResearchCommandHandler {
    private static final Logger log = LoggerFactory.getLogger(FundResearchCommandHandler.class);
    private static final int DAILY_BATCH_SIZE = 100;
    private static final int DAILY_TARGET_ETF_LIMIT = 100;
    private static final Source EASTMONEY = new Source("东方财富", "https://fundf10.eastmoney.com");

    private final FundResearchRepository repository;
    private final FundResearchSourceGateway source;
    private final FundResearchWriter writer;
    private final Clock clock;

    public void refresh(String fundCode) {
        String code = normalize(fundCode);
        refreshOne(code, new LinkedHashSet<>(), new LinkedHashSet<>());
        writer.markAttempt(code, clock.instant());
    }

    public int refreshTrackedBatch() {
        List<String> codes = repository.findTrackedFundCodes(DAILY_BATCH_SIZE);
        Set<String> attemptedHoldings = new LinkedHashSet<>();
        Set<String> refreshedTargets = new LinkedHashSet<>();
        for (String code : codes) {
            try {
                refreshOne(code, attemptedHoldings, refreshedTargets);
            } catch (RuntimeException exception) {
                log.error("基金 {} 研究刷新写入失败，继续刷新其他基金", code, exception);
            } finally {
                try {
                    writer.markAttempt(code, clock.instant());
                } catch (RuntimeException exception) {
                    log.error("基金 {} 研究刷新尝试时间记录失败", code, exception);
                }
            }
        }
        return codes.size();
    }

    private void refreshOne(String code, Set<String> attemptedHoldings, Set<String> refreshedTargets) {
        FundResearch current = refreshProfile(code);
        refreshScale(code);
        refreshHoldings(code, current.targetEtfCode(), attemptedHoldings, refreshedTargets);
        refreshIndustry(code);
    }

    private FundResearch refreshProfile(String code) {
        SourceSnapshot<FundResearch.Profile> fetched;
        try {
            fetched = require(source.fetchProfile(code));
        } catch (RuntimeException exception) {
            log.warn("基金 {} 研究概况刷新失败，保留旧值", code, exception);
            return writer.failProfile(code, EASTMONEY);
        }
        return writer.writeProfile(code, fetched, clock.instant());
    }

    private void refreshScale(String code) {
        SourceSnapshot<FundResearch.Scale> fetched;
        try {
            fetched = require(source.fetchScale(code));
        } catch (RuntimeException exception) {
            log.warn("基金 {} 规模刷新失败，保留旧值", code, exception);
            writer.failScale(code, EASTMONEY);
            return;
        }
        writer.writeScale(code, fetched, clock.instant());
    }

    private void refreshHoldings(String code, String targetEtfCode, Set<String> attemptedHoldings,
                                 Set<String> refreshedTargets) {
        if (!attemptedHoldings.add(code)) return;
        SourceSnapshot<Holdings> fetched;
        try {
            fetched = require(source.fetchHoldings(code));
        } catch (RuntimeException exception) {
            log.warn("基金 {} 持仓刷新失败，保留旧值", code, exception);
            writer.failHoldings(code, EASTMONEY);
            return;
        }

        Holdings data = fetched.data();
        if (targetEtfCode != null && !targetEtfCode.equals(code)) {
            boolean hasSpecificWeight = data.stockHoldings().stream().anyMatch(item ->
                    item.kind() == HoldingKind.TARGET_ETF && targetEtfCode.equals(item.code()));
            data = hasSpecificWeight
                    ? lookThroughTarget(data, fetched.reportDate(), targetEtfCode,
                            attemptedHoldings, refreshedTargets)
                    : withQuality(data, LookThroughQuality.TARGET_WEIGHT_UNKNOWN);
        }
        writer.writeHoldings(code, data, fetched, clock.instant());
    }

    private Holdings lookThroughTarget(Holdings parent, Instant parentReportDate, String targetCode,
                                       Set<String> attemptedHoldings, Set<String> refreshedTargets) {
        boolean alreadyAttempted = attemptedHoldings.contains(targetCode);
        boolean withinLimit = alreadyAttempted || refreshedTargets.size() < DAILY_TARGET_ETF_LIMIT;
        if (!alreadyAttempted && withinLimit) {
            attemptedHoldings.add(targetCode);
            refreshedTargets.add(targetCode);
            refreshTargetHoldings(targetCode);
        }

        FundResearch.Snapshot<Holdings> target = repository.findByFundCode(targetCode)
                .map(FundResearch::holdings).orElse(null);
        if (target == null || target.data() == null) {
            return withQuality(parent, withinLimit
                    ? LookThroughQuality.TARGET_FAILED : LookThroughQuality.TARGET_STALE);
        }
        LookThroughQuality quality = target.status() == FundResearch.FetchStatus.FAILED
                ? LookThroughQuality.TARGET_FAILED
                : stale(parentReportDate, target.reportDate()) || !withinLimit
                ? LookThroughQuality.TARGET_STALE : LookThroughQuality.COMPLETE;
        return lookThrough(parent, targetCode, target.data(), target.reportDate(), quality);
    }

    private void refreshTargetHoldings(String targetCode) {
        SourceSnapshot<Holdings> fetched;
        try {
            fetched = require(source.fetchHoldings(targetCode));
        } catch (RuntimeException exception) {
            log.warn("目标 ETF {} 持仓刷新失败，尝试使用旧值", targetCode, exception);
            writer.failHoldings(targetCode, EASTMONEY);
            return;
        }
        writer.writeHoldings(targetCode, fetched.data(), fetched, clock.instant());
    }

    private void refreshIndustry(String code) {
        SourceSnapshot<FundResearch.Industry> fetched;
        try {
            fetched = require(source.fetchIndustry(code));
        } catch (RuntimeException exception) {
            log.warn("基金 {} 行业配置刷新失败，保留旧值", code, exception);
            writer.failIndustry(code, EASTMONEY);
            return;
        }
        writer.writeIndustry(code, fetched, clock.instant());
    }

    static Holdings lookThrough(Holdings parent, String targetCode, Holdings target,
                                Instant targetReportDate, LookThroughQuality quality) {
        BigDecimal targetWeight = parent.stockHoldings().stream()
                .filter(item -> item.kind() == HoldingKind.TARGET_ETF && targetCode.equals(item.code()))
                .map(Holding::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (targetWeight.signum() == 0) return withQuality(parent, LookThroughQuality.TARGET_WEIGHT_UNKNOWN);

        List<Holding> values = new ArrayList<>();
        parent.stockHoldings().stream().filter(item -> !(
                item.kind() == HoldingKind.TARGET_ETF && targetCode.equals(item.code()))).forEach(values::add);
        target.stockHoldings().forEach(item -> values.add(new Holding(
                item.kind() == HoldingKind.TARGET_ETF ? HoldingKind.STOCK : item.kind(),
                item.code(), item.name(), item.weight().multiply(targetWeight))));

        List<WeightedName> industries = combineWeighted(parent.industryHoldings(), target.industryHoldings(), targetWeight);
        List<WeightedName> regions = combineWeighted(parent.regionHoldings(), target.regionHoldings(), targetWeight);
        List<WeightedName> currencies = combineWeighted(parent.currencyHoldings(), target.currencyHoldings(), targetWeight);
        List<Holding> combined = combineHoldings(values);
        BigDecimal coverage = combined.stream().filter(item -> item.kind() != HoldingKind.UNKNOWN)
                .map(Holding::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Holdings(combined, industries, regions, currencies, coverage, true,
                targetReportDate, quality);
    }

    private static Holdings withQuality(Holdings value, LookThroughQuality quality) {
        return new Holdings(value.stockHoldings(), value.industryHoldings(), value.regionHoldings(),
                value.currencyHoldings(), value.disclosedCoverage(), value.lookThrough(),
                value.targetEtfReportDate(), quality);
    }

    private static List<Holding> combineHoldings(List<Holding> values) {
        Map<String, Holding> combined = new LinkedHashMap<>();
        values.forEach(item -> {
            String key = item.kind() + "|" + item.code() + "|" + item.name();
            combined.merge(key, item, (left, right) -> new Holding(left.kind(), left.code(), left.name(),
                    left.weight().add(right.weight())));
        });
        return List.copyOf(combined.values());
    }

    private static List<WeightedName> combineWeighted(List<WeightedName> parent,
                                                       List<WeightedName> target,
                                                       BigDecimal targetWeight) {
        if (parent == null && target == null) return null;
        Map<String, BigDecimal> combined = new LinkedHashMap<>();
        if (parent != null) parent.forEach(value -> combined.merge(value.name(), value.weight(), BigDecimal::add));
        if (target != null) target.forEach(value -> combined.merge(value.name(),
                value.weight().multiply(targetWeight), BigDecimal::add));
        return combined.entrySet().stream().map(entry -> new WeightedName(entry.getKey(), entry.getValue())).toList();
    }

    private static boolean stale(Instant parentReportDate, Instant targetReportDate) {
        return parentReportDate != null && (targetReportDate == null || targetReportDate.isBefore(parentReportDate));
    }

    private static <T> T require(T value) {
        if (value == null) throw new IllegalStateException("东方财富页面没有可用研究数据");
        return value;
    }

    private static String normalize(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("基金代码不能为空");
        return code.trim();
    }
}
