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
    private final TransactionConfirmSupport transactionConfirmSupport;

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

    /**
     * 尝试确认单条交易;当日无 NavHistory 返回 false 不报错。
     *
     * <p>基金转换(task 07-08):转出腿确认后(得 amount = 转出净金额),回填转入腿 amount 并递归确认。
     * 同批内若转入腿先被循环到(amount 仍空),走"amount 为空跳过"分支,待转出腿处理时回填并递归续确认。
     * 守卫 {@code status != PENDING} 防止递归+外层循环对同一腿重复确认。
     */
    private boolean tryConfirm(FundTransactionEntity tx, Instant dayStart, Instant dayEnd) {
        if (tx.getStatus() != FundTransactionStatus.PENDING) {
            return false;  // 同批内已被递归确认(转换转入腿),跳过
        }
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
            }
            case DECREASE, TRANSFER_OUT -> {
                if (tx.getShares() == null) {
                    log.warn("DECREASE 交易 shares 为空跳过 tx_id={}", tx.getId());
                    return false;
                }
            }
            // ADJUST 录入即 CONFIRMED,不会被 findByStatus(PENDING) 查到;覆盖枚举防 switch 漏分支
            case ADJUST_IN, ADJUST_OUT -> {
            }
        }
        tx.setNav(navValue);
        tx.setConfirmTime(Instant.now());
        tx.setStatus(FundTransactionStatus.CONFIRMED);
        // 扣手续费 + 建/消耗 lot + 更新成本单价(统一走 TransactionConfirmSupport)
        switch (source) {
            case INCREASE, TRANSFER_IN, INVEST -> transactionConfirmSupport.onBuyConfirmed(tx, navValue);
            case DECREASE, TRANSFER_OUT -> transactionConfirmSupport.onSellConfirmed(tx, navValue);
            // ADJUST 不建 lot/不算费(录入即 CONFIRMED,不触达批量确认)
            case ADJUST_IN, ADJUST_OUT -> {
            }
        }
        fundTransactionRepository.save(tx);

        // 转换:转出腿确认后回填转入腿 amount 并递归确认(B 当日净值可得则即时确认,否则留 PENDING 次日续)
        if (source == FundTransactionSource.TRANSFER_OUT) {
            FundTransactionEntity related = tx.getRelatedFundTransactionEntity();
            if (related != null && related.getSource() == FundTransactionSource.TRANSFER_IN
                    && related.getStatus() == FundTransactionStatus.PENDING) {
                related.setAmount(tx.getAmount());  // 转出净金额 = 转入本金
                tryConfirm(related, dayStart, dayEnd);
            }
        }
        return true;
    }

    // updateCostPerShare 已移至 TransactionConfirmSupport(统一扣费 + lot + 成本更新)
}
