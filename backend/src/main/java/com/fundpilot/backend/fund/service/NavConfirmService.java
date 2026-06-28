package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * 净值确认服务(issue #15):每晚净值公布后回填当天 PENDING 交易的另一侧 + nav + confirmTime,转 CONFIRMED。
 *
 * <h3>流程</h3>
 * <ol>
 *   <li>查所有 status=PENDING 的交易</li>
 *   <li>每条:查 fund 当日(UTC 0点起 24 小时区间)NavHistory 行;无则跳过(基金公司未公布净值的边缘情况)</li>
 *   <li>有则:INCREASE→shares=amount/nav;DECREASE→amount=shares×nav;填 nav/confirmTime=now/status=CONFIRMED</li>
 *   <li>转账两腿(TRANSFER_IN/TRANSFER_OUT)按各自方向回填(direction 同 INCREASE/DECREASE)</li>
 * </ol>
 *
 * <h3>为什么用 accumulatedNav 而非 nav</h3>
 * 累计净值已含分红再投资,份额/金额计算应基于累计净值(ADR-0001:峰值用 accumulatedNav,口径一致)。
 *
 * <h3>costPerShare 加权更新(ADR-0013)</h3>
 * INCREASE/TRANSFER_IN/INVEST 确认后同一事务内加权更新 FundEntity.costPerShare。
 */
@Service
@RequiredArgsConstructor
public class NavConfirmService {

    private static final Logger log = LoggerFactory.getLogger(NavConfirmService.class);
    private static final MathContext MATH = MathContext.DECIMAL64;

    private final FundTransactionRepository fundTransactionRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final FundRepository fundRepository;
    private final FundPositionService fundPositionService;

    /**
     * 回填指定日期的 PENDING 交易。null 时用今天 UTC。
     * @return 本次确认的交易条数
     */
    @Transactional
    public int confirmPendingTransactions(Instant date) {
        Instant dayStart = date != null ? date : Instant.now();
        Instant dayEnd = dayStart.plus(1, ChronoUnit.DAYS);
        List<FundTransactionEntity> pendings = fundTransactionRepository.findByStatus(FundTransactionStatus.PENDING);
        int confirmed = 0;
        for (FundTransactionEntity tx : pendings) {
            if (tryConfirm(tx, dayStart, dayEnd)) {
                confirmed++;
            }
        }
        log.info("净值确认完成 date={} pending={} confirmed={}", dayStart, pendings.size(), confirmed);
        return confirmed;
    }

    /** 尝试确认单条交易;当日无 NavHistory 返回 false 不报错。 */
    private boolean tryConfirm(FundTransactionEntity tx, Instant dayStart, Instant dayEnd) {
        Long fundId = tx.getFundEntity().getId();
        FundNavHistoryEntity nav = fundNavHistoryRepository
                .findByFundEntity_IdAndNavDateBetween(fundId, dayStart, dayEnd).stream()
                .findFirst().orElse(null);
        if (nav == null || nav.getAccumulatedNav() == null || nav.getAccumulatedNav().signum() <= 0) {
            return false; // 当日无净值,保留 PENDING 等次日 job
        }
        BigDecimal navValue = nav.getAccumulatedNav();
        FundTransactionSource source = tx.getSource();
        switch (source) {
            case INCREASE, TRANSFER_IN, INVEST -> {
                if (tx.getAmount() == null) {
                    log.warn("INCREASE 交易 amount 为空跳过 tx_id={}", tx.getId());
                    return false;
                }
                tx.setShares(tx.getAmount().divide(navValue, MATH));
            }
            case DECREASE, TRANSFER_OUT -> {
                if (tx.getShares() == null) {
                    log.warn("DECREASE 交易 shares 为空跳过 tx_id={}", tx.getId());
                    return false;
                }
                tx.setAmount(tx.getShares().multiply(navValue, MATH));
            }
        }
        tx.setNav(navValue);
        tx.setConfirmTime(Instant.now());
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        fundTransactionRepository.save(tx);

        // ADR-0013:买入类交易确认后加权更新 costPerShare
        updateCostPerShare(tx, source);
        return true;
    }

    /**
     * INCREASE/TRANSFER_IN/INVEST 确认后加权更新 FundEntity.costPerShare。
     * 卖出类不触发。
     */
    private void updateCostPerShare(FundTransactionEntity tx, FundTransactionSource source) {
        if (source != FundTransactionSource.INCREASE
                && source != FundTransactionSource.TRANSFER_IN
                && source != FundTransactionSource.INVEST) {
            return;
        }
        Long fundId = tx.getFundEntity().getId();
        BigDecimal totalAfter = fundPositionService.getHoldingShares(fundId);
        BigDecimal oldShares = totalAfter.subtract(tx.getShares());
        BigDecimal oldCostPerShare = tx.getFundEntity().getCostPerShare();

        BigDecimal newCostPerShare;
        if (oldCostPerShare == null || oldShares.signum() <= 0) {
            newCostPerShare = tx.getAmount().divide(tx.getShares(), MATH);
        } else {
            BigDecimal numerator = oldCostPerShare.multiply(oldShares).add(tx.getAmount());
            BigDecimal denominator = oldShares.add(tx.getShares());
            newCostPerShare = numerator.divide(denominator, MATH);
        }

        FundEntity fund = tx.getFundEntity();
        fund.setCostPerShare(newCostPerShare);
        fundRepository.save(fund);
        log.info("costPerShare 加权更新 fund={} oldShares={} oldCost={} newShares={} amount={} newCost={}",
                fundId, oldShares, oldCostPerShare, tx.getShares(), tx.getAmount(), newCostPerShare);
    }
}
