package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.fund.client.FundFeeSnapshot;
import com.fundpilot.backend.fund.client.RedemptionTier;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundLotEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.repository.FundLotRedemptionRepository;
import com.fundpilot.backend.fund.repository.FundLotRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

/**
 * {@link TransactionConfirmSupport} 单测:验证申购费扣除 + FIFO 赎回费匹配 + 费率缺失降级 + 卖超抛异常。
 * <p>用 mock 隔离 FundFeeService/FundLotRepository/FundPositionService,聚焦扣费公式 + FIFO 遍历逻辑。
 */
@ExtendWith(MockitoExtension.class)
class TransactionConfirmSupportTest {

    private static final MathContext MATH = MathContext.DECIMAL64;

    @Mock private FundFeeService fundFeeService;
    @Mock private FundLotRepository fundLotRepository;
    @Mock private FundLotRedemptionRepository fundLotRedemptionRepository;
    @Mock private FundPositionService fundPositionService;
    @Mock private FundRepository fundRepository;

    @InjectMocks private TransactionConfirmSupport support;

    private FundEntity fund;

    @BeforeEach
    void setUp() {
        fund = new FundEntity();
        fund.setId(1L);
        fund.setFundCode("001071");
    }

    // ===== 申购费扣除 =====

    @Test
    void onBuyConfirmed_有优惠费率_扣费后算shares() {
        // amount=1000, discountRate=0.0015(0.15%), nav=1.5
        // fee=1.50, netAmount=998.50, shares=998.50/1.5=665.6667
        when(fundFeeService.getFeeByFundId(1L)).thenReturn(
                new FundFeeSnapshot(new BigDecimal("0.0015"), List.of(), null));
        when(fundPositionService.getHoldingShares(1L)).thenReturn(new BigDecimal("665.6667"));

        FundTransactionEntity tx = buyTx(new BigDecimal("1000"), Instant.parse("2026-07-05T00:00:00Z"));
        support.onBuyConfirmed(tx, new BigDecimal("1.5"));

        assertThat(tx.getFee()).isEqualByComparingTo(new BigDecimal("1.500"));
        assertThat(tx.getFeeRate()).isEqualByComparingTo(new BigDecimal("0.0015"));
        // shares = 998.5 / 1.5 (DECIMAL64)
        assertThat(tx.getShares()).isEqualByComparingTo(
                new BigDecimal("998.5").divide(new BigDecimal("1.5"), MATH));
        verify(fundLotRepository).save(any(FundLotEntity.class));
    }

    @Test
    void onBuyConfirmed_费率缺失_降级不扣费() {
        // fee 缺失 → fee=0, shares=amount/nav(原逻辑)
        when(fundFeeService.getFeeByFundId(1L)).thenReturn(FundFeeSnapshot.empty());
        when(fundPositionService.getHoldingShares(1L)).thenReturn(new BigDecimal("666.6667"));

        FundTransactionEntity tx = buyTx(new BigDecimal("1000"), Instant.parse("2026-07-05T00:00:00Z"));
        support.onBuyConfirmed(tx, new BigDecimal("1.5"));

        assertThat(tx.getFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(tx.getFeeRate()).isNull();
        // shares = 1000 / 1.5 (DECIMAL64,费率缺失降级不扣费)
        assertThat(tx.getShares()).isEqualByComparingTo(
                new BigDecimal("1000").divide(new BigDecimal("1.5"), MATH));
    }

    // ===== FIFO 赎回费匹配 =====

    @Test
    void onSellConfirmed_单lot_持有5天_赎回费150() {
        // 1 lot: 100 shares, acquired 5 days ago, nav=1.6, rate=0.015(1.5%, <7天)
        // fee = 100 × 1.6 × 0.015 = 2.40, amount = 100 × 1.6 − 2.40 = 157.60
        List<RedemptionTier> ladder = List.of(
                new RedemptionTier(7, new BigDecimal("0.015")),
                new RedemptionTier(null, BigDecimal.ZERO));
        when(fundFeeService.getFeeByFundId(1L)).thenReturn(
                new FundFeeSnapshot(null, ladder, null));

        FundLotEntity lot = lot(100, Instant.parse("2026-06-30T00:00:00Z"));
        when(fundLotRepository.findOpenLotsByFundIdOrderByAcquireDateAsc(1L)).thenReturn(List.of(lot));

        FundTransactionEntity tx = sellTx(new BigDecimal("100"), Instant.parse("2026-07-05T00:00:00Z"));
        support.onSellConfirmed(tx, new BigDecimal("1.6"));

        assertThat(tx.getFee()).isEqualByComparingTo(new BigDecimal("2.400"));
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("157.6000"));
        assertThat(lot.getRemainingShares()).isEqualByComparingTo(BigDecimal.ZERO);
        verify(fundLotRedemptionRepository).saveAll(anyList());
    }

    @Test
    void onSellConfirmed_跨多lot_FIFO按持有期分档() {
        // lot A: 150 shares, 10 天(0.75%), lot B: 100 shares, 100 天(0.5%)
        // 卖 200 份:消耗 A 150 + B 50
        // fee = 150×1.6×0.0075 + 50×1.6×0.005 = 1.80 + 0.40 = 2.20
        // amount = 200×1.6 − 2.20 = 317.80
        List<RedemptionTier> ladder = List.of(
                new RedemptionTier(7, new BigDecimal("0.015")),
                new RedemptionTier(30, new BigDecimal("0.0075")),
                new RedemptionTier(365, new BigDecimal("0.005")),
                new RedemptionTier(null, BigDecimal.ZERO));
        when(fundFeeService.getFeeByFundId(1L)).thenReturn(
                new FundFeeSnapshot(null, ladder, null));

        FundLotEntity lotA = lot(150, Instant.parse("2026-06-25T00:00:00Z")); // 10 天
        FundLotEntity lotB = lot(100, Instant.parse("2026-03-27T00:00:00Z")); // 100 天
        when(fundLotRepository.findOpenLotsByFundIdOrderByAcquireDateAsc(1L))
                .thenReturn(List.of(lotA, lotB));

        FundTransactionEntity tx = sellTx(new BigDecimal("200"), Instant.parse("2026-07-05T00:00:00Z"));
        support.onSellConfirmed(tx, new BigDecimal("1.6"));

        assertThat(tx.getFee()).isEqualByComparingTo(new BigDecimal("2.2000"));
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("317.8000"));
        assertThat(lotA.getRemainingShares()).isEqualByComparingTo(BigDecimal.ZERO); // A 全消耗
        assertThat(lotB.getRemainingShares()).isEqualByComparingTo(new BigDecimal("50")); // B 消耗 50
    }

    @Test
    void onSellConfirmed_费率缺失_降级不扣赎回费() {
        when(fundFeeService.getFeeByFundId(1L)).thenReturn(FundFeeSnapshot.empty());

        FundLotEntity lot = lot(100, Instant.parse("2026-06-30T00:00:00Z"));
        when(fundLotRepository.findOpenLotsByFundIdOrderByAcquireDateAsc(1L)).thenReturn(List.of(lot));

        FundTransactionEntity tx = sellTx(new BigDecimal("100"), Instant.parse("2026-07-05T00:00:00Z"));
        support.onSellConfirmed(tx, new BigDecimal("1.6"));

        assertThat(tx.getFee()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(tx.getAmount()).isEqualByComparingTo(new BigDecimal("160.0000"));
    }

    @Test
    void onSellConfirmed_卖超抛INSUFFICIENT_LOTS() {
        when(fundFeeService.getFeeByFundId(1L)).thenReturn(FundFeeSnapshot.empty());
        FundLotEntity lot = lot(50, Instant.parse("2026-06-30T00:00:00Z"));
        when(fundLotRepository.findOpenLotsByFundIdOrderByAcquireDateAsc(1L)).thenReturn(List.of(lot));

        FundTransactionEntity tx = sellTx(new BigDecimal("100"), Instant.parse("2026-07-05T00:00:00Z"));
        assertThatThrownBy(() -> support.onSellConfirmed(tx, new BigDecimal("1.6")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("可用 lot 份额不足");
    }

    @Test
    void onSellConfirmed_事实持仓含调增份额_部分lot不足按零费率降级() {
        when(fundFeeService.getFeeByFundId(1L)).thenReturn(
                new FundFeeSnapshot(null,
                        List.of(new RedemptionTier(null, new BigDecimal("0.01"))), null));
        FundLotEntity lot = lot(50, Instant.parse("2026-06-30T00:00:00Z"));
        when(fundLotRepository.findOpenLotsByFundIdOrderByAcquireDateAsc(1L)).thenReturn(List.of(lot));
        // 调用时卖出交易已标 CONFIRMED，因此事实持仓为卖出后的 0；卖出前为 100，超出 lot 的 50 来自 ADJUST_IN。
        when(fundPositionService.getHoldingShares(1L)).thenReturn(BigDecimal.ZERO);

        FundTransactionEntity tx = sellTx(new BigDecimal("100"), Instant.parse("2026-07-05T00:00:00Z"));
        support.onSellConfirmed(tx, new BigDecimal("1.6"));

        assertThat(tx.getFee()).isEqualByComparingTo("0.800");
        assertThat(tx.getAmount()).isEqualByComparingTo("159.200");
        assertThat(lot.getRemainingShares()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void onAdjustConfirmed_调减按FIFO缩减lot且不生成赎回明细() {
        FundLotEntity first = lot(80, Instant.parse("2026-05-01T00:00:00Z"));
        FundLotEntity second = lot(50, Instant.parse("2026-06-01T00:00:00Z"));
        when(fundLotRepository.findOpenLotsByFundIdOrderByAcquireDateAsc(1L))
                .thenReturn(List.of(first, second));
        FundTransactionEntity tx = sellTx(new BigDecimal("100"), Instant.parse("2026-07-05T00:00:00Z"));
        tx.setSource(FundTransactionSource.ADJUST_OUT);

        support.onAdjustConfirmed(tx);

        assertThat(first.getRemainingShares()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(second.getRemainingShares()).isEqualByComparingTo("30");
        verify(fundLotRepository).saveAll(List.of(first, second));
        verifyNoInteractions(fundLotRedemptionRepository);
    }

    @Test
    void lookupRedemptionRate_持有7天命中第二档() {
        List<RedemptionTier> ladder = List.of(
                new RedemptionTier(7, new BigDecimal("0.015")),
                new RedemptionTier(30, new BigDecimal("0.0075")),
                new RedemptionTier(null, BigDecimal.ZERO));
        // holdingDays=7 → 7 < 7 false, 7 < 30 true → 0.0075
        assertThat(TransactionConfirmSupport.lookupRedemptionRate(ladder, 7))
                .isEqualByComparingTo(new BigDecimal("0.0075"));
        // holdingDays=6 → 6 < 7 true → 0.015
        assertThat(TransactionConfirmSupport.lookupRedemptionRate(ladder, 6))
                .isEqualByComparingTo(new BigDecimal("0.015"));
        // holdingDays=800 → 最后一档 null → 0
        assertThat(TransactionConfirmSupport.lookupRedemptionRate(ladder, 800))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void lookupRedemptionRate_空阶梯返零() {
        assertThat(TransactionConfirmSupport.lookupRedemptionRate(List.of(), 100))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ===== helpers =====

    private FundTransactionEntity buyTx(BigDecimal amount, Instant confirmTime) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setId(10L);
        tx.setFundEntity(fund);
        tx.setSource(FundTransactionSource.INCREASE);
        tx.setAmount(amount);
        tx.setConfirmTime(confirmTime);
        return tx;
    }

    private FundTransactionEntity sellTx(BigDecimal shares, Instant confirmTime) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setId(20L);
        tx.setFundEntity(fund);
        tx.setSource(FundTransactionSource.DECREASE);
        tx.setShares(shares);
        tx.setConfirmTime(confirmTime);
        return tx;
    }

    private FundLotEntity lot(int shares, Instant acquireDate) {
        FundLotEntity lot = new FundLotEntity();
        lot.setId(System.nanoTime());
        lot.setFundEntity(fund);
        lot.setAcquireTxId(10L);
        lot.setAcquireDate(acquireDate);
        lot.setAcquireShares(new BigDecimal(shares));
        lot.setRemainingShares(new BigDecimal(shares));
        lot.setAcquireCostPerShare(new BigDecimal("1.5"));
        return lot;
    }

    @SuppressWarnings("unchecked")
    private static <T> List<T> anyList() {
        return org.mockito.ArgumentMatchers.anyList();
    }
}
