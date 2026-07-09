package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundTransactionView;
import com.fundpilot.backend.fund.controller.ManualTransactionRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    @Transactional
    void createManual_买入类写amount_份额null_状态PENDING_无关联信号() {
        FundEntity fund = persistFund();
        ManualTransactionRequest req = new ManualTransactionRequest(
                FundTransactionSource.INCREASE, new BigDecimal("1000"), null, null);

        FundTransactionView view = fundTransactionService.createManual(fund.getId(), req);

        assertThat(view.source()).isEqualTo(FundTransactionSource.INCREASE);
        assertThat(view.amount()).isEqualByComparingTo("1000");
        assertThat(view.shares()).isNull();
        assertThat(view.status()).isEqualTo(FundTransactionStatus.PENDING);
        assertThat(view.signalLogId()).isNull(); // 手动交易不关联信号
    }

    @Test
    @Transactional
    void createManual_卖出类写shares_金额null() {
        FundEntity fund = persistFund();
        ManualTransactionRequest req = new ManualTransactionRequest(
                FundTransactionSource.DECREASE, null, new BigDecimal("500"), null);

        FundTransactionView view = fundTransactionService.createManual(fund.getId(), req);

        assertThat(view.source()).isEqualTo(FundTransactionSource.DECREASE);
        assertThat(view.shares()).isEqualByComparingTo("500");
        assertThat(view.amount()).isNull();
        assertThat(view.status()).isEqualTo(FundTransactionStatus.PENDING);
        assertThat(view.signalLogId()).isNull();
    }

    @Test
    @Transactional
    void createManual_买入类amount为null_抛异常() {
        FundEntity fund = persistFund();
        ManualTransactionRequest req = new ManualTransactionRequest(
                FundTransactionSource.INCREASE, null, null, null);

        assertThatThrownBy(() -> fundTransactionService.createManual(fund.getId(), req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED.name());
    }

    @Test
    @Transactional
    void createManual_卖出类shares为null_抛异常() {
        FundEntity fund = persistFund();
        ManualTransactionRequest req = new ManualTransactionRequest(
                FundTransactionSource.DECREASE, null, null, null);

        assertThatThrownBy(() -> fundTransactionService.createManual(fund.getId(), req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED.name());
    }

    @Test
    @Transactional
    void createManual_定投转入按买入方向_转出按卖出方向() {
        FundEntity fund = persistFund();
        // INVEST 定投:买入方向,写 amount
        FundTransactionView invest = fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.INVEST, new BigDecimal("500"), null, null));
        assertThat(invest.amount()).isEqualByComparingTo("500");
        assertThat(invest.shares()).isNull();
        // TRANSFER_IN 转入:买入方向
        FundTransactionView tin = fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.TRANSFER_IN, new BigDecimal("800"), null, null));
        assertThat(tin.amount()).isEqualByComparingTo("800");
        // TRANSFER_OUT 转出:卖出方向,写 shares
        FundTransactionView tout = fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.TRANSFER_OUT, null, new BigDecimal("300"), null));
        assertThat(tout.shares()).isEqualByComparingTo("300");
        assertThat(tout.amount()).isNull();
    }

    @Test
    @Transactional
    void createManual_转换模式_targetFundId非空_建两条互指交易() {
        // task 07-08:TRANSFER_OUT + targetFundId -> 转出(A)+转入(B)两条互指,转入 amount/shares 均空待确认回填
        FundEntity fundA = persistFund();
        FundEntity fundB = new FundEntity();
        fundB.setFundCode("161725");
        fundB.setFundName("招商白酒");
        fundB.setStatus(FundStatus.HOLDING);
        fundRepository.save(fundB);

        ManualTransactionRequest req = new ManualTransactionRequest(
                FundTransactionSource.TRANSFER_OUT, null, new BigDecimal("300"), fundB.getId());

        FundTransactionView view = fundTransactionService.createManual(fundA.getId(), req);

        // 返回转出腿
        assertThat(view.source()).isEqualTo(FundTransactionSource.TRANSFER_OUT);
        assertThat(view.shares()).isEqualByComparingTo("300");
        assertThat(view.amount()).isNull();
        assertThat(view.status()).isEqualTo(FundTransactionStatus.PENDING);
        // 转入腿存在且互指
        FundTransactionEntity txOut = fundTransactionRepository.findById(view.id()).orElseThrow();
        FundTransactionEntity txIn = txOut.getRelatedFundTransactionEntity();
        assertThat(txIn).isNotNull();
        assertThat(txIn.getSource()).isEqualTo(FundTransactionSource.TRANSFER_IN);
        assertThat(txIn.getFundEntity().getId()).isEqualTo(fundB.getId());
        assertThat(txIn.getAmount()).isNull();   // 待确认回填
        assertThat(txIn.getShares()).isNull();
        assertThat(txIn.getStatus()).isEqualTo(FundTransactionStatus.PENDING);
        // 双向互指
        assertThat(txIn.getRelatedFundTransactionEntity().getId()).isEqualTo(txOut.getId());
    }

    @Test
    @Transactional
    void createManual_转换模式_targetFundId等于自身_抛异常() {
        FundEntity fund = persistFund();
        ManualTransactionRequest req = new ManualTransactionRequest(
                FundTransactionSource.TRANSFER_OUT, null, new BigDecimal("300"), fund.getId());

        assertThatThrownBy(() -> fundTransactionService.createManual(fund.getId(), req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED.name());
    }

    @Test
    @Transactional
    void createManual_转换模式_targetFundId不存在_抛异常() {
        FundEntity fund = persistFund();
        ManualTransactionRequest req = new ManualTransactionRequest(
                FundTransactionSource.TRANSFER_OUT, null, new BigDecimal("300"), 999999L);

        assertThatThrownBy(() -> fundTransactionService.createManual(fund.getId(), req))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.FUND_NOT_FOUND.name());
    }

    @Test
    @Transactional
    void createManual_纯转出_targetFundId为空_仅单条记录不互指() {
        // 兼容:targetFundId 为空走原纯转出逻辑,无 relatedTransaction
        FundEntity fund = persistFund();
        ManualTransactionRequest req = new ManualTransactionRequest(
                FundTransactionSource.TRANSFER_OUT, null, new BigDecimal("300"), null);

        FundTransactionView view = fundTransactionService.createManual(fund.getId(), req);

        FundTransactionEntity tx = fundTransactionRepository.findById(view.id()).orElseThrow();
        assertThat(tx.getRelatedFundTransactionEntity()).isNull();
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
