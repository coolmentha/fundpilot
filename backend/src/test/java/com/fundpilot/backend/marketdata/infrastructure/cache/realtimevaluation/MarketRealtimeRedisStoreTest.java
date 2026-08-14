package com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation;

import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundEstimateSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundIntradayChart;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.IndexRealtimeSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.MarketBreadthSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.MarketVolumePriceSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.MoneyFlowSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.SectorSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MarketRealtimeRedisStoreTest {

    @Test
    void saveAndLoad_JSON往返保持完整快照() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        MarketRealtimeRedisStore store = new MarketRealtimeRedisStore(redisTemplate);
        MarketRealtimeRedisStore.Snapshot expected = new MarketRealtimeRedisStore.Snapshot(
                List.of(new IndexRealtimeSnapshot("1.000001", "上证指数", new BigDecimal("3500.12"),
                        new BigDecimal("12.34"), new BigDecimal("0.0035"), new BigDecimal("123456"))),
                new MarketBreadthSnapshot(3000, 2000, 100, 42, 25),
                List.of(new SectorSnapshot("BK0420", "航空机场", new BigDecimal("0.01"),
                        new BigDecimal("1000"), new BigDecimal("200"))),
                new MoneyFlowSnapshot(new BigDecimal("300"), Instant.parse("2026-07-20T06:00:00Z")),
                Map.of("510300", new FundEstimateSnapshot(new BigDecimal("0.0123"),
                        "2026-07-20 14:00", "2026-07-19")),
                Map.of("510300", EstimateStatus.AVAILABLE),
                Map.of("510300", new FundIntradayChart("2026-07-20", "2026-07-19", new BigDecimal("1.0000"),
                        List.of(new FundIntradayChart.Point("09:30", new BigDecimal("1.0010"))),
                        List.of(new FundIntradayChart.TradingSession("09:30", "11:30"),
                                new FundIntradayChart.TradingSession("13:00", "15:00")))),
                Instant.parse("2026-07-20T06:00:00Z"), Instant.parse("2026-07-20T06:00:01Z"),
                Instant.parse("2026-07-20T06:00:02Z"),
                new MarketVolumePriceSnapshot(new BigDecimal("0.0035"), new BigDecimal("1.68"),
                        Instant.parse("2026-07-20T06:00:00Z")));

        store.save(expected);

        ArgumentCaptor<String> json = ArgumentCaptor.forClass(String.class);
        verify(values).set(anyString(), json.capture());
        when(values.get(anyString())).thenReturn(json.getValue());
        assertThat(store.load()).contains(expected);
    }

    @Test
    void load_旧JSON缺平盘数和更新时间时保持为空() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(anyString())).thenReturn("""
                {"indices":[],"breadth":{"risingCount":3000,"fallingCount":2000,
                 "limitUpCount":42,"limitDownCount":25},"sectors":[],"moneyFlow":null,
                 "estimates":{},"estimateStatuses":{},"intradayCharts":{}}
                """);

        MarketRealtimeRedisStore.Snapshot snapshot = new MarketRealtimeRedisStore(redisTemplate).load().orElseThrow();

        assertThat(snapshot.breadth().flatCount()).isNull();
        assertThat(snapshot.indicesUpdatedAt()).isNull();
        assertThat(snapshot.breadthUpdatedAt()).isNull();
        assertThat(snapshot.sectorsUpdatedAt()).isNull();
        assertThat(snapshot.marketVolumePrice()).isNull();
    }
}
