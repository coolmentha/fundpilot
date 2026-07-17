package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundTransactionView;
import com.fundpilot.backend.fund.controller.ManualTransactionRequest;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundLotEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundLotRedemptionRepository;
import com.fundpilot.backend.fund.repository.FundLotRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
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
    @Autowired FundPositionService fundPositionService;
    @Autowired FundLotRepository fundLotRepository;
    @Autowired FundLotRedemptionRepository fundLotRedemptionRepository;

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
        assertThat(rows).extracting(FundTransactionView::tradeDate)
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
    void listPending_跨基金只返回待处理交易且按交易时间倒序() {
        FundEntity fundA = persistFund();
        FundEntity fundB = new FundEntity();
        fundB.setFundCode("161725");
        fundB.setFundName("招商白酒");
        fundB.setStatus(FundStatus.HOLDING);
        fundRepository.save(fundB);
        FundTransactionEntity older = persistTx(fundA, FundTransactionSource.INCREASE, "1000");
        older.setTradeDate(Instant.parse("2026-07-16T00:00:00Z"));
        FundTransactionEntity newer = persistTx(fundB, FundTransactionSource.DECREASE, "500");
        newer.setTradeDate(Instant.parse("2026-07-17T00:00:00Z"));
        FundTransactionEntity confirmed = persistTx(fundA, FundTransactionSource.INVEST, "200");
        confirmed.setStatus(FundTransactionStatus.CONFIRMED);
        fundTransactionRepository.flush();

        List<FundTransactionView> rows = fundTransactionService.listPending();

        assertThat(rows).extracting(FundTransactionView::id)
                .containsExactly(newer.getId(), older.getId());
        assertThat(rows).extracting(FundTransactionView::status)
                .containsOnly(FundTransactionStatus.PENDING);
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
    void createManual_指定历史交易日_保存为交易发生时间() {
        FundEntity fund = persistFund();
        Instant tradeDate = Instant.parse("2026-07-08T00:00:00Z");

        FundTransactionView view = fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.INCREASE,
                        new BigDecimal("1000"), null, null, tradeDate));

        assertThat(view.tradeDate()).isEqualTo(tradeDate);
    }

    @Test
    @Transactional
    void createManual_未来交易时间_拒绝创建() {
        FundEntity fund = persistFund();

        assertThatThrownBy(() -> fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.INCREASE,
                        new BigDecimal("1000"), null, null, Instant.now().plusSeconds(3600))))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED.name());
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

        Instant tradeDate = Instant.parse("2026-07-08T00:00:00Z");
        ManualTransactionRequest req = new ManualTransactionRequest(
                FundTransactionSource.TRANSFER_OUT, null, new BigDecimal("300"), fundB.getId(), tradeDate);

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
        assertThat(txOut.getTradeDate()).isEqualTo(tradeDate);
        assertThat(txIn.getTradeDate()).isEqualTo(tradeDate);
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

    @Test
    @Transactional
    void createManual_调增录入即CONFIRMED_持仓增加_不建lot不算费() {
        // task 07-09:ADJUST_IN 录入即 CONFIRMED,amount/fee/nav 均空,持仓份额立即增加
        FundEntity fund = persistFund();
        fund.setStatus(FundStatus.PENDING_HOLDING);
        fundRepository.save(fund);
        assertThat(fundPositionService.getHoldingShares(fund.getId())).isEqualByComparingTo("0");

        ManualTransactionRequest req = new ManualTransactionRequest(
                FundTransactionSource.ADJUST_IN, null, new BigDecimal("100"), null);

        FundTransactionView view = fundTransactionService.createManual(fund.getId(), req);

        assertThat(view.source()).isEqualTo(FundTransactionSource.ADJUST_IN);
        assertThat(view.shares()).isEqualByComparingTo("100");
        assertThat(view.amount()).isNull();
        assertThat(view.status()).isEqualTo(FundTransactionStatus.CONFIRMED);
        assertThat(view.confirmTime()).isNotNull();
        // 持仓立即 +100
        assertThat(fundPositionService.getHoldingShares(fund.getId())).isEqualByComparingTo("100");
        assertThat(fundRepository.findById(fund.getId()).orElseThrow().getStatus())
                .isEqualTo(FundStatus.HOLDING);
    }

    @Test
    @Transactional
    void createManual_调减录入即CONFIRMED_持仓减少() {
        FundEntity fund = persistFund();
        // 先调增 200,再调减 50 -> 持仓 150
        fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.ADJUST_IN, null, new BigDecimal("200"), null));
        fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.ADJUST_OUT, null, new BigDecimal("50"), null));

        assertThat(fundPositionService.getHoldingShares(fund.getId())).isEqualByComparingTo("150");
        assertThat(fundRepository.findById(fund.getId()).orElseThrow().getStatus())
                .isEqualTo(FundStatus.HOLDING);
    }

    @Test
    @Transactional
    void createManual_调减至零_状态变为CLEARED() {
        FundEntity fund = persistFund();
        fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.ADJUST_IN, null,
                        new BigDecimal("100"), null));

        fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.ADJUST_OUT, null,
                        new BigDecimal("100"), null));

        assertThat(fundPositionService.getHoldingShares(fund.getId())).isEqualByComparingTo("0");
        assertThat(fundRepository.findById(fund.getId()).orElseThrow().getStatus())
                .isEqualTo(FundStatus.CLEARED);
    }

    @Test
    @Transactional
    void createManual_调减超过事实持仓_拒绝且不写交易() {
        FundEntity fund = persistFund();
        fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.ADJUST_IN, null,
                        new BigDecimal("100"), null));
        long before = fundTransactionRepository.count();

        assertThatThrownBy(() -> fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.ADJUST_OUT, null,
                        new BigDecimal("101"), null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.INSUFFICIENT_HOLDING_SHARES.name());

        assertThat(fundTransactionRepository.count()).isEqualTo(before);
        assertThat(fundPositionService.getHoldingShares(fund.getId())).isEqualByComparingTo("100");
    }

    @Test
    @Transactional
    void createManual_调减同步缩减lot且不生成赎回明细() {
        FundEntity fund = persistFund();
        FundTransactionEntity holding = new FundTransactionEntity();
        holding.setFundEntity(fund);
        holding.setSource(FundTransactionSource.INCREASE);
        holding.setStatus(FundTransactionStatus.CONFIRMED);
        holding.setAmount(new BigDecimal("120"));
        holding.setShares(new BigDecimal("100"));
        holding.setNav(new BigDecimal("1.20"));
        holding.setTradeDate(Instant.parse("2026-06-01T00:00:00Z"));
        holding.setConfirmTime(Instant.parse("2026-06-02T00:00:00Z"));
        fundTransactionRepository.save(holding);
        FundLotEntity lot = new FundLotEntity();
        lot.setFundEntity(fund);
        lot.setAcquireTxId(999L);
        lot.setAcquireDate(Instant.parse("2026-06-01T00:00:00Z"));
        lot.setAcquireShares(new BigDecimal("100"));
        lot.setRemainingShares(new BigDecimal("100"));
        lot.setAcquireCostPerShare(new BigDecimal("1.20"));
        lot = fundLotRepository.save(lot);

        FundTransactionView adjustment = fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.ADJUST_OUT, null,
                        new BigDecimal("40"), null));

        FundLotEntity reloaded = fundLotRepository.findById(lot.getId()).orElseThrow();
        assertThat(reloaded.getRemainingShares()).isEqualByComparingTo("60");
        assertThat(fundLotRedemptionRepository.findBySellTxId(adjustment.id())).isEmpty();
    }

    @Test
    @Transactional
    void createManual_调整份额为空或非正_抛异常() {
        FundEntity fund = persistFund();
        // null 份额
        assertThatThrownBy(() -> fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.ADJUST_IN, null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED.name());
        // 零份额
        assertThatThrownBy(() -> fundTransactionService.createManual(fund.getId(),
                new ManualTransactionRequest(FundTransactionSource.ADJUST_IN, null, BigDecimal.ZERO, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED.name());
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
