package com.fundpilot.backend.productcatalog.domain.research;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class FundResearch {
    private final Long id;
    private final String fundCode;
    private Snapshot<Profile> profile;
    private Snapshot<Scale> scale;
    private Snapshot<Holdings> holdings;
    private Snapshot<Industry> industry;
    private Instant lastAttemptAt;

    private FundResearch(Long id, String fundCode, Snapshot<Profile> profile,
                         Snapshot<Scale> scale, Snapshot<Holdings> holdings,
                         Snapshot<Industry> industry, Instant lastAttemptAt) {
        this.id = id;
        this.fundCode = requireCode(fundCode);
        this.profile = profile;
        this.scale = scale;
        this.holdings = holdings;
        this.industry = industry;
        this.lastAttemptAt = lastAttemptAt;
    }

    public static FundResearch create(String fundCode) {
        return new FundResearch(null, fundCode, null, null, null, null, null);
    }

    public static FundResearch rehydrate(Long id, String fundCode, Snapshot<Profile> profile,
                                         Snapshot<Scale> scale, Snapshot<Holdings> holdings,
                                         Snapshot<Industry> industry, Instant lastAttemptAt) {
        return new FundResearch(Objects.requireNonNull(id), fundCode, profile, scale, holdings,
                industry, lastAttemptAt);
    }

    public void updateProfile(Profile data, Source source, Instant reportDate, Instant fetchedAt) {
        profile = Snapshot.success(data, source, reportDate, fetchedAt);
    }

    public void updateScale(Scale data, Source source, Instant reportDate, Instant fetchedAt) {
        scale = Snapshot.success(data, source, reportDate, fetchedAt);
    }

    public void updateHoldings(Holdings data, Source source, Instant reportDate, Instant fetchedAt) {
        holdings = Snapshot.success(data, source, reportDate, fetchedAt);
    }

    public void updateIndustry(Industry data, Source source, Instant reportDate, Instant fetchedAt) {
        industry = Snapshot.success(data, source, reportDate, fetchedAt);
    }

    public void markAttempt(Instant attemptedAt) {
        lastAttemptAt = Objects.requireNonNull(attemptedAt, "研究数据尝试时间不能为空");
    }

    public void failProfile(Source source) { profile = Snapshot.failed(profile, source); }
    public void failScale(Source source) { scale = Snapshot.failed(scale, source); }
    public void failHoldings(Source source) { holdings = Snapshot.failed(holdings, source); }
    public void failIndustry(Source source) { industry = Snapshot.failed(industry, source); }

    private static String requireCode(String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("基金代码不能为空");
        return code.trim();
    }

    public Long id() { return id; }
    public String fundCode() { return fundCode; }
    public Snapshot<Profile> profile() { return profile; }
    public Snapshot<Scale> scale() { return scale; }
    public Snapshot<Holdings> holdings() { return holdings; }
    public Snapshot<Industry> industry() { return industry; }
    public Instant lastAttemptAt() { return lastAttemptAt; }
    public String targetEtfCode() {
        return profile == null || profile.data() == null || profile.data().targetEtf() == null
                ? null : profile.data().targetEtf().code();
    }

    public enum FetchStatus { SUCCESS, FAILED }
    public enum ShareClass { A, C, OTHER }
    public enum HoldingKind { STOCK, TARGET_ETF, CASH, UNKNOWN }
    public enum AmountUnit { HUNDRED_MILLION_SHARES, CNY_100_MILLION }
    public enum LookThroughQuality { DIRECT, COMPLETE, TARGET_STALE, TARGET_FAILED, TARGET_WEIGHT_UNKNOWN }

    public record Source(String name, String url) {
        public Source { Objects.requireNonNull(name, "资料来源不能为空"); }
    }

    public record Snapshot<T>(T data, Source source, Instant reportDate,
                              Instant fetchedAt, FetchStatus status) {
        static <T> Snapshot<T> success(T data, Source source, Instant reportDate, Instant fetchedAt) {
            return new Snapshot<>(Objects.requireNonNull(data), Objects.requireNonNull(source), reportDate,
                    Objects.requireNonNull(fetchedAt), FetchStatus.SUCCESS);
        }

        static <T> Snapshot<T> failed(Snapshot<T> current, Source source) {
            if (current == null) return new Snapshot<>(null, source, null, null, FetchStatus.FAILED);
            return new Snapshot<>(current.data(), current.source(), current.reportDate(),
                    current.fetchedAt(), FetchStatus.FAILED);
        }
    }

    public record Reference(String code, String name) {}
    public record Profile(String fundCategory, ShareClass shareClass,
                          Reference trackingIndex, Reference targetEtf, Instant launchDate) {}
    public record DatedAmount(BigDecimal value, AmountUnit unit, Instant asOf) {}
    public record Scale(DatedAmount shareScale, DatedAmount categoryAssetScale,
                        DatedAmount combinedAssetScale) {}
    public record Holding(HoldingKind kind, String code, String name, BigDecimal weight) {}
    public record WeightedName(String name, BigDecimal weight) {}
    public record Holdings(List<Holding> stockHoldings, List<WeightedName> industryHoldings,
                           List<WeightedName> regionHoldings, List<WeightedName> currencyHoldings,
                           BigDecimal disclosedCoverage, boolean lookThrough,
                           Instant targetEtfReportDate, LookThroughQuality lookThroughQuality) {
        public Holdings {
            stockHoldings = stockHoldings == null ? List.of() : List.copyOf(stockHoldings);
            industryHoldings = copyNullable(industryHoldings);
            regionHoldings = copyNullable(regionHoldings);
            currencyHoldings = copyNullable(currencyHoldings);
            lookThroughQuality = lookThroughQuality == null ? LookThroughQuality.DIRECT : lookThroughQuality;
        }

        private static <T> List<T> copyNullable(List<T> values) {
            return values == null ? null : List.copyOf(values);
        }
    }
    public record Industry(List<WeightedName> holdings) {
        public Industry { holdings = holdings == null ? List.of() : List.copyOf(holdings); }
    }
}
