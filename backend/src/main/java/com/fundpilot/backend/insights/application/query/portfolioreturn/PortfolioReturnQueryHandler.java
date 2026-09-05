package com.fundpilot.backend.insights.application.query.portfolioreturn;

import com.fundpilot.backend.insights.application.gateway.portfolioreturn.ReturnCompositionGateway;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.Clock;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Insights 的组合累计收益同步组合查询。 */
@Service
@RequiredArgsConstructor
public class PortfolioReturnQueryHandler {
    private static final MathContext MATH = MathContext.DECIMAL64;
    private final ReturnCompositionGateway facts;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PortfolioReturnResult findByOwner(long ownerId) {
        return findByOwner(ownerId, null);
    }

    @Transactional(readOnly = true)
    public PortfolioReturnResult findByOwnerAt(long ownerId, Instant businessDate) {
        return findByOwner(ownerId, Objects.requireNonNull(businessDate));
    }

    private PortfolioReturnResult findByOwner(long ownerId, Instant businessDate) {
        Instant endExclusive = businessDate == null ? null : businessDate.plus(java.time.Duration.ofDays(1));
        List<ReturnCompositionGateway.PortfolioFund> funds = facts.findPortfolioFunds(ownerId).stream()
                .filter(ReturnCompositionGateway.PortfolioFund::tracked).toList();
        Map<Long, ReturnCompositionGateway.Position> positions = (businessDate == null
                ? facts.findPositions(ownerId) : facts.findPositionsAt(ownerId, endExclusive)).stream()
                .collect(Collectors.toMap(ReturnCompositionGateway.Position::portfolioFundId, Function.identity()));
        Map<Long, ReturnCompositionGateway.ReturnFact> returns = (businessDate == null
                ? facts.findReturnFacts(ownerId) : facts.findReturnFactsAt(ownerId, endExclusive)).stream()
                .collect(Collectors.toMap(ReturnCompositionGateway.ReturnFact::portfolioFundId, Function.identity()));
        Map<Long, ReturnCompositionGateway.Product> products = facts.findProducts(funds.stream()
                        .map(ReturnCompositionGateway.PortfolioFund::fundProductId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ReturnCompositionGateway.Product::id, Function.identity()));
        Map<Long, List<ReturnCompositionGateway.Nav>> navs = (businessDate == null
                ? facts.findLatestTwoNavs(products.keySet())
                : facts.findLatestTwoNavsAt(products.keySet(), businessDate)).stream()
                .collect(Collectors.groupingBy(ReturnCompositionGateway.Nav::fundProductId));
        Map<String, ReturnCompositionGateway.RealtimeValuation> valuations = (businessDate == null
                ? facts.findRealtimeValuations(products.values().stream().map(
                        ReturnCompositionGateway.Product::fundCode).collect(Collectors.toSet()))
                : List.<ReturnCompositionGateway.RealtimeValuation>of()).stream()
                .collect(Collectors.toMap(ReturnCompositionGateway.RealtimeValuation::fundCode, Function.identity()));
        Map<Long, List<FundGroup>> groups = facts.findGroupMemberships(ownerId).stream()
                .collect(Collectors.groupingBy(ReturnCompositionGateway.GroupMembership::portfolioFundId,
                        Collectors.mapping(value -> new FundGroup(value.groupId(), value.groupName()),
                                Collectors.toList())));
        Map<Long, String> classifications = facts.findDisciplineClassifications(ownerId,
                        funds.stream().map(ReturnCompositionGateway.PortfolioFund::id).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ReturnCompositionGateway.DisciplineClassification::portfolioFundId,
                        ReturnCompositionGateway.DisciplineClassification::category));
        List<FundReturnResult> rows = funds.stream().map(fund -> row(fund, positions.get(fund.id()),
                returns.get(fund.id()), products.get(fund.fundProductId()),
                navs.getOrDefault(fund.fundProductId(), List.of()),
                valuations.get(products.get(fund.fundProductId()) == null ? null
                        : products.get(fund.fundProductId()).fundCode()),
                groups.getOrDefault(fund.id(), List.of()), classifications.get(fund.id()),
                businessDate == null ? clock.instant() : businessDate)).toList();
        BigDecimal invested = rows.stream().map(FundReturnResult::externalInvestedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal redeemed = rows.stream().map(FundReturnResult::externalRedeemedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fees = rows.stream().map(FundReturnResult::feeAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean realizedComplete = rows.stream().allMatch(FundReturnResult::realizedComplete);
        boolean unrealizedComplete = rows.stream().filter(FundReturnResult::open)
                .allMatch(row -> row.unrealizedPnl() != null);
        // 任一持仓基金持仓市值未知(如估值拉取失败)时,合计保持未知,不得按已知子集拼凑
        boolean holdingComplete = rows.stream().filter(FundReturnResult::open)
                .allMatch(row -> row.holdingAmount() != null);
        BigDecimal realized = realizedComplete ? rows.stream().map(FundReturnResult::realizedPnl)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        BigDecimal unrealized = unrealizedComplete ? rows.stream().map(FundReturnResult::unrealizedPnl)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        BigDecimal holding = holdingComplete ? rows.stream().map(FundReturnResult::holdingAmount)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        BigDecimal totalReturn = holding == null ? null : holding.add(redeemed).subtract(invested);
        return new PortfolioReturnResult(invested, redeemed, fees, holding, realized, unrealized, totalReturn,
                totalReturn != null && invested.signum() > 0 ? totalReturn.divide(invested, MATH) : null,
                realizedComplete, rows);
    }

    @Transactional(readOnly = true)
    public List<FundReturnResult> currentFunds(long ownerId) {
        return findByOwner(ownerId).funds().stream()
                .filter(fund -> !"CLEARED".equals(fund.positionStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FundReturnResult> clearedFunds(long ownerId) {
        return findByOwner(ownerId).funds().stream()
                .filter(fund -> "CLEARED".equals(fund.positionStatus()))
                .toList();
    }

    @Transactional(readOnly = true)
    public FundReturnResult fund(long ownerId, long legacyFundId) {
        return findByOwner(ownerId).funds().stream()
                .filter(fund -> fund.legacyFundId() != null && fund.legacyFundId() == legacyFundId)
                .findFirst().orElse(null);
    }

    @Transactional(readOnly = true)
    public PortfolioSummaryResult summary(long ownerId) {
        List<FundReturnResult> funds = currentFunds(ownerId).stream().filter(FundReturnResult::open).toList();
        boolean holdingComplete = funds.stream().allMatch(fund -> fund.holdingAmount() != null);
        BigDecimal holding = holdingComplete ? funds.stream().map(FundReturnResult::holdingAmount)
                .filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add) : null;
        List<FundReturnResult> covered = funds.stream().filter(fund -> fund.dailyPnl() != null).toList();
        BigDecimal dailyPnl = covered.isEmpty() && !funds.isEmpty() ? null : covered.stream()
                .map(FundReturnResult::dailyPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dailyBase = covered.stream().filter(fund -> fund.dailyChangePct() != null
                        && BigDecimal.ONE.add(fund.dailyChangePct(), MATH).signum() != 0)
                .map(fund -> fund.holdingAmount().divide(BigDecimal.ONE.add(fund.dailyChangePct(), MATH), MATH))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPnl = funds.stream().anyMatch(fund -> fund.unrealizedPnl() == null) ? null
                : funds.stream().map(FundReturnResult::unrealizedPnl)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PortfolioSummaryResult(holding, dailyPnl,
                dailyBase.signum() == 0 || dailyPnl == null ? null : dailyPnl.divide(dailyBase, MATH), totalPnl,
                funds.size(), covered.size(), countPositive(funds, FundReturnResult::dailyChangePct),
                countNegative(funds, FundReturnResult::dailyChangePct),
                countPositive(funds, FundReturnResult::unrealizedPnl),
                countNegative(funds, FundReturnResult::unrealizedPnl),
                funds.stream().anyMatch(FundReturnResult::estimated),
                (int) funds.stream().filter(FundReturnResult::estimateFetchFailed).count());
    }

    private static FundReturnResult row(ReturnCompositionGateway.PortfolioFund fund,
                                        ReturnCompositionGateway.Position position,
                                        ReturnCompositionGateway.ReturnFact returns,
                                        ReturnCompositionGateway.Product product,
                                        List<ReturnCompositionGateway.Nav> navs,
                                        ReturnCompositionGateway.RealtimeValuation valuation,
                                        List<FundGroup> groups, String disciplineCategory, Instant now) {
        BigDecimal shares = position == null ? BigDecimal.ZERO : position.confirmedShares();
        boolean open = position != null && "OPEN".equals(position.status()) && shares.signum() > 0;
        ReturnCompositionGateway.Nav latest = navs.isEmpty() ? null : navs.getFirst();
        ReturnCompositionGateway.Nav previous = navs.size() < 2 ? null : navs.get(1);
        boolean qdii = product != null && "QDII".equals(product.investmentTarget());
        boolean supported = product == null || !("MONEY_MARKET".equals(product.investmentTarget())
                || "REIT".equals(product.investmentTarget()) || containsUnsupportedName(product.fundName()));
        Instant today = BusinessDay.toDateLabel(now);
        boolean todayNavConfirmed = latest != null && !latest.navDate().isBefore(today);
        boolean confirmedNavSelected = qdii
                ? latest != null && previous != null && isLatestNavFirstSeenToday(latest, now)
                : todayNavConfirmed;
        String estimateStatus = confirmedNavSelected ? "AVAILABLE" : qdii ? "STALE" : !supported ? "UNAVAILABLE"
                : valuation == null ? "NOT_ATTEMPTED" : valuation.status();
        BigDecimal dailyChange = confirmedNavSelected ? change(latest, previous)
                : !qdii && valuation != null && valuation.estimatedChangePct() != null
                ? valuation.estimatedChangePct()
                : "STALE".equals(estimateStatus) || "NOT_ATTEMPTED".equals(estimateStatus)
                ? BigDecimal.ZERO : null;
        boolean estimated = !confirmedNavSelected && !qdii && valuation != null
                && valuation.estimatedChangePct() != null;
        // 估值拉取失败(TIMEOUT/PARSE_ERROR)时持仓市值与总盈亏为未知,不得拿上一期已公布净值冒充当前值
        boolean estimateFailed = "TIMEOUT".equals(estimateStatus) || "PARSE_ERROR".equals(estimateStatus);
        BigDecimal positionNav = latest == null || estimateFailed ? null : estimated && dailyChange != null
                ? latest.unitNav().multiply(BigDecimal.ONE.add(dailyChange, MATH), MATH) : latest.unitNav();
        BigDecimal holding = !open ? BigDecimal.ZERO
                : positionNav == null ? null : shares.multiply(positionNav, MATH);
        BigDecimal unrealized = open && positionNav != null && position.costPerShare() != null
                ? holding.subtract(shares.multiply(position.costPerShare(), MATH)) : open ? null : BigDecimal.ZERO;
        BigDecimal dailyBase = estimated || !confirmedNavSelected ? latest == null ? null : latest.unitNav()
                : previous == null ? null : previous.unitNav();
        BigDecimal dailyPnl = open && dailyBase != null && dailyChange != null
                ? shares.multiply(dailyBase, MATH).multiply(dailyChange, MATH) : null;
        ReturnCompositionGateway.ReturnFact value = returns == null
                ? new ReturnCompositionGateway.ReturnFact(fund.id(), BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true) : returns;
        BigDecimal total = holding == null ? null
                : holding.add(value.redeemedAmount()).subtract(value.investedAmount());
        return new FundReturnResult(fund.id(), fund.legacyFundId(), product == null ? null : product.fundCode(),
                product == null ? null : product.fundName(), position == null ? "EMPTY" : position.status(),
                product == null ? null : product.productType(), product == null ? null : product.investmentTarget(),
                product == null ? null : product.benchmarkIndexCode(), disciplineCategory, fund.positionWarningEnabled(),
                fund.positionWarningRatio(),
                value.investedAmount(), value.redeemedAmount(), value.externalInvestedAmount(),
                value.externalRedeemedAmount(), value.feeAmount(), holding, value.realizedPnl(), unrealized, total,
                value.investedAmount().signum() > 0 && total != null ? total.divide(value.investedAmount(), MATH) : null,
                value.realizedComplete(), latest == null ? null : latest.navDate(), open, groups,
                shares.signum() == 0 ? null : shares, position == null ? null : position.costPerShare(),
                dailyChange, dailyPnl, estimated, "TIMEOUT".equals(estimateStatus)
                || "PARSE_ERROR".equals(estimateStatus), estimateStatus, positionNav,
                todayNavConfirmed ? "CONFIRMED_NAV" : estimated ? "INTRADAY_ESTIMATE"
                        : latest == null ? null : "LATEST_CONFIRMED_NAV",
                latest == null ? null : latest.firstSeenAt(), valuation == null ? null : valuation.estimateTime(),
                valuation == null ? null : valuation.baseNavDate(), position == null ? null : position.openedAt());
    }

    private static boolean containsUnsupportedName(String name) {
        if (name == null) return false;
        String normalized = name.toUpperCase(java.util.Locale.ROOT);
        return normalized.contains("货币") || normalized.contains("REIT") || normalized.contains("不动产投资信托");
    }

    /** QDII 只在最新净值首次被平台发现的北京时间当天结算一次收益。 */
    private static boolean isLatestNavFirstSeenToday(ReturnCompositionGateway.Nav latest, Instant now) {
        return latest.firstSeenAt() != null
                && BusinessDay.toDateLabel(latest.firstSeenAt()).equals(BusinessDay.toDateLabel(now));
    }

    private static BigDecimal change(ReturnCompositionGateway.Nav latest, ReturnCompositionGateway.Nav previous) {
        BigDecimal latestValue = latest == null ? null
                : latest.accumulatedNav() == null ? latest.unitNav() : latest.accumulatedNav();
        BigDecimal previousValue = previous == null ? null
                : previous.accumulatedNav() == null ? previous.unitNav() : previous.accumulatedNav();
        return latestValue == null || previousValue == null || previousValue.signum() == 0 ? null
                : latestValue.divide(previousValue, MATH).subtract(BigDecimal.ONE, MATH);
    }

    private static int countPositive(List<FundReturnResult> funds,
                                     Function<FundReturnResult, BigDecimal> value) {
        return (int) funds.stream().map(value).filter(Objects::nonNull).filter(number -> number.signum() > 0).count();
    }

    private static int countNegative(List<FundReturnResult> funds,
                                     Function<FundReturnResult, BigDecimal> value) {
        return (int) funds.stream().map(value).filter(Objects::nonNull).filter(number -> number.signum() < 0).count();
    }

    public record PortfolioReturnResult(BigDecimal investedAmount, BigDecimal redeemedAmount, BigDecimal feeAmount,
                                        BigDecimal holdingAmount, BigDecimal realizedPnl, BigDecimal unrealizedPnl,
                                        BigDecimal totalReturn, BigDecimal returnRate, boolean realizedComplete,
                                        List<FundReturnResult> funds) {}
    public record PortfolioSummaryResult(BigDecimal holdingAmountTotal, BigDecimal dailyPnlTotal,
                                         BigDecimal dailyChangePct, BigDecimal totalPnlTotal,
                                         int holdingFundCount, int dailyCoveredFundCount,
                                         int risingFundCount, int fallingFundCount,
                                         int profitableFundCount, int losingFundCount,
                                         boolean isEstimated, int estimateFetchFailedCount) {}
    public record FundReturnResult(long portfolioFundId, Long legacyFundId, String fundCode, String fundName,
                                   String positionStatus, String productType, String investmentTarget,
                                   String benchmarkIndexCode, String disciplineCategory,
                                   boolean positionWarningEnabled, BigDecimal positionWarningRatio,
                                   BigDecimal investedAmount, BigDecimal redeemedAmount,
                                   BigDecimal externalInvestedAmount, BigDecimal externalRedeemedAmount,
                                   BigDecimal feeAmount, BigDecimal holdingAmount, BigDecimal realizedPnl,
                                   BigDecimal unrealizedPnl, BigDecimal totalReturn, BigDecimal returnRate,
                                   boolean realizedComplete, Instant valuationDate, boolean open,
                                   List<FundGroup> groups, BigDecimal holdingShares, BigDecimal costPerShare,
                                   BigDecimal dailyChangePct, BigDecimal dailyPnl, boolean estimated,
                                   boolean estimateFetchFailed, String estimateStatus, BigDecimal valuationNav,
                                   String valuationSource, Instant valuationFirstSeenAt, String estimateTime,
                                   String baseNavDate, Instant openedAt) {}
    public record FundGroup(long id, String name) {}
}
