package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PendingTransactionCompensationService {

    private static final Logger log = LoggerFactory.getLogger(PendingTransactionCompensationService.class);

    private final FundTransactionRepository fundTransactionRepository;
    private final NavConfirmService navConfirmService;

    public int compensateAll() {
        Set<Long> fundIds = new LinkedHashSet<>();
        for (FundTransactionEntity transaction
                : fundTransactionRepository.findByStatus(FundTransactionStatus.PENDING)) {
            if (transaction.getFundEntity() != null && transaction.getFundEntity().getId() != null) {
                fundIds.add(transaction.getFundEntity().getId());
            }
        }
        int confirmed = 0;
        for (Long fundId : fundIds) {
            confirmed += compensateFund(fundId);
        }
        log.info("待确认交易补偿完成 funds={} confirmed={}", fundIds.size(), confirmed);
        return confirmed;
    }

    public int compensateFund(Long fundId) {
        try {
            return navConfirmService.confirmPendingTransactionsForFund(fundId);
        } catch (RuntimeException ex) {
            log.error("待确认交易补偿失败 fund_id={} message={}", fundId, ex.getMessage(), ex);
            return 0;
        }
    }
}
