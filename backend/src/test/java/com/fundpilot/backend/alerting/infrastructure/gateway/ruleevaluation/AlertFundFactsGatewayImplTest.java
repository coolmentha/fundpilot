package com.fundpilot.backend.alerting.infrastructure.gateway.ruleevaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.accounting.adapter.api.position.PositionApi;
import com.fundpilot.backend.accounting.adapter.api.transaction.TransactionApi;
import com.fundpilot.backend.insights.adapter.api.fundreturn.FundReturnApi;
import com.fundpilot.backend.marketdata.adapter.api.publishednav.PublishedNavApi;
import com.fundpilot.backend.marketdata.adapter.api.tradingcalendar.TradingCalendarApi;
import com.fundpilot.backend.productcatalog.adapter.api.product.FundProductApi;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * 网格基准线的取数口径：只认「已确认 + 有成交净值」的真实买卖流水，且取成交日最晚的一条。
 *
 * <p>这些约束是需求的核心——基准线错了，整条网格提醒的方向就是错的，因此逐条固定下来。
 */
class AlertFundFactsGatewayImplTest {

    private static final Instant NOW = Instant.parse("2026-09-21T06:30:00Z");
    private static final long OWNER = 3L;
    private static final long FUND_ID = 11L;

    private final FundReturnApi fundReturns = mock(FundReturnApi.class);
    private final TradingCalendarApi tradingCalendar = mock(TradingCalendarApi.class);
    private final PositionApi positions = mock(PositionApi.class);
    private final PublishedNavApi navs = mock(PublishedNavApi.class);
    private final FundProductApi products = mock(FundProductApi.class);
    private final TransactionApi transactions = mock(TransactionApi.class);
    private final AlertFundFactsGatewayImpl gateway = new AlertFundFactsGatewayImpl(fundReturns, tradingCalendar,
            positions, navs, products, transactions, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void 基准线取成交日最晚的已确认流水净值() {
        // 故意乱序给出，确保按成交日而不是按列表顺序挑选
        stub(transaction(1L, TransactionApi.Source.INCREASE, TransactionApi.Status.CONFIRMED, "1.2000",
                        "2026-09-10T00:00:00Z", null),
                transaction(2L, TransactionApi.Source.DECREASE, TransactionApi.Status.CONFIRMED, "1.5000",
                        "2026-09-18T00:00:00Z", null),
                transaction(3L, TransactionApi.Source.INVEST, TransactionApi.Status.CONFIRMED, "1.3000",
                        "2026-09-15T00:00:00Z", null));

        var fact = onlyFact();

        assertThat(fact.baselineNav()).isEqualByComparingTo("1.5000");
        assertThat(fact.baselineNavDate()).isEqualTo(Instant.parse("2026-09-18T00:00:00Z"));
    }

    @Test
    void 买入与卖出都算作基准线() {
        stub(transaction(1L, TransactionApi.Source.TRANSFER_IN, TransactionApi.Status.CONFIRMED, "1.1000",
                "2026-09-11T00:00:00Z", null));

        assertThat(onlyFact().baselineNav()).isEqualByComparingTo("1.1000");
    }

    @Test
    void 未确认流水不参与基准线() {
        stub(transaction(1L, TransactionApi.Source.INCREASE, TransactionApi.Status.PENDING, "1.9000",
                "2026-09-20T00:00:00Z", null));

        assertThat(onlyFact().baselineNav()).isNull();
    }

    @Test
    void 调整流水与成本修正不参与基准线() {
        // 这些都是「录入即确认、不计净值」的来源，即便带了净值也不该当基准线
        stub(transaction(1L, TransactionApi.Source.ADJUST_IN, TransactionApi.Status.CONFIRMED, "9.9000",
                        "2026-09-20T00:00:00Z", null),
                transaction(2L, TransactionApi.Source.ADJUST_OUT, TransactionApi.Status.CONFIRMED, "9.9000",
                        "2026-09-20T00:00:00Z", null),
                transaction(3L, TransactionApi.Source.COST_BASIS_RESET, TransactionApi.Status.CONFIRMED, "9.9000",
                        "2026-09-20T00:00:00Z", null));

        assertThat(onlyFact().baselineNav()).isNull();
    }

    @Test
    void 净值缺失或非正的流水不参与基准线() {
        stub(transaction(1L, TransactionApi.Source.INCREASE, TransactionApi.Status.CONFIRMED, null,
                        "2026-09-19T00:00:00Z", null),
                transaction(2L, TransactionApi.Source.INCREASE, TransactionApi.Status.CONFIRMED, "0.0000",
                        "2026-09-20T00:00:00Z", null));

        assertThat(onlyFact().baselineNav()).isNull();
    }

    @Test
    void 无可用流水时不回退到持仓成本单价() {
        stub();

        var fact = onlyFact();

        assertThat(fact.baselineNav()).isNull();
        assertThat(fact.baselineNavDate()).isNull();
        // 成本单价仍照常提供，只是网格基准线不拿它兜底
        assertThat(fact.costPerShare()).isEqualByComparingTo("2.0000");
    }

    @Test
    void 缺交易日时回退确认时间作为成交日() {
        stub(transaction(1L, TransactionApi.Source.DECREASE, TransactionApi.Status.CONFIRMED, "1.4000",
                null, "2026-09-16T07:00:00Z"));

        var fact = onlyFact();

        assertThat(fact.baselineNav()).isEqualByComparingTo("1.4000");
        assertThat(fact.baselineNavDate()).isEqualTo(Instant.parse("2026-09-16T00:00:00Z"));
    }

    /** 一只关注基金的最小事实骨架；只有流水与持仓成本会随用例变化。 */
    private void stub(TransactionApi.Transaction... history) {
        when(fundReturns.currentFunds(OWNER)).thenReturn(List.of(new FundReturnApi.FundReturnSnapshot(FUND_ID, 101L,
                "161725", "招商中证白酒", "OPEN", true, new BigDecimal("0.01"), new BigDecimal("10000"),
                new BigDecimal("500"), new BigDecimal("0.05"), new BigDecimal("1.5000"), NOW, "AVAILABLE")));
        when(positions.findOwned(OWNER, FUND_ID)).thenReturn(Optional.of(new PositionApi.Position(FUND_ID, OWNER,
                PositionApi.Status.OPEN, NOW.minusSeconds(86400 * 30), new BigDecimal("2.0000"),
                new BigDecimal("10000"))));
        when(positions.openLots(OWNER, FUND_ID)).thenReturn(List.of());
        when(navs.latest(101L)).thenReturn(Optional.of(new PublishedNavApi.PublishedNav(101L, "161725",
                NOW.minusSeconds(86400), new BigDecimal("1.5000"), new BigDecimal("2.4000"), NOW)));
        when(products.findById(101L)).thenReturn(Optional.of(new FundProductApi.Product(101L, "161725",
                "招商中证白酒", FundProductApi.ProductType.INDEX, FundProductApi.InvestmentTarget.STOCK, null,
                null)));
        when(transactions.findByPortfolioFund(OWNER, FUND_ID)).thenReturn(List.of(history));
    }

    private com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway.AlertFundFact
            onlyFact() {
        return gateway.currentFunds(OWNER).getFirst();
    }

    /** 流水的方便构造：日期用 ISO 字符串给，null 表示该时间缺失。 */
    private static TransactionApi.Transaction transaction(long id, TransactionApi.Source source,
                                                          TransactionApi.Status status, String nav,
                                                          String tradeDate, String confirmTime) {
        return new TransactionApi.Transaction(id, FUND_ID, OWNER, source, status, new BigDecimal("1000"),
                new BigDecimal("1000"), nav == null ? null : new BigDecimal(nav), BigDecimal.ZERO, BigDecimal.ZERO,
                instant(tradeDate), instant(confirmTime), null, NOW.minusSeconds(86400 * 60), null, null, null);
    }

    private static Instant instant(String value) {
        return value == null ? null : Instant.parse(value);
    }
}
