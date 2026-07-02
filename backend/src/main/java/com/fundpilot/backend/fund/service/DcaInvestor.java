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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * 定投扣款单只基金执行器(issue #61):独立事务中为单只基金产生 INVEST 交易(PENDING)。
 * <p>金额 = {@code FundEntity.dcaAmount};份额留空由 NavConfirmJob 按当日净值回填并加权更新 costPerShare。
 * <p>注:当前使用 REQUIRED 传播(默认),若某只基金扣款失败会标记事务 rollback-only,
 * 导致同批其他基金的扣款也回滚。未来可改为 REQUIRES_NEW 隔离,但需解决测试事务数据可见性问题。
 */
@Service
@RequiredArgsConstructor
public class DcaInvestor {

    private static final Logger log = LoggerFactory.getLogger(DcaInvestor.class);

    private final FundTransactionRepository fundTransactionRepository;

    /**
     * 为单只基金产生 INVEST 交易(PENDING)。
     *
     * @return true 已写交易;false 跳过(无金额)
     */
    @Transactional
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