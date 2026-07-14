package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundLotEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.enums.TakeProfitPhase;
import com.fundpilot.backend.fund.repository.FundLotRepository;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import com.fundpilot.backend.strategy.service.support.TakeProfitEvaluation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;

/** 定投止盈周期状态机，以及基于 FIFO lot 的成熟可赎回份额计算。 */
@Service
@RequiredArgsConstructor
public class TakeProfitLifecycleService {

    private static final MathContext MATH = MathContext.DECIMAL64;
    private static final int MIN_HOLD_TRADING_DAYS = 5;

    private final FundLotRepository fundLotRepository;
    private final FundStrategyRepository fundStrategyRepository;
    private final TradingCalendarService tradingCalendarService;

    public TakeProfitEvaluation prepare(FundEntity fund, FundStrategyEntity strategy,
                                        BigDecimal currentUnitNav, BigDecimal currentAccumulatedNav,
                                        BigDecimal holdingShares, Instant today) {
        if (!hasPositive(currentUnitNav) || !hasPositive(currentAccumulatedNav)
                || !hasPositive(holdingShares) || !hasPositive(fund.getCostPerShare())) {
            return TakeProfitEvaluation.disabled();
        }

        BigDecimal holdingCost = fund.getCostPerShare().multiply(holdingShares, MATH);
        BigDecimal marketValue = currentUnitNav.multiply(holdingShares, MATH);
        BigDecimal floatingProfit = marketValue.subtract(holdingCost).max(BigDecimal.ZERO);
        BigDecimal overallReturn = floatingProfit.divide(holdingCost, MATH);

        TakeProfitPhase phase = strategy.getTakeProfitPhase();
        if (phase == null) {
            phase = TakeProfitPhase.ACCUMULATING;
            strategy.setTakeProfitPhase(phase);
        }

        if (phase == TakeProfitPhase.TRIGGERED) {
            return TakeProfitEvaluation.disabled();
        }

        if (phase == TakeProfitPhase.COOLDOWN) {
            if (!cooldownFinished(strategy, today)) {
                return TakeProfitEvaluation.disabled();
            }
            strategy.setCooldownStartedAt(null);
            strategy.setTriggeredSignalId(null);
            if (overallReturn.compareTo(strategy.getProfitActivationPercent()) >= 0) {
                arm(strategy, currentAccumulatedNav, today);
            } else {
                strategy.setTakeProfitPhase(TakeProfitPhase.ACCUMULATING);
                strategy.setCycleStartedAt(null);
                strategy.setCyclePeakNav(null);
            }
            return TakeProfitEvaluation.disabled();
        }

        if (phase == TakeProfitPhase.ACCUMULATING) {
            if (overallReturn.compareTo(strategy.getProfitActivationPercent()) >= 0) {
                arm(strategy, currentAccumulatedNav, today);
            }
            return TakeProfitEvaluation.disabled();
        }

        if (strategy.getCyclePeakNav() == null) {
            arm(strategy, currentAccumulatedNav, today);
            return TakeProfitEvaluation.disabled();
        }

        if (currentAccumulatedNav.compareTo(strategy.getCyclePeakNav()) > 0) {
            strategy.setCyclePeakNav(currentAccumulatedNav);
            return TakeProfitEvaluation.disabled();
        }

        return new TakeProfitEvaluation(
                true,
                floatingProfit,
                matureRedeemableShares(fund.getId(), holdingShares, today));
    }

    public void bindTriggeredSignal(FundStrategyEntity strategy, Long signalId) {
        strategy.setTakeProfitPhase(TakeProfitPhase.TRIGGERED);
        strategy.setTriggeredSignalId(signalId);
        fundStrategyRepository.save(strategy);
    }

    public void onTransactionConfirmed(FundTransactionEntity transaction) {
        if (!isTrailingStopTransaction(transaction)
                || transaction.getStatus() != FundTransactionStatus.CONFIRMED) {
            return;
        }
        FundStrategyEntity strategy = transaction.getSignalLogEntity().getFundStrategyEntity();
        if (!matchesTriggeredSignal(strategy, transaction)) {
            return;
        }
        strategy.setTakeProfitPhase(TakeProfitPhase.COOLDOWN);
        strategy.setCooldownStartedAt(transaction.getConfirmTime());
        strategy.setTriggeredSignalId(null);
        strategy.setCycleStartedAt(null);
        strategy.setCyclePeakNav(null);
        fundStrategyRepository.save(strategy);
    }

    public void onTransactionCancelled(FundTransactionEntity transaction) {
        if (!isTrailingStopTransaction(transaction)
                || transaction.getStatus() != FundTransactionStatus.CANCELLED) {
            return;
        }
        FundStrategyEntity strategy = transaction.getSignalLogEntity().getFundStrategyEntity();
        if (!matchesTriggeredSignal(strategy, transaction)) {
            return;
        }
        resetTriggeredCycle(strategy);
        fundStrategyRepository.save(strategy);
    }

    /** 忽略当前止盈信号等价于放弃本次执行，恢复 ARMED 并等待建立新峰值。 */
    public void onSignalIgnored(com.fundpilot.backend.signal.entity.SignalLogEntity signal) {
        if (signal.getReason() != SignalReason.TRAILING_STOP || signal.getFundStrategyEntity() == null) {
            return;
        }
        FundStrategyEntity strategy = signal.getFundStrategyEntity();
        if (strategy.getTakeProfitPhase() != TakeProfitPhase.TRIGGERED
                || strategy.getTriggeredSignalId() == null
                || !strategy.getTriggeredSignalId().equals(signal.getId())) {
            return;
        }
        resetTriggeredCycle(strategy);
        fundStrategyRepository.save(strategy);
    }

    private boolean cooldownFinished(FundStrategyEntity strategy, Instant today) {
        if (strategy.getCooldownStartedAt() == null) {
            return true;
        }
        long days = tradingCalendarService.daysBetweenTradingDays(
                ChinaTradingDate.toUtcDate(strategy.getCooldownStartedAt()), today);
        return days >= strategy.getCooldownTradingDays();
    }

    private BigDecimal matureRedeemableShares(Long fundId, BigDecimal holdingShares, Instant today) {
        List<FundLotEntity> lots = fundLotRepository.findOpenLotsByFundIdOrderByAcquireDateAsc(fundId);
        BigDecimal trackedShares = BigDecimal.ZERO;
        BigDecimal matureShares = BigDecimal.ZERO;
        for (FundLotEntity lot : lots) {
            trackedShares = trackedShares.add(lot.getRemainingShares());
            long holdingDays = tradingCalendarService.daysBetweenTradingDays(
                    ChinaTradingDate.toUtcDate(lot.getAcquireDate()), today);
            if (holdingDays >= MIN_HOLD_TRADING_DAYS) {
                matureShares = matureShares.add(lot.getRemainingShares());
            }
        }
        BigDecimal untrackedShares = holdingShares.subtract(trackedShares).max(BigDecimal.ZERO);
        return matureShares.add(untrackedShares).min(holdingShares);
    }

    private static void arm(FundStrategyEntity strategy, BigDecimal currentAccumulatedNav, Instant today) {
        strategy.setTakeProfitPhase(TakeProfitPhase.ARMED);
        strategy.setCycleStartedAt(today);
        strategy.setCyclePeakNav(currentAccumulatedNav);
        strategy.setTriggeredSignalId(null);
    }

    private static void resetTriggeredCycle(FundStrategyEntity strategy) {
        strategy.setTakeProfitPhase(TakeProfitPhase.ARMED);
        strategy.setTriggeredSignalId(null);
        strategy.setCycleStartedAt(null);
        strategy.setCyclePeakNav(null);
    }

    private static boolean isTrailingStopTransaction(FundTransactionEntity transaction) {
        return transaction.getSignalLogEntity() != null
                && transaction.getSignalLogEntity().getReason() == SignalReason.TRAILING_STOP
                && transaction.getSignalLogEntity().getFundStrategyEntity() != null;
    }

    private static boolean matchesTriggeredSignal(FundStrategyEntity strategy, FundTransactionEntity transaction) {
        Long triggeredSignalId = strategy.getTriggeredSignalId();
        Long signalId = transaction.getSignalLogEntity().getId();
        return triggeredSignalId != null && triggeredSignalId.equals(signalId);
    }

    private static boolean hasPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }
}
