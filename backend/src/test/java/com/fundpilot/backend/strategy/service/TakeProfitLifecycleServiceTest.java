package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundLotEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.enums.TakeProfitPhase;
import com.fundpilot.backend.fund.repository.FundLotRepository;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import com.fundpilot.backend.strategy.service.support.TakeProfitEvaluation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TakeProfitLifecycleServiceTest {

    private static final Instant TODAY = Instant.parse("2026-07-10T00:00:00Z");

    @Mock FundLotRepository fundLotRepository;
    @Mock FundStrategyRepository fundStrategyRepository;
    @Mock TradingCalendarService tradingCalendarService;

    private TakeProfitLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new TakeProfitLifecycleService(
                fundLotRepository, fundStrategyRepository, tradingCalendarService);
    }

    @Test
    void 收益达到启动线_当天只进入ARMED不卖出() {
        FundEntity fund = fund("1.00");
        FundStrategyEntity strategy = strategy(TakeProfitPhase.ACCUMULATING);

        TakeProfitEvaluation result = service.prepare(
                fund, strategy, new BigDecimal("1.15"), new BigDecimal("1.30"),
                new BigDecimal("100"), TODAY);

        assertThat(result.evaluationEnabled()).isFalse();
        assertThat(strategy.getTakeProfitPhase()).isEqualTo(TakeProfitPhase.ARMED);
        assertThat(strategy.getCyclePeakNav()).isEqualByComparingTo("1.30");
        assertThat(strategy.getCycleStartedAt()).isEqualTo(TODAY);
    }

    @Test
    void ARMED创新高_更新高点且当天不卖出() {
        FundEntity fund = fund("1.00");
        FundStrategyEntity strategy = strategy(TakeProfitPhase.ARMED);
        strategy.setCyclePeakNav(new BigDecimal("1.15"));

        TakeProfitEvaluation result = service.prepare(
                fund, strategy, new BigDecimal("1.10"), new BigDecimal("1.20"),
                new BigDecimal("100"), TODAY);

        assertThat(result.evaluationEnabled()).isFalse();
        assertThat(strategy.getCyclePeakNav()).isEqualByComparingTo("1.20");
    }

    @Test
    void ARMED回撤时_成熟lot与未跟踪份额可参与止盈() {
        FundEntity fund = fund("1.00");
        FundStrategyEntity strategy = strategy(TakeProfitPhase.ARMED);
        strategy.setCyclePeakNav(new BigDecimal("1.20"));
        FundLotEntity mature = lot("60", "2026-06-01T00:00:00Z");
        FundLotEntity recent = lot("20", "2026-07-09T00:00:00Z");
        when(fundLotRepository.findOpenLotsByFundIdOrderByAcquireDateAsc(1L))
                .thenReturn(List.of(mature, recent));
        when(tradingCalendarService.daysBetweenTradingDays(any(), any()))
                .thenReturn(10L, 1L);

        TakeProfitEvaluation result = service.prepare(
                fund, strategy, new BigDecimal("1.10"), new BigDecimal("1.10"),
                new BigDecimal("100"), TODAY);

        assertThat(result.evaluationEnabled()).isTrue();
        assertThat(result.floatingProfit()).isEqualByComparingTo("10");
        // 60 份成熟 lot + 20 份未跟踪 ADJUST_IN，最近买入 20 份不参与。
        assertThat(result.matureRedeemableShares()).isEqualByComparingTo("80");
    }

    @Test
    void 冷静期结束且收益仍达标_直接开启新周期但当天不卖() {
        FundEntity fund = fund("1.00");
        FundStrategyEntity strategy = strategy(TakeProfitPhase.COOLDOWN);
        strategy.setCooldownStartedAt(Instant.parse("2026-06-20T00:00:00Z"));
        when(tradingCalendarService.daysBetweenTradingDays(any(), any())).thenReturn(10L);

        TakeProfitEvaluation result = service.prepare(
                fund, strategy, new BigDecimal("1.18"), new BigDecimal("1.35"),
                new BigDecimal("100"), TODAY);

        assertThat(result.evaluationEnabled()).isFalse();
        assertThat(strategy.getTakeProfitPhase()).isEqualTo(TakeProfitPhase.ARMED);
        assertThat(strategy.getCyclePeakNav()).isEqualByComparingTo("1.35");
        assertThat(strategy.getCooldownStartedAt()).isNull();
    }

    @Test
    void 累计净值上涨但单位净值未盈利_不得启动止盈() {
        FundEntity fund = fund("1.00");
        FundStrategyEntity strategy = strategy(TakeProfitPhase.ACCUMULATING);

        TakeProfitEvaluation result = service.prepare(
                fund, strategy, new BigDecimal("1.00"), new BigDecimal("1.30"),
                new BigDecimal("100"), TODAY);

        assertThat(result.evaluationEnabled()).isFalse();
        assertThat(strategy.getTakeProfitPhase()).isEqualTo(TakeProfitPhase.ACCUMULATING);
        assertThat(strategy.getCyclePeakNav()).isNull();
    }

    @Test
    void 止盈交易确认后进入COOLDOWN_撤销则恢复ARMED() {
        FundStrategyEntity strategy = strategy(TakeProfitPhase.TRIGGERED);
        strategy.setTriggeredSignalId(99L);
        strategy.setCycleStartedAt(Instant.parse("2026-07-01T00:00:00Z"));
        strategy.setCyclePeakNav(new BigDecimal("1.50"));
        SignalLogEntity signal = new SignalLogEntity();
        signal.setId(99L);
        signal.setReason(SignalReason.TRAILING_STOP);
        signal.setFundStrategyEntity(strategy);
        FundTransactionEntity transaction = new FundTransactionEntity();
        transaction.setSignalLogEntity(signal);
        transaction.setStatus(FundTransactionStatus.CONFIRMED);
        transaction.setConfirmTime(TODAY);

        service.onTransactionConfirmed(transaction);

        assertThat(strategy.getTakeProfitPhase()).isEqualTo(TakeProfitPhase.COOLDOWN);
        assertThat(strategy.getCooldownStartedAt()).isEqualTo(TODAY);
        verify(fundStrategyRepository).save(strategy);

        strategy.setTakeProfitPhase(TakeProfitPhase.TRIGGERED);
        strategy.setTriggeredSignalId(99L);
        transaction.setStatus(FundTransactionStatus.CANCELLED);
        service.onTransactionCancelled(transaction);
        assertThat(strategy.getTakeProfitPhase()).isEqualTo(TakeProfitPhase.ARMED);
        assertThat(strategy.getCycleStartedAt()).isNull();
        assertThat(strategy.getCyclePeakNav()).isNull();
    }

    @Test
    void 忽略当前止盈信号_恢复ARMED并清除绑定() {
        FundStrategyEntity strategy = strategy(TakeProfitPhase.TRIGGERED);
        strategy.setTriggeredSignalId(99L);
        strategy.setCycleStartedAt(Instant.parse("2026-07-01T00:00:00Z"));
        strategy.setCyclePeakNav(new BigDecimal("1.50"));
        SignalLogEntity signal = new SignalLogEntity();
        signal.setId(99L);
        signal.setReason(SignalReason.TRAILING_STOP);
        signal.setFundStrategyEntity(strategy);

        service.onSignalIgnored(signal);

        assertThat(strategy.getTakeProfitPhase()).isEqualTo(TakeProfitPhase.ARMED);
        assertThat(strategy.getTriggeredSignalId()).isNull();
        assertThat(strategy.getCycleStartedAt()).isNull();
        assertThat(strategy.getCyclePeakNav()).isNull();
        verify(fundStrategyRepository).save(strategy);
    }

    @Test
    void 恢复ARMED后首次评估只建立新峰值不重复触发() {
        FundEntity fund = fund("1.00");
        FundStrategyEntity strategy = strategy(TakeProfitPhase.ARMED);

        TakeProfitEvaluation result = service.prepare(
                fund, strategy, new BigDecimal("1.20"), new BigDecimal("1.20"),
                new BigDecimal("100"), TODAY);

        assertThat(result.evaluationEnabled()).isFalse();
        assertThat(strategy.getCycleStartedAt()).isEqualTo(TODAY);
        assertThat(strategy.getCyclePeakNav()).isEqualByComparingTo("1.20");
    }

    private FundEntity fund(String costPerShare) {
        FundEntity fund = new FundEntity();
        fund.setId(1L);
        fund.setCostPerShare(new BigDecimal(costPerShare));
        return fund;
    }

    private FundStrategyEntity strategy(TakeProfitPhase phase) {
        FundStrategyEntity strategy = new FundStrategyEntity();
        strategy.setTakeProfitPhase(phase);
        strategy.setProfitActivationPercent(new BigDecimal("0.15"));
        strategy.setCooldownTradingDays(10);
        return strategy;
    }

    private FundLotEntity lot(String shares, String acquireDate) {
        FundLotEntity lot = new FundLotEntity();
        lot.setRemainingShares(new BigDecimal(shares));
        lot.setAcquireDate(Instant.parse(acquireDate));
        return lot;
    }
}
