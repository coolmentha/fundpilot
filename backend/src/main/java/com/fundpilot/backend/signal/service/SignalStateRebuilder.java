package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.FundPositionService;
import com.fundpilot.backend.strategy.service.support.TakeProfitParams;
import com.fundpilot.backend.strategy.service.support.TrailingStopEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 信号状态重建器(issue #62):从建仓起逐日回放净值+已确认交易,用 {@link TrailingStopEngine}
 * 推进轮内 {@link TrailingStopEngine.State} 到当日,供 SignalGenerationService 装配生产信号。
 * <p>High/peakYield/止盈线/前日跌破/冷却计数 均为运行态,不落库——每日从历史回放重建(ADR-0015)。
 * 与回测模拟器同源(都调 TrailingStopEngine),保证生产信号与回测口径一致。
 *
 * <p>回放范围:openedAt → today(不含 today,今日由 SignalGenerationService 调 evaluate 收尾)。
 * 极端行情保护所需的单日跌/3日累计跌由调用方在今日 evaluate 时注入(本类不装配 extreme)。
 */
@Component
@RequiredArgsConstructor
public class SignalStateRebuilder {

    private static final MathContext MATH = MathContext.DECIMAL64;

    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final FundPositionService fundPositionService;

    /**
     * 重建当日前的轮内状态 + 持仓快照。
     *
     * @param fund   基金(含 openedAt)
     * @param today  当日(回放至 today 前;today 当日的 evaluate 由调用方收尾)
     * @param params 真实策略参数(回放与今日同源,避免参数漂移导致 State 失真)
     * @return 状态 + 持仓快照
     */
    public Rebuilt rebuild(FundEntity fund, Instant today, TakeProfitParams params) {
        Instant start = fund.getOpenedAt() != null ? fund.getOpenedAt() : Instant.EPOCH;
        List<FundNavHistoryEntity> navHistory =
                fundNavHistoryRepository.findByFundEntity_IdAndNavDateBetweenOrderByNavDateAsc(
                        fund.getId(), start, today);
        // 当日前的 CONFIRMED 交易(navDate < today),按日分组
        List<FundTransactionEntity> txs = fundTransactionRepository
                .findByFundEntity_IdAndStatus(fund.getId(), FundTransactionStatus.CONFIRMED);
        Map<Instant, List<FundTransactionEntity>> txByDay = new TreeMap<>();
        for (FundTransactionEntity tx : txs) {
            Instant day = tx.getConfirmTime() != null ? tx.getConfirmTime() : Instant.EPOCH;
            if (day.isBefore(today)) {
                txByDay.computeIfAbsent(day, k -> new java.util.ArrayList<>()).add(tx);
            }
        }

        TrailingStopEngine.State state = TrailingStopEngine.State.initial();
        BigDecimal holdingShares = BigDecimal.ZERO;
        BigDecimal totalAcquired = BigDecimal.ZERO;
        BigDecimal totalSold = BigDecimal.ZERO;
        BigDecimal invested = BigDecimal.ZERO;
        BigDecimal cashedOut = BigDecimal.ZERO;

        for (FundNavHistoryEntity nav : navHistory) {
            Instant day = nav.getNavDate();
            if (!day.isBefore(today)) {
                break; // 跳过今日及之后
            }
            // 应用当日交易
            List<FundTransactionEntity> dayTxs = txByDay.get(day);
            if (dayTxs != null) {
                for (FundTransactionEntity tx : dayTxs) {
                    if (isBuy(tx.getSource())) {
                        BigDecimal amt = tx.getAmount() != null ? tx.getAmount() : BigDecimal.ZERO;
                        BigDecimal sh = tx.getShares() != null ? tx.getShares()
                                : (nav.getAccumulatedNav() != null && nav.getAccumulatedNav().signum() > 0
                                        ? amt.divide(nav.getAccumulatedNav(), MATH) : BigDecimal.ZERO);
                        holdingShares = holdingShares.add(sh, MATH);
                        totalAcquired = totalAcquired.add(sh, MATH);
                        invested = invested.add(amt, MATH);
                    } else if (tx.getSource() == FundTransactionSource.DECREASE
                            || tx.getSource() == FundTransactionSource.TRANSFER_OUT) {
                        BigDecimal sh = tx.getShares() != null ? tx.getShares() : BigDecimal.ZERO;
                        holdingShares = holdingShares.subtract(sh, MATH);
                        totalSold = totalSold.add(sh, MATH);
                        BigDecimal amt = tx.getAmount() != null ? tx.getAmount()
                                : sh.multiply(nav.getAccumulatedNav() != null ? nav.getAccumulatedNav() : BigDecimal.ZERO, MATH);
                        cashedOut = cashedOut.add(amt, MATH);
                    }
                }
            }
            BigDecimal navValue = nav.getAccumulatedNav();
            if (navValue == null || navValue.signum() <= 0) {
                continue;
            }
            TrailingStopEngine.Position position = new TrailingStopEngine.Position(
                    holdingShares, totalAcquired, totalSold, invested, cashedOut);
            state = TrailingStopEngine.evaluate(state, position, navValue, params).newState();
        }

        // 当日持仓快照:以最新已确认交易后的实际持仓为准(回放值与 FundPositionService 应一致)
        BigDecimal todayHolding = fundPositionService.getHoldingShares(fund.getId());
        if (todayHolding.signum() > 0) {
            holdingShares = todayHolding; // 以权威持仓为准
        }
        return new Rebuilt(state, new TrailingStopEngine.Position(
                holdingShares, totalAcquired, totalSold, invested, cashedOut));
    }

    private static boolean isBuy(FundTransactionSource s) {
        return s == FundTransactionSource.INCREASE || s == FundTransactionSource.TRANSFER_IN
                || s == FundTransactionSource.INVEST;
    }

    /** 回放产物:轮内状态 + 持仓快照。 */
    public record Rebuilt(TrailingStopEngine.State state, TrailingStopEngine.Position position) {
    }
}