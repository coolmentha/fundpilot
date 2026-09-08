package com.fundpilot.backend.productcatalog.application.query.research;

import com.fundpilot.backend.productcatalog.domain.research.FundResearch;
import com.fundpilot.backend.productcatalog.domain.research.FundResearchRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FundResearchQueryHandler {
    private static final Duration STALE_AFTER = Duration.ofDays(1);
    private final FundResearchRepository research;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<Result> find(String fundCode) {
        if (fundCode == null || fundCode.isBlank()) return Optional.empty();
        Instant now = clock.instant();
        return research.findByFundCode(fundCode.trim()).map(value -> new Result(value.fundCode(),
                snapshot(value.profile(), ProfileResult::from, now),
                snapshot(value.scale(), ScaleResult::from, now),
                snapshot(value.holdings(), HoldingsResult::from, now),
                snapshot(value.industry(), IndustryResult::from, now)));
    }

    public record Result(String fundCode, SnapshotResult<ProfileResult> profile,
                         SnapshotResult<ScaleResult> scale,
                         SnapshotResult<HoldingsResult> holdings,
                         SnapshotResult<IndustryResult> industry) {}
    public record SnapshotResult<T>(T data, SourceResult source, Instant reportDate,
                                    Instant fetchedAt, String status, boolean stale) {}
    public record SourceResult(String name, String url) {}
    public record ReferenceResult(String code, String name) {
        static ReferenceResult from(FundResearch.Reference value) {
            return value == null ? null : new ReferenceResult(value.code(), value.name());
        }
    }
    public record ProfileResult(String fundCategory, String shareClass,
                                ReferenceResult trackingIndex, ReferenceResult targetEtf,
                                Instant launchDate) {
        static ProfileResult from(FundResearch.Profile value) {
            return new ProfileResult(value.fundCategory(),
                    value.shareClass() == null ? null : value.shareClass().name(),
                    ReferenceResult.from(value.trackingIndex()), ReferenceResult.from(value.targetEtf()),
                    value.launchDate());
        }
    }
    public record DatedAmountResult(BigDecimal value, String unit, Instant asOf) {
        static DatedAmountResult from(FundResearch.DatedAmount value) {
            return value == null ? null : new DatedAmountResult(value.value(), value.unit().name(), value.asOf());
        }
    }
    public record ScaleResult(DatedAmountResult shareScale, DatedAmountResult categoryAssetScale,
                              DatedAmountResult combinedAssetScale) {
        static ScaleResult from(FundResearch.Scale value) {
            return new ScaleResult(DatedAmountResult.from(value.shareScale()),
                    DatedAmountResult.from(value.categoryAssetScale()),
                    DatedAmountResult.from(value.combinedAssetScale()));
        }
    }
    public record HoldingResult(String kind, String code, String name, BigDecimal weight) {
        static HoldingResult from(FundResearch.Holding value) {
            String kind = value.kind() == FundResearch.HoldingKind.TARGET_ETF
                    ? FundResearch.HoldingKind.STOCK.name() : value.kind().name();
            return new HoldingResult(kind, value.code(), value.name(), value.weight());
        }
    }
    public record WeightedNameResult(String name, BigDecimal weight) {
        static WeightedNameResult from(FundResearch.WeightedName value) {
            return new WeightedNameResult(value.name(), value.weight());
        }
    }
    public record HoldingsResult(List<HoldingResult> stockHoldings,
                                 List<WeightedNameResult> industryHoldings,
                                 List<WeightedNameResult> regionHoldings,
                                 List<WeightedNameResult> currencyHoldings,
                                 BigDecimal disclosedCoverage, boolean lookThrough,
                                 Instant targetEtfReportDate, String lookThroughQuality) {
        static HoldingsResult from(FundResearch.Holdings value) {
            return new HoldingsResult(value.stockHoldings().stream().map(HoldingResult::from).toList(),
                    mapNullable(value.industryHoldings()), mapNullable(value.regionHoldings()),
                    mapNullable(value.currencyHoldings()), value.disclosedCoverage(), value.lookThrough(),
                    value.targetEtfReportDate(), value.lookThroughQuality().name());
        }
    }
    public record IndustryResult(List<WeightedNameResult> holdings) {
        static IndustryResult from(FundResearch.Industry value) {
            return new IndustryResult(value.holdings().stream().map(WeightedNameResult::from).toList());
        }
    }

    private static <T, R> SnapshotResult<R> snapshot(FundResearch.Snapshot<T> value,
                                                      Function<T, R> mapper, Instant now) {
        if (value == null) return null;
        FundResearch.Source source = value.source();
        boolean stale = value.fetchedAt() != null
                && Duration.between(value.fetchedAt(), now).compareTo(STALE_AFTER) > 0;
        return new SnapshotResult<>(value.data() == null ? null : mapper.apply(value.data()),
                source == null ? null : new SourceResult(source.name(), source.url()), value.reportDate(),
                value.fetchedAt(), value.status().name(), stale);
    }

    private static List<WeightedNameResult> mapNullable(List<FundResearch.WeightedName> values) {
        return values == null ? null : values.stream().map(WeightedNameResult::from).toList();
    }
}
