package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.enums.InvestmentTarget;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundEstimateSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation.EstimateStatus;
import com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation.MarketRealtimeCache;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FundPnlServiceDateTest {

    @Mock FundPositionService fundPositionService;
    @Mock FundNavHistoryRepository fundNavHistoryRepository;
    @Mock FundRepository fundRepository;
    @Mock FundTransactionRepository fundTransactionRepository;
    @Mock MarketRealtimeCache marketRealtimeCache;
    @Mock CurrentActorApi currentUserService;

    @Test
    void QDII按北京时间首次发现当天使用最新确认净值收益() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-21T00:30:00Z"), ZoneOffset.UTC);
        FundPnlService service = new FundPnlService(
                fundPositionService, fundNavHistoryRepository, fundRepository,
                fundTransactionRepository, marketRealtimeCache, clock, currentUserService);
        FundEntity fund = fund();
        fund.setInvestmentTarget(InvestmentTarget.QDII);
        List<FundNavHistoryEntity> navs = List.of(
                nav("2026-07-17T00:00:00Z", "1.10", "2026-07-20T16:30:00Z"),
                nav("2026-07-16T00:00:00Z", "1.00", "2026-07-20T16:30:00Z"));
        when(fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(1L))
                .thenReturn(navs);
        when(fundPositionService.getHoldingShares(1L)).thenReturn(new BigDecimal("100"));

        FundPnlService.Pnl single = service.computeForFund(fund);

        assertThat(single.dailyChangePct()).isEqualByComparingTo("0.10");
        assertThat(single.dailyPnl()).isEqualByComparingTo("10.00");
        assertThat(single.isEstimated()).isFalse();
        assertThat(single.valuationSource()).isEqualTo("LATEST_CONFIRMED_NAV");
        assertThat(single.valuationDate()).isEqualTo(Instant.parse("2026-07-17T00:00:00Z"));
        assertThat(single.valuationFirstSeenAt()).isEqualTo(Instant.parse("2026-07-20T16:30:00Z"));
        verifyNoInteractions(marketRealtimeCache);

        FundNavHistoryRepository.LatestNavProjection latest = projection(1L, navs.get(0));
        FundNavHistoryRepository.LatestNavProjection previous = projection(1L, navs.get(1));
        FundTransactionRepository.HoldingSharesProjection holdingShares =
                holdingSharesProjection(1L, "100");
        when(fundNavHistoryRepository.findLatestTwoByFundIds(List.of(1L)))
                .thenReturn(List.of(latest, previous));
        when(fundTransactionRepository.aggregateConfirmedShares(List.of(1L)))
                .thenReturn(List.of(holdingShares));
        when(marketRealtimeCache.getEstimates(List.of("510300"))).thenReturn(Map.of());
        when(marketRealtimeCache.getEstimateStatuses(List.of("510300"))).thenReturn(Map.of());

        FundPnlService.Pnl batch = service.computeForFunds(List.of(fund)).get(1L);

        assertThat(batch.dailyChangePct()).isEqualByComparingTo(single.dailyChangePct());
        assertThat(batch.dailyPnl()).isEqualByComparingTo(single.dailyPnl());
        assertThat(batch.valuationDate()).isEqualTo(single.valuationDate());
    }

    @Test
    void QDII首次发现次日今日收益归零且单基金与批量结果一致() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-21T08:00:00Z"), ZoneOffset.UTC);
        FundPnlService service = new FundPnlService(
                fundPositionService, fundNavHistoryRepository, fundRepository,
                fundTransactionRepository, marketRealtimeCache, clock, currentUserService);
        FundEntity fund = fund();
        fund.setInvestmentTarget(InvestmentTarget.QDII);
        fund.setCostPerShare(new BigDecimal("1.00"));
        List<FundNavHistoryEntity> navs = List.of(
                nav("2026-07-17T00:00:00Z", "1.10", "2026-07-20T11:00:00Z"),
                nav("2026-07-16T00:00:00Z", "1.00", "2026-07-20T11:00:00Z"));
        when(fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(1L)).thenReturn(navs);
        when(fundPositionService.getHoldingShares(1L)).thenReturn(new BigDecimal("100"));

        FundPnlService.Pnl single = service.computeForFund(fund);

        FundNavHistoryRepository.LatestNavProjection latest = projection(1L, navs.get(0));
        FundNavHistoryRepository.LatestNavProjection previous = projection(1L, navs.get(1));
        FundTransactionRepository.HoldingSharesProjection holdingShares =
                holdingSharesProjection(1L, "100");
        when(fundNavHistoryRepository.findLatestTwoByFundIds(List.of(1L)))
                .thenReturn(List.of(latest, previous));
        when(fundTransactionRepository.aggregateConfirmedShares(List.of(1L)))
                .thenReturn(List.of(holdingShares));
        when(marketRealtimeCache.getEstimates(List.of("510300"))).thenReturn(Map.of());
        when(marketRealtimeCache.getEstimateStatuses(List.of("510300")))
                .thenReturn(Map.of("510300", EstimateStatus.NOT_ATTEMPTED));
        FundPnlService.Pnl batch = service.computeForFunds(List.of(fund)).get(1L);

        assertThat(single.dailyChangePct()).isZero();
        assertThat(single.dailyPnl()).isZero();
        assertThat(single.holdingAmount()).isEqualByComparingTo("110.00");
        assertThat(single.totalPnl()).isEqualByComparingTo("10.00");
        assertThat(batch.dailyChangePct()).isEqualByComparingTo(single.dailyChangePct());
        assertThat(batch.dailyPnl()).isEqualByComparingTo(single.dailyPnl());
        assertThat(batch.holdingAmount()).isEqualByComparingTo(single.holdingAmount());
        assertThat(batch.totalPnl()).isEqualByComparingTo(single.totalPnl());
        assertThat(batch.valuationDate()).isEqualTo(single.valuationDate());
    }

    @Test
    void 普通基金北京时间凌晨已有当日估值_使用估值() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-06T16:30:00Z"), ZoneOffset.UTC);
        FundPnlService service = new FundPnlService(
                fundPositionService, fundNavHistoryRepository, fundRepository,
                fundTransactionRepository, marketRealtimeCache, clock, currentUserService);
        FundEntity fund = new FundEntity();
        fund.setId(1L);
        fund.setFundCode("510300");

        FundNavHistoryEntity yesterday = nav("2026-07-06T00:00:00Z", "1.20");
        FundNavHistoryEntity previous = nav("2026-07-03T00:00:00Z", "1.10");
        when(fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(1L))
                .thenReturn(List.of(yesterday, previous));
        when(marketRealtimeCache.getEstimates(List.of("510300"))).thenReturn(Map.of(
                "510300", new FundEstimateSnapshot(new BigDecimal("0.01"),
                        "2026-07-07 00:30", "2026-07-06")));
        when(marketRealtimeCache.getEstimateStatus("510300")).thenReturn(EstimateStatus.AVAILABLE);

        FundPnlService.Pnl pnl = service.computeForFund(fund);

        verify(marketRealtimeCache).getEstimates(List.of("510300"));
        assertThat(pnl.isEstimated()).isTrue();
        assertThat(pnl.dailyChangePct()).isEqualByComparingTo("0.01");
    }

    @Test
    void 北京时间盘后重启_今日净值未落库_使用缓存估值计算今日收益() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-10T07:20:00Z"), ZoneOffset.UTC);
        FundPnlService service = new FundPnlService(
                fundPositionService, fundNavHistoryRepository, fundRepository,
                fundTransactionRepository, marketRealtimeCache, clock, currentUserService);
        FundEntity fund = fund();
        FundNavHistoryEntity yesterday = nav("2026-07-09T00:00:00Z", "1.20");
        FundNavHistoryEntity previous = nav("2026-07-08T00:00:00Z", "1.10");
        when(fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(1L))
                .thenReturn(List.of(yesterday, previous));
        when(marketRealtimeCache.getEstimates(List.of("510300"))).thenReturn(Map.of(
                "510300", new FundEstimateSnapshot(new BigDecimal("0.0123"),
                        "2026-07-10 15:00", "2026-07-09")));
        when(marketRealtimeCache.getEstimateStatus("510300")).thenReturn(EstimateStatus.AVAILABLE);
        when(fundPositionService.getHoldingShares(1L)).thenReturn(new BigDecimal("100"));

        FundPnlService.Pnl pnl = service.computeForFund(fund);

        assertThat(pnl.isEstimated()).isTrue();
        assertThat(pnl.dailyChangePct()).isEqualByComparingTo("0.0123");
        assertThat(pnl.dailyPnl()).isEqualByComparingTo("1.47600");
        assertThat(pnl.valuationSource()).isEqualTo("INTRADAY_ESTIMATE");
        assertThat(pnl.estimateTime()).isEqualTo("2026-07-10 15:00");
        assertThat(pnl.baseNavDate()).isEqualTo("2026-07-09");
    }

    @Test
    void 北京时间盘后重启_今日净值未落库且估值为空_不回退昨日收益() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-10T07:20:00Z"), ZoneOffset.UTC);
        FundPnlService service = new FundPnlService(
                fundPositionService, fundNavHistoryRepository, fundRepository,
                fundTransactionRepository, marketRealtimeCache, clock, currentUserService);
        FundEntity fund = fund();
        when(fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(1L))
                .thenReturn(List.of(
                        nav("2026-07-09T00:00:00Z", "1.20"),
                        nav("2026-07-08T00:00:00Z", "1.10")));
        when(marketRealtimeCache.getEstimates(List.of("510300"))).thenReturn(Map.of());
        when(marketRealtimeCache.getEstimateStatus("510300")).thenReturn(EstimateStatus.UNAVAILABLE);
        when(fundPositionService.getHoldingShares(1L)).thenReturn(new BigDecimal("100"));

        FundPnlService.Pnl pnl = service.computeForFund(fund);

        assertThat(pnl.dailyChangePct()).isNull();
        assertThat(pnl.dailyPnl()).isNull();
    }

    @Test
    void 当日估值尚未出现_使用最近确认净值计算持仓() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-10T00:00:00Z"), ZoneOffset.UTC);
        FundPnlService service = new FundPnlService(
                fundPositionService, fundNavHistoryRepository, fundRepository,
                fundTransactionRepository, marketRealtimeCache, clock, currentUserService);
        FundEntity fund = fund();
        fund.setCostPerShare(new BigDecimal("1.10"));
        when(fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(1L))
                .thenReturn(List.of(
                        nav("2026-07-09T00:00:00Z", "1.20"),
                        nav("2026-07-08T00:00:00Z", "1.10")));
        when(marketRealtimeCache.getEstimates(List.of("510300"))).thenReturn(Map.of());
        when(marketRealtimeCache.getEstimateStatus("510300")).thenReturn(EstimateStatus.STALE);
        when(fundPositionService.getHoldingShares(1L)).thenReturn(new BigDecimal("100"));

        FundPnlService.Pnl pnl = service.computeForFund(fund);

        assertThat(pnl.dailyChangePct()).isZero();
        assertThat(pnl.isEstimated()).isFalse();
        assertThat(pnl.holdingAmount()).isEqualByComparingTo("120.00");
        assertThat(pnl.dailyPnl()).isZero();
        assertThat(pnl.totalPnl()).isEqualByComparingTo("10.00");
        assertThat(pnl.valuationSource()).isEqualTo("LATEST_CONFIRMED_NAV");
        assertThat(pnl.valuationDate()).isEqualTo(Instant.parse("2026-07-09T00:00:00Z"));
    }

    @Test
    void 兼容基金查询使用Accounting当前成本而不是legacy成本() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-09T00:00:00Z"), ZoneOffset.UTC);
        FundPnlService service = new FundPnlService(
                fundPositionService, fundNavHistoryRepository, fundRepository,
                fundTransactionRepository, marketRealtimeCache, clock, currentUserService);
        FundEntity fund = fund();
        fund.setCostPerShare(new BigDecimal("1.00"));
        when(fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(1L))
                .thenReturn(List.of(nav("2026-07-09T00:00:00Z", "1.50"),
                        nav("2026-07-08T00:00:00Z", "1.40")));
        when(fundPositionService.getHoldingShares(1L)).thenReturn(new BigDecimal("100"));

        FundPnlService.Pnl pnl = service.computeForFund(fund,
                Map.of(1L, new BigDecimal("1.25")));

        assertThat(pnl.totalPnl()).isEqualByComparingTo("25.00");
    }

    @Test
    void Accounting持仓存在但成本为空时不回退legacy成本() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-09T00:00:00Z"), ZoneOffset.UTC);
        FundPnlService service = new FundPnlService(
                fundPositionService, fundNavHistoryRepository, fundRepository,
                fundTransactionRepository, marketRealtimeCache, clock, currentUserService);
        FundEntity fund = fund();
        fund.setCostPerShare(new BigDecimal("1.00"));
        when(fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(1L))
                .thenReturn(List.of(nav("2026-07-09T00:00:00Z", "1.50"),
                        nav("2026-07-08T00:00:00Z", "1.40")));
        when(fundPositionService.getHoldingShares(1L)).thenReturn(new BigDecimal("100"));
        Map<Long, BigDecimal> currentCost = new java.util.HashMap<>();
        currentCost.put(1L, null);

        FundPnlService.Pnl pnl = service.computeForFund(fund, currentCost);

        assertThat(pnl.totalPnl()).isNull();
    }

    @Test
    void 货币基金不读取盘中估值且返回不可用() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-10T07:20:00Z"), ZoneOffset.UTC);
        FundPnlService service = new FundPnlService(
                fundPositionService, fundNavHistoryRepository, fundRepository,
                fundTransactionRepository, marketRealtimeCache, clock, currentUserService);
        FundEntity fund = fund();
        fund.setInvestmentTarget(InvestmentTarget.MONEY_MARKET);
        when(fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(1L))
                .thenReturn(List.of(nav("2026-07-09T00:00:00Z", "1.20")));
        when(fundPositionService.getHoldingShares(1L)).thenReturn(new BigDecimal("100"));

        FundPnlService.Pnl pnl = service.computeForFund(fund);

        assertThat(pnl.estimateStatus()).isEqualTo(EstimateStatus.UNAVAILABLE);
        assertThat(pnl.dailyChangePct()).isNull();
        verifyNoInteractions(marketRealtimeCache);
    }

    private static FundEntity fund() {
        FundEntity fund = new FundEntity();
        fund.setId(1L);
        fund.setFundCode("510300");
        return fund;
    }

    private static FundNavHistoryEntity nav(String date, String accumulatedNav) {
        return nav(date, accumulatedNav, "2026-07-20T11:00:00Z");
    }

    private static FundNavHistoryEntity nav(String date, String accumulatedNav, String firstSeenAt) {
        FundNavHistoryEntity entity = new FundNavHistoryEntity();
        entity.setNavDate(Instant.parse(date));
        entity.setNav(new BigDecimal(accumulatedNav));
        entity.setAccumulatedNav(new BigDecimal(accumulatedNav));
        entity.setFirstSeenAt(Instant.parse(firstSeenAt));
        return entity;
    }

    private static FundNavHistoryRepository.LatestNavProjection projection(
            Long fundId, FundNavHistoryEntity nav) {
        FundNavHistoryRepository.LatestNavProjection projection =
                mock(FundNavHistoryRepository.LatestNavProjection.class);
        when(projection.getFundId()).thenReturn(fundId);
        when(projection.getNavDate()).thenReturn(nav.getNavDate());
        when(projection.getNav()).thenReturn(nav.getNav());
        when(projection.getAccumulatedNav()).thenReturn(nav.getAccumulatedNav());
        when(projection.getFirstSeenAt()).thenReturn(nav.getFirstSeenAt());
        return projection;
    }

    private static FundTransactionRepository.HoldingSharesProjection holdingSharesProjection(
            Long fundId, String shares) {
        FundTransactionRepository.HoldingSharesProjection projection =
                mock(FundTransactionRepository.HoldingSharesProjection.class);
        when(projection.getFundId()).thenReturn(fundId);
        when(projection.getHoldingShares()).thenReturn(new BigDecimal(shares));
        return projection;
    }
}
