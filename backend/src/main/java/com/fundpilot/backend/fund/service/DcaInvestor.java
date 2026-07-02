package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 定投扣款单只基金执行器(issue #61):独立 REQUIRES_NEW 事务,保证单只基金扣款失败不影响其他基金。
 * <p>金额 = {@code FundEntity.dcaAmount};份额留空由 NavConfirmJob 按当日净值回填并加权更新 costPerShare。
 */
@Service
@RequiredArgsConstructor
public class DcaInvestor {

    private static final Logger log = LoggerFactory.getLogger(DcaInvestor.class);

    private final FundTransactionRepository fundTransactionRepository;

    /**
     * 在独立事务中为单只基金产生 INVEST 交易(PENDING)。调用方传已校验的 fund。
     *
     * @return true 已写交易;false 跳过(无金额)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean invest(FundEntity fund) {
        BigDecimal dcaAmount = fund.getDcaAmount();
        if (dcaAmount == null || dcaAmount.signum() <= 0) {
            return false;
        }
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(FundTransactionSource.INVEST);
        tx.setAmount(dcaAmount);
        tx.setShares(null); // 由 NavConfirmJob 按当日净值回填,确认时加权更新 costPerShare
        tx.setNav(null);
        tx.setStatus(FundTransactionStatus.PENDING);
        tx.setSignalLogEntity(null); // 定投不经信号引擎
        fundTransactionRepository.save(tx);
        log.info("定投扣款 fund_id={} amount={}", fund.getId(), dcaAmount);
        return true;
    }
}