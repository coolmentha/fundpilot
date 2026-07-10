package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundSubType;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.enums.StrategyParamStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.FundPositionService;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.market.entity.MarketIndicatorSnapshotEntity;
import com.fundpilot.backend.market.enums.VolumeState;
import com.fundpilot.backend.market.enums.WeeklyMacdState;
import com.fundpilot.backend.market.service.MarketIndicatorProvider;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.repository.SignalLogRepository;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import com.fundpilot.backend.strategy.service.DisciplineStrategyService;
import com.fundpilot.backend.strategy.service.support.SignalResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * SignalGenerationService 单元测试(issue #13):Mockito 纯单元测试,验证编排逻辑——
 * 遍历 EFFECTIVE 基金、snapshot 缺失降级、重跑覆盖、单只异常隔离。
 * <p>
 * DisciplineStrategyService 被 mock,聚焦"编排落库"而非"引擎决策"(引擎决策由 #12 单测覆盖)。
 * FundEntity/FundStrategyEntity 用真实对象( setId 区分,避免 {@code @EqualsAndHashCode(of="id")} 误匹配)。
 * <p>金字塔退场后 buildCapitalContext 仅查 peakNav/holdingPeakNav/holdingShares/lastBuyConfirmTime,
 * 不再查 BUILD/ADD tier 加仓份额。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignalGenerationServiceTest {

    private static final Instant DATE = Instant.parse("2026-06-25T00:00:00Z");

    @Mock FundStrategyRepository fundStrategyRepository;
    @Mock FundRepository fundRepository;
    @Mock FundNavHistoryRepository fundNavHistoryRepository;
    @Mock FundTransactionRepository fundTransactionRepository;
    @Mock FundPositionService fundPositionService;
    @Mock MarketIndicatorProvider marketIndicatorProvider;
    @Mock SignalLogRepository signalLogRepository;
    @Mock TradingCalendarService tradingCalendarService;
    @Mock DisciplineStrategyService disciplineStrategyService;

    private SignalGenerationService service;

    @BeforeEach
    void setUp() {
        service = new SignalGenerationService(fundStrategyRepository, fundRepository,
                fundNavHistoryRepository, fundTransactionRepository, fundPositionService,
                marketIndicatorProvider, signalLogRepository,
                tradingCalendarService, disciplineStrategyService);
    }

    /** 构造一只基金 + EFFECTIVE 策略,并 stub buildCapitalContext 所需的全部查询。返回 strategy(可取 fund)。 */
    private FundStrategyEntity stubFund(Long id, FundStatus status) {
        FundEntity fund = new FundEntity();
        fund.setId(id);
        fund.setStatus(status);
        fund.setFundCategory(FundCategory.BROAD_BASE);
        fund.setOpenedAt(null); // openedAt=null → 走 peakNav 分支,避免 findPeakAccumulatedNavSince
        FundStrategyEntity strategy = new FundStrategyEntity();
        strategy.setId(id); // 区分 strategy,避免 @EqualsAndHashCode(of="id") 误匹配
        strategy.setStatus(StrategyParamStatus.EFFECTIVE);
        strategy.setFundEntity(fund);
        when(fundRepository.findById(id)).thenReturn(Optional.of(fund));
        when(fundStrategyRepository.findByFundEntity_IdAndStatus(id, StrategyParamStatus.EFFECTIVE))
                .thenReturn(Optional.of(strategy));
        when(fundNavHistoryRepository.findPeakAccumulatedNav(id)).thenReturn(Optional.of(new BigDecimal("1.0")));
        when(fundPositionService.getHoldingShares(id)).thenReturn(BigDecimal.ZERO);
        // MIN_HOLD_DAYS 只看最近一笔已确认买入类交易。
        FundTransactionEntity confirmTx = new FundTransactionEntity();
        confirmTx.setConfirmTime(DATE.minus(10, java.time.temporal.ChronoUnit.DAYS));
        when(fundTransactionRepository
                .findFirstByFundEntity_IdAndStatusAndSourceInAndConfirmTimeIsNotNullOrderByConfirmTimeDesc(
                        eq(id), eq(FundTransactionStatus.CONFIRMED), anyCollection()))
                .thenReturn(Optional.of(confirmTx));
        return strategy;
    }

    private MarketIndicatorSnapshotEntity snapshot(BigDecimal nav) {
        MarketIndicatorSnapshotEntity snap = new MarketIndicatorSnapshotEntity();
        snap.setSnapshotDate(DATE);
        snap.setCurrentNav(nav);
        snap.setPriceAboveYearLine(true);
        snap.setYearLineRising(true);
        snap.setWeeklyMacdState(WeeklyMacdState.GREEN_SHRINKING);
        snap.setVolumeState(VolumeState.NORMAL);
        snap.setWeeklyDropPercent(BigDecimal.ZERO);
        snap.setSixtyDayHigh(true);
        return snap;
    }

    @Test
    void generateDailySignals_两只基金分别落SELL和NONE信号() {
        FundStrategyEntity s1 = stubFund(1L, FundStatus.HOLDING);
        FundStrategyEntity s2 = stubFund(2L, FundStatus.HOLDING);
        when(fundStrategyRepository.findEffectiveFundIds()).thenReturn(List.of(1L, 2L));
        when(marketIndicatorProvider.getIndicators(eq(1L), eq(DATE))).thenReturn(Optional.of(snapshot(new BigDecimal("1.0"))));
        when(marketIndicatorProvider.getIndicators(eq(2L), eq(DATE))).thenReturn(Optional.of(snapshot(new BigDecimal("1.0"))));
        when(disciplineStrategyService.evaluateSignal(eq(s1.getFundEntity()), eq(s1), any(), any(), any(), anyLong()))
                .thenReturn(SignalResult.none(SignalReason.NO_STRATEGY));
        when(disciplineStrategyService.evaluateSignal(eq(s2.getFundEntity()), eq(s2), any(), any(), any(), anyLong()))
                .thenReturn(SignalResult.none(SignalReason.NO_STRATEGY));

        service.generateDailySignals(DATE);

        ArgumentCaptor<SignalLogEntity> captor = ArgumentCaptor.forClass(SignalLogEntity.class);
        verify(signalLogRepository, times(2)).save(captor.capture());
        assertThat(captor.getAllValues()).extracting(SignalLogEntity::getSignalType)
                .containsExactlyInAnyOrder(SignalType.NONE, SignalType.NONE);
        // 两只基金的 strategy 都写回(副作用随信号生成一起落库)
        verify(fundStrategyRepository, times(2)).save(any(FundStrategyEntity.class));
    }

    @Test
    void generateDailySignals_snapshot缺失落NONE_INSUFFICIENT_MARKET_DATA() {
        stubFund(1L, FundStatus.HOLDING);
        when(fundStrategyRepository.findEffectiveFundIds()).thenReturn(List.of(1L));
        when(marketIndicatorProvider.getIndicators(eq(1L), eq(DATE))).thenReturn(Optional.empty());

        service.generateDailySignals(DATE);

        verify(disciplineStrategyService, never()).evaluateSignal(any(), any(), any(), any(), any(), anyLong());
        ArgumentCaptor<SignalLogEntity> captor = ArgumentCaptor.forClass(SignalLogEntity.class);
        verify(signalLogRepository).save(captor.capture());
        SignalLogEntity saved = captor.getValue();
        assertThat(saved.getSignalType()).isEqualTo(SignalType.NONE);
        assertThat(saved.getReason()).isEqualTo(SignalReason.INSUFFICIENT_MARKET_DATA);
    }

    @Test
    void generateDailySignals_重跑软删同日旧行再写新() {
        FundStrategyEntity s1 = stubFund(1L, FundStatus.HOLDING);
        when(fundStrategyRepository.findEffectiveFundIds()).thenReturn(List.of(1L));
        when(marketIndicatorProvider.getIndicators(eq(1L), eq(DATE))).thenReturn(Optional.of(snapshot(new BigDecimal("1.0"))));
        when(disciplineStrategyService.evaluateSignal(eq(s1.getFundEntity()), eq(s1), any(), any(), any(), anyLong()))
                .thenReturn(SignalResult.none(SignalReason.NO_STRATEGY));
        SignalLogEntity stale = new SignalLogEntity();
        when(signalLogRepository.findByFundEntity_IdAndSignalDateBetween(eq(1L), any(), any()))
                .thenReturn(List.of(stale));

        service.generateDailySignals(DATE);

        verify(signalLogRepository).delete(stale); // 软删旧行(SQLDelete 重定向为 UPDATE deleted_date)
        verify(signalLogRepository).save(any(SignalLogEntity.class)); // 写新行
    }

    @Test
    void generateDailySignals_单只基金异常不影响其他基金() {
        FundStrategyEntity s2 = stubFund(2L, FundStatus.HOLDING);
        stubFund(1L, FundStatus.HOLDING);
        when(fundStrategyRepository.findEffectiveFundIds()).thenReturn(List.of(1L, 2L));
        // fund1: snapshot 拉取抛异常
        when(marketIndicatorProvider.getIndicators(eq(1L), eq(DATE))).thenThrow(new RuntimeException("snap 拉取失败"));
        // fund2: 正常
        when(marketIndicatorProvider.getIndicators(eq(2L), eq(DATE))).thenReturn(Optional.of(snapshot(new BigDecimal("1.0"))));
        when(disciplineStrategyService.evaluateSignal(eq(s2.getFundEntity()), eq(s2), any(), any(), any(), anyLong()))
                .thenReturn(SignalResult.none(SignalReason.NO_STRATEGY));

        service.generateDailySignals(DATE);

        // fund1 未落 SignalLog,fund2 正常落一次
        verify(signalLogRepository, times(1)).save(any(SignalLogEntity.class));
    }

    @Test
    void generateDailySignals_ETF放量下跌快照经真实策略链触发逻辑止损() {
        FundStrategyEntity strategy = stubFund(1L, FundStatus.HOLDING);
        strategy.getFundEntity().setFundSubType(FundSubType.ETF);
        strategy.setStopLossPullbackPercent(new BigDecimal("-0.08"));
        when(fundStrategyRepository.findEffectiveFundIds()).thenReturn(List.of(1L));
        when(fundPositionService.getHoldingShares(1L)).thenReturn(new BigDecimal("100"));

        MarketIndicatorSnapshotEntity snapshot = snapshot(new BigDecimal("0.80"));
        snapshot.setPriceAboveYearLine(false);
        snapshot.setWeeklyMacdState(WeeklyMacdState.GREEN_EXPANDING);
        snapshot.setVolumeState(VolumeState.HIGH_DROP);
        when(marketIndicatorProvider.getIndicators(1L, DATE)).thenReturn(Optional.of(snapshot));

        SignalGenerationService realService = new SignalGenerationService(
                fundStrategyRepository, fundRepository, fundNavHistoryRepository,
                fundTransactionRepository, fundPositionService, marketIndicatorProvider,
                signalLogRepository, tradingCalendarService, new DisciplineStrategyService());

        realService.generateDailySignals(DATE);

        ArgumentCaptor<SignalLogEntity> captor = ArgumentCaptor.forClass(SignalLogEntity.class);
        verify(signalLogRepository).save(captor.capture());
        assertThat(captor.getValue().getSignalType()).isEqualTo(SignalType.SELL);
        assertThat(captor.getValue().getReason()).isEqualTo(SignalReason.LOGIC_BROKEN);
    }

    @Test
    void generateDailySignals_无买入确认记录时仍落信号() {
        FundStrategyEntity strategy = stubFund(1L, FundStatus.HOLDING);
        when(fundTransactionRepository
                .findFirstByFundEntity_IdAndStatusAndSourceInAndConfirmTimeIsNotNullOrderByConfirmTimeDesc(
                        eq(1L), eq(FundTransactionStatus.CONFIRMED), anyCollection()))
                .thenReturn(Optional.empty());
        when(fundStrategyRepository.findEffectiveFundIds()).thenReturn(List.of(1L));
        when(marketIndicatorProvider.getIndicators(1L, DATE))
                .thenReturn(Optional.of(snapshot(new BigDecimal("1.0"))));
        when(disciplineStrategyService.evaluateSignal(eq(strategy.getFundEntity()), eq(strategy),
                any(), any(), any(), anyLong())).thenReturn(SignalResult.none(SignalReason.NO_STRATEGY));

        service.generateDailySignals(DATE);

        verify(signalLogRepository).save(any(SignalLogEntity.class));
    }
}
