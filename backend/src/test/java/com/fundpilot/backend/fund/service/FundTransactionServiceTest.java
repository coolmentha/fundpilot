package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.controller.FundTransactionView;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * issue #18 交易流水查询集成测试(CONTEXT.md「交易合并到基金详情」)。
 * <p>查某基金全部交易,按 createdDate 倒序,转 {@link FundTransactionView}。
 */
class FundTransactionServiceTest extends AbstractIntegrationTest {

    @Autowired FundTransactionService fundTransactionService;
    @Autowired FundRepository fundRepository;
    @Autowired FundTransactionRepository fundTransactionRepository;

    @Test
    @Transactional
    void listByFund_按创建时间倒序返回且转View() throws InterruptedException {
        FundEntity fund = persistFund();
        persistTx(fund, FundTransactionSource.INCREASE, "1000");
        Thread.sleep(5);
        persistTx(fund, FundTransactionSource.DECREASE, "500");
        Thread.sleep(5);
        persistTx(fund, FundTransactionSource.INVEST, "2000");
        fundTransactionRepository.flush();

        List<FundTransactionView> rows = fundTransactionService.listByFund(fund.getId());

        assertThat(rows).hasSize(3);
        // 按 createdDate 倒序(最新交易在前)
        assertThat(rows).extracting(FundTransactionView::createdDate)
                .isSortedAccordingTo(Comparator.reverseOrder());
        // 全部属于该基金
        assertThat(rows).extracting(FundTransactionView::fundId).containsOnly(fund.getId());
        // View 字段映射正确
        assertThat(rows).extracting(FundTransactionView::source)
                .containsExactlyInAnyOrder(
                        FundTransactionSource.INCREASE, FundTransactionSource.DECREASE, FundTransactionSource.INVEST);
        assertThat(rows).extracting(FundTransactionView::status)
                .containsOnly(FundTransactionStatus.PENDING);
    }

    @Test
    @Transactional
    void listByFund_无交易返回空列表() {
        FundEntity fund = persistFund();

        List<FundTransactionView> rows = fundTransactionService.listByFund(fund.getId());

        assertThat(rows).isEmpty();
    }

    private FundEntity persistFund() {
        FundEntity fund = new FundEntity();
        fund.setFundCode("510300");
        fund.setFundName("沪深300ETF");
        fund.setStatus(FundStatus.HOLDING);
        return fundRepository.save(fund);
    }

    private FundTransactionEntity persistTx(FundEntity fund, FundTransactionSource source, String amount) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(source);
        tx.setStatus(FundTransactionStatus.PENDING);
        tx.setAmount(new BigDecimal(amount));
        tx.setShares(new BigDecimal("100"));
        tx.setNav(new BigDecimal("1.20"));
        return fundTransactionRepository.save(tx);
    }
}
