package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundCategory;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Comparator;
import java.util.List;

/**
 * 定投扣款服务(issue #61):每月最后交易日为所有 HOLDING 基金产生 INVEST 交易(记账型,不接实盘)。
 * <p>逐只基金在独立事务({@link DcaInvestor})中扣款,单只失败不影响其他基金。
 * 金额 = {@code FundEntity.dcaAmount};份额留空,由 NavConfirmJob 按当日净值回填,确认时加权更新 costPerShare。
 *
 * <h3>行业定投暂停/恢复</h3>
 * 行业基金止盈后暂停定投(下跌途中不加仓),收益率跌回 10% 以下(市场冷却)才恢复;
 * 宽基基金一直定投不停(ADR-0015「定投暂停与恢复」)。
 * <p>暂停判定基于「最近一次 DECREASE(止盈卖出)交易」+ 当前收益率 >10%(持久语义,不依赖每日覆盖的 SignalLog)。
 */
@Service
@RequiredArgsConstructor
public class DcaService {

    private static final Logger log = LoggerFactory.getLogger(DcaService.class);
    private static final MathContext MATH = MathContext.DECIMAL64;
    /** 行业止盈后暂停定投,收益率跌回此值(10%)以下才恢复。 */
    private static final BigDecimal DCA_RESUME_YIELD = new BigDecimal("0.10");

    private final FundRepository fundRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final FundPositionService fundPositionService;
    private final DcaInvestor dcaInvestor;

    /**
     * 为所有 HOLDING 基金执行当月定投扣款。单只基金异常不影响其他基金(独立事务)。
     *
     * @return 产生的 INVEST 交易数
     */
    public int investMonthly() {
        List<FundEntity> holdingFunds = fundRepository.findByStatus(FundStatus.HOLDING);
        int count = 0;
        for (FundEntity fund : holdingFunds) {
            try {
                if (!shouldSkip(fund) && dcaInvestor.invest(fund)) {
                    count++;
                }
            } catch (RuntimeException ex) {
                log.error("定投扣款失败 fund_id={}: {}", fund.getId(), ex.getMessage(), ex);
            }
        }
        log.info("定投扣款结束 produced={} of holding={}", count, holdingFunds.size());
        return count;
    }

    /** 是否应跳过该基金(无金额 / 行业暂停期)。 */
    private boolean shouldSkip(FundEntity fund) {
        if (fund.getDcaAmount() == null || fund.getDcaAmount().signum() <= 0) {
            return true;
        }
        if (isSectorDcaPaused(fund)) {
            log.debug("行业止盈暂停定投 fund_id={}", fund.getId());
            return true;
        }
        return false;
    }

    /**
     * 行业基金止盈后暂停判定:存在最近一次 DECREASE(止盈卖出)CONFIRMED 交易 且 当前收益率 >10% → 暂停。
     * 宽基恒不暂停。收益率 = (持仓市值 − 累计投入) / 累计投入。
     */
    private boolean isSectorDcaPaused(FundEntity fund) {
        if (fund.getFundCategory() != FundCategory.SECTOR) {
            return false;
        }
        boolean hasRecentSell = fundTransactionRepository
                .findByFundEntity_IdAndStatus(fund.getId(), FundTransactionStatus.CONFIRMED).stream()
                .filter(tx -> tx.getSource() == FundTransactionSource.DECREASE
                        || tx.getSource() == FundTransactionSource.TRANSFER_OUT)
                .anyMatch(tx -> true);
        if (!hasRecentSell) {
            return false;
        }
        BigDecimal yield = currentYield(fund);
        return yield != null && yield.compareTo(DCA_RESUME_YIELD) > 0;
    }

    /** 当前收益率 = (持仓市值 − 累计投入) / 累计投入;无净值或无投入返 null。 */
    private BigDecimal currentYield(FundEntity fund) {
        BigDecimal invested = sumInvested(fund.getId());
        if (invested == null || invested.signum() <= 0) {
            return null;
        }
        var latestTwo = fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(fund.getId());
        if (latestTwo.isEmpty() || latestTwo.get(0).getAccumulatedNav() == null) {
            return null;
        }
        BigDecimal nav = latestTwo.get(0).getAccumulatedNav();
        if (nav.signum() <= 0) {
            return null;
        }
        BigDecimal shares = fundPositionService.getHoldingShares(fund.getId());
        BigDecimal mv = shares.multiply(nav, MATH);
        return mv.subtract(invested).divide(invested, MATH);
    }

    /** 累计投入 = 历次买入(INCREASE/TRANSFER_IN/INVEST)CONFIRMED 金额之和。 */
    private BigDecimal sumInvested(Long fundId) {
        return fundTransactionRepository.findByFundEntity_IdAndStatus(fundId, FundTransactionStatus.CONFIRMED).stream()
                .filter(tx -> {
                    var s = tx.getSource();
                    return s == FundTransactionSource.INCREASE
                            || s == FundTransactionSource.TRANSFER_IN
                            || s == FundTransactionSource.INVEST;
                })
                .map(tx -> tx.getAmount() == null ? BigDecimal.ZERO : tx.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}