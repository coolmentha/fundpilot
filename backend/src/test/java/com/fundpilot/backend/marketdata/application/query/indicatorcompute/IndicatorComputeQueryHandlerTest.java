package com.fundpilot.backend.marketdata.application.query.indicatorcompute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.marketdata.application.gateway.indicatorcompute.IndicatorFundGateway;
import com.fundpilot.backend.marketdata.application.gateway.navpublishing.TrackedNavProductGateway.InvestmentTarget;
import com.fundpilot.backend.marketdata.domain.indexkline.IndexBar;
import com.fundpilot.backend.marketdata.domain.indexkline.IndexKlineRepository;
import com.fundpilot.backend.marketdata.domain.indexvaluation.IndexValuation;
import com.fundpilot.backend.marketdata.domain.indexvaluation.IndexValuationRepository;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNav;
import com.fundpilot.backend.marketdata.domain.publishednav.PublishedNavRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IndicatorComputeQueryHandlerTest {

    private static final Instant END = Instant.parse("2026-09-21T00:00:00Z");
    private static final String INDEX_CODE = "000300.SH";

    private final PublishedNavRepository navs = mock(PublishedNavRepository.class);
    private final IndexKlineRepository klines = mock(IndexKlineRepository.class);
    private final IndexValuationRepository valuations = mock(IndexValuationRepository.class);
    private final IndicatorFundGateway funds = mock(IndicatorFundGateway.class);

    private IndicatorComputeQueryHandler handler;

    @BeforeEach
    void setUp() {
        handler = new IndicatorComputeQueryHandler(navs, klines, valuations, funds,
                Clock.fixed(Instant.parse("2026-09-25T06:30:00Z"), ZoneOffset.UTC));
        track(InvestmentTarget.STOCK, INDEX_CODE);
    }

    @Test
    void 均线指标按窗口现算最近两个取值() {
        when(navs.findByProductIdAndDateRange(anyLong(), any(), any())).thenReturn(navs("1", "2", "3", "4",
                "5", "6", "7", "8", "9", "10"));

        List<IndicatorComputeQueryHandler.IndicatorValue> values = handler.recent(request("MA", Map.of("window", 3), 2));

        assertThat(values).hasSize(2);
        assertThat(values.get(0).value()).isEqualByComparingTo("8");
        assertThat(values.get(1).value()).isEqualByComparingTo("9");
        assertThat(values.get(1).asOf()).isAfter(values.get(0).asOf());
    }

    @Test
    void 不同窗口算出不同取值且同日重复求值命中缓存() {
        when(navs.findByProductIdAndDateRange(anyLong(), any(), any())).thenReturn(navs("1", "2", "3", "4",
                "5", "6", "7", "8", "9", "10"));

        var windowThree = handler.recent(request("MA", Map.of("window", 3), 2));
        var windowFive = handler.recent(request("MA", Map.of("window", 5), 2));
        var cached = handler.recent(request("MA", Map.of("window", 3), 2));

        assertThat(windowThree.get(1).value()).isEqualByComparingTo("9");
        assertThat(windowFive.get(1).value()).isEqualByComparingTo("8");
        assertThat(cached).isEqualTo(windowThree);
        verify(navs, times(2)).findByProductIdAndDateRange(anyLong(), any(), any());
    }

    @Test
    void 净值不足窗口时返回空列表() {
        when(navs.findByProductIdAndDateRange(anyLong(), any(), any())).thenReturn(navs("1", "2", "3"));

        assertThat(handler.recent(request("MA", Map.of("window", 250), 2))).isEmpty();
        assertThat(handler.recent(request("PRICE_VS_MA", Map.of("window", 250), 2))).isEmpty();
    }

    @Test
    void 货币基金不计算净值类指标() {
        track(InvestmentTarget.MONEY_MARKET, INDEX_CODE);

        assertThat(handler.recent(request("MA", Map.of("window", 3), 2))).isEmpty();
    }

    @Test
    void 均线交叉取快慢线差值() {
        when(navs.findByProductIdAndDateRange(anyLong(), any(), any())).thenReturn(navs("1", "1", "1", "1",
                "1", "1", "1", "1", "1", "10"));

        List<IndicatorComputeQueryHandler.IndicatorValue> values =
                handler.recent(request("MA_CROSS", Map.of("fast", 2, "slow", 5), 2));

        assertThat(values).hasSize(2);
        assertThat(values.get(0).value()).isEqualByComparingTo("0");
        assertThat(values.get(1).value()).isEqualByComparingTo("2.7");
    }

    @Test
    void 快线窗口不小于慢线窗口被拒绝() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> handler.recent(request("MA_CROSS", Map.of("fast", 50, "slow", 20), 2)));
    }

    @Test
    void 区间位置落在一到零之间() {
        when(navs.findByProductIdAndDateRange(anyLong(), any(), any())).thenReturn(navs("5", "1", "3", "4"));

        List<IndicatorComputeQueryHandler.IndicatorValue> values =
                handler.recent(request("NAV_RANGE_POSITION", Map.of("window", 3), 2));

        assertThat(values).hasSize(2);
        assertThat(values.get(0).value()).isEqualByComparingTo("0.5");
        assertThat(values.get(1).value()).isEqualByComparingTo("1");
    }

    @Test
    void 周线MACD不足最小周数返回空() {
        when(navs.findByProductIdAndDateRange(anyLong(), any(), any())).thenReturn(risingNavs(100));

        assertThat(handler.recent(request("WEEKLY_MACD_HISTOGRAM", Map.of(), 2))).isEmpty();
    }

    @Test
    void 周线MACD按可调参数现算柱高() {
        when(navs.findByProductIdAndDateRange(anyLong(), any(), any())).thenReturn(risingNavs(300));

        List<IndicatorComputeQueryHandler.IndicatorValue> values =
                handler.recent(request("WEEKLY_MACD_HISTOGRAM", Map.of("fast", 12, "slow", 26, "signal", 9), 2));

        assertThat(values).hasSize(2);
        assertThat(values.get(1).value()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    void 量能比为最新完整日K与近窗均量之比() {
        when(klines.findAll(INDEX_CODE)).thenReturn(bars(20, 100L));

        List<IndicatorComputeQueryHandler.IndicatorValue> values =
                handler.recent(request("VOLUME_RATIO", Map.of("window", 20), 1));

        assertThat(values).hasSize(1);
        assertThat(values.getFirst().value()).isGreaterThan(new BigDecimal("2.7"));
    }

    @Test
    void 缺少基准指数的基金量能与估值指标为空() {
        track(InvestmentTarget.STOCK, null);

        assertThat(handler.recent(request("VOLUME_RATIO", Map.of(), 1))).isEmpty();
        assertThat(handler.recent(request("INDEX_PE", Map.of(), 1))).isEmpty();
        assertThat(handler.recent(request("INDEX_PE_PERCENTILE", Map.of(), 1))).isEmpty();
    }

    @Test
    void 市盈率分位为历史样本占比() {
        when(valuations.findHistory(anyString(), anyString(), any())).thenReturn(valuations(10, 11, 12, 13, 14,
                15, 16, 17, 18, 19));

        List<IndicatorComputeQueryHandler.IndicatorValue> latest = handler.recent(request("INDEX_PE", Map.of(), 2));

        assertThat(latest.get(0).value()).isEqualByComparingTo("18");
        assertThat(latest.get(1).value()).isEqualByComparingTo("19");
    }

    @Test
    void 市盈率分位按当前点之前的样本窗口计算() {
        when(valuations.findHistory(anyString(), anyString(), any())).thenReturn(valuations(3, 1, 4, 1, 5));

        List<IndicatorComputeQueryHandler.IndicatorValue> percentile =
                handler.recent(request("INDEX_PE_PERCENTILE", Map.of(), 2));

        assertThat(percentile.get(0).value()).isEqualByComparingTo("0.5");
        assertThat(percentile.get(1).value()).isEqualByComparingTo("1");
    }

    @Test
    void 支持清单内的指标均可计算() {
        for (String code : IndicatorComputeQueryHandler.supportedCodes()) {
            assertThat(handler.recent(request(code, Map.of(), 1))).isEmpty();
        }
    }

    @Test
    void 不支持的指标与越界取值个数被拒绝() {
        assertThatIllegalArgumentException().isThrownBy(() -> handler.recent(request("RSI", Map.of(), 1)));
        assertThatIllegalArgumentException().isThrownBy(() -> handler.recent(request("MA", Map.of(), 0)));
        assertThatIllegalArgumentException().isThrownBy(() -> handler.recent(request("MA", Map.of(), 6)));
    }

    private void track(InvestmentTarget target, String indexCode) {
        when(funds.findById(1L)).thenReturn(Optional.of(new IndicatorFundGateway.IndicatorFund(1L, "000001",
                "测试基金", indexCode, target)));
    }

    private static IndicatorComputeQueryHandler.IndicatorComputeRequest request(String code,
                                                                              Map<String, Integer> params,
                                                                              int count) {
        return new IndicatorComputeQueryHandler.IndicatorComputeRequest(1L, code, params, END, count);
    }

    private static List<PublishedNav> navs(String... accumulated) {
        List<PublishedNav> result = new ArrayList<>();
        Instant start = END.minus(accumulated.length, ChronoUnit.DAYS);
        for (int index = 0; index < accumulated.length; index++) {
            Instant date = start.plus(index, ChronoUnit.DAYS);
            result.add(PublishedNav.publish(9L, 1L, "000001", date, BigDecimal.ONE,
                    new BigDecimal(accumulated[index]), date));
        }
        return result;
    }

    private static List<PublishedNav> risingNavs(int size) {
        String[] accumulated = new String[size];
        for (int index = 0; index < size; index++) {
            accumulated[index] = BigDecimal.ONE.add(BigDecimal.valueOf(index).movePointLeft(3)).toPlainString();
        }
        return navs(accumulated);
    }

    private static List<IndexBar> bars(int size, long lastVolume) {
        List<IndexBar> result = new ArrayList<>();
        Instant start = END.minus(size, ChronoUnit.DAYS);
        for (int index = 0; index < size; index++) {
            long volume = index == size - 1 ? Math.round(lastVolume * 3) : lastVolume;
            result.add(new IndexBar(INDEX_CODE, start.plus(index, ChronoUnit.DAYS),
                    new BigDecimal("10"), new BigDecimal("11"), new BigDecimal("9"), new BigDecimal("10"), volume));
        }
        return result;
    }

    private static List<IndexValuation> valuations(int... peValues) {
        List<IndexValuation> result = new ArrayList<>();
        Instant start = END.minus(peValues.length, ChronoUnit.DAYS);
        for (int index = 0; index < peValues.length; index++) {
            result.add(new IndexValuation(INDEX_CODE, start.plus(index, ChronoUnit.DAYS),
                    BigDecimal.valueOf(peValues[index]), "CSINDEX_INDEX_CSI_DS_PE_PEG"));
        }
        return result;
    }
}