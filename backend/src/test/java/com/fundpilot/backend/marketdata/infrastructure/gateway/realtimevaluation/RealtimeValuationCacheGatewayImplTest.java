package com.fundpilot.backend.marketdata.infrastructure.gateway.realtimevaluation;

import com.fundpilot.backend.marketdata.application.gateway.realtimevaluation.RealtimeValuationCacheGateway;
import com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation.MarketRealtimeCache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RealtimeValuationCacheGatewayImplTest {
    private final MarketRealtimeCache marketRealtimeCache = mock(MarketRealtimeCache.class);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-29T05:00:00Z"), ZoneOffset.UTC);

    @Test
    void readsStatusesAndEstimateFromSharedCache() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("fundpilot:market-realtime:v1")).thenReturn("""
                {"estimates":{"000001":{"estimatedChangePct":0.0123,"estimateTime":"2026-07-29 12:00",\
                "baseNavDate":"2026-07-28"}},"estimateStatuses":{"000001":"AVAILABLE","000002":"TIMEOUT"}}
                """);

        var result = new RealtimeValuationCacheGatewayImpl(redis, CLOCK, marketRealtimeCache)
                .findByFundCodes(List.of("000001", "000002", "000003"));

        assertThat(result.get("000001").estimatedChangePct()).isEqualByComparingTo("0.0123");
        assertThat(result.get("000001").status()).isEqualTo("AVAILABLE");
        assertThat(result.get("000002").status()).isEqualTo("TIMEOUT");
        assertThat(result.get("000003").status()).isEqualTo("NOT_ATTEMPTED");
    }

    @Test
    void 昨日AVAILABLE估值不被当作今日数据返回() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("fundpilot:market-realtime:v1")).thenReturn("""
                {"estimates":{"000001":{"estimatedChangePct":0.0123,"estimateTime":"2026-07-28 14:50",\
                "baseNavDate":"2026-07-27"}},"estimateStatuses":{"000001":"AVAILABLE"}}
                """);

        var result = new RealtimeValuationCacheGatewayImpl(redis, CLOCK, marketRealtimeCache)
                .findByFundCodes(List.of("000001"));

        assertThat(result.get("000001").status()).isEqualTo("STALE");
    }

    @Test
    void redisFailureDegradesToEmptyResult() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));

        assertThat(new RealtimeValuationCacheGatewayImpl(redis, CLOCK, marketRealtimeCache)
                .findByFundCodes(List.of("000001"))).isEmpty();
    }

    @Test
    void readsAvailableIntradayChartOnly() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("fundpilot:market-realtime:v1")).thenReturn("""
                {"estimates":{"000001":{"estimatedChangePct":0.0123,"estimateTime":"2026-07-29 12:00"}},\
                "estimateStatuses":{"000001":"AVAILABLE"},
                "intradayCharts":{"000001":{"estimateDate":"2026-07-29","baseNav":1.0000,
                "tradingSessions":[{"start":"09:30","end":"11:30"},{"start":"13:00","end":"15:00"}],
                "points":[{"time":"09:30","nav":1.0010},{"time":"09:31","nav":1.0020}]}}}
                """);

        var result = new RealtimeValuationCacheGatewayImpl(redis, CLOCK, marketRealtimeCache).findIntraday("000001");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().points()).extracting(value -> value.time())
                .containsExactly("09:30", "09:31");
        assertThat(result.orElseThrow().tradingSessions()).containsExactly(
                new RealtimeValuationCacheGateway.TradingSession("09:30", "11:30"),
                new RealtimeValuationCacheGateway.TradingSession("13:00", "15:00"));
    }

    @Test
    void 旧Redis分时图缺少交易时段仍可读取() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("fundpilot:market-realtime:v1")).thenReturn("""
                {"estimates":{"000001":{"estimatedChangePct":0.0123,"estimateTime":"2026-07-29 12:00"}},
                "estimateStatuses":{"000001":"AVAILABLE"},
                "intradayCharts":{"000001":{"estimateDate":"2026-07-29","baseNav":1.0000,
                "points":[{"time":"09:30","nav":1.0010},{"time":"09:31","nav":1.0020}]}}}
                """);

        var result = new RealtimeValuationCacheGatewayImpl(redis, CLOCK, marketRealtimeCache).findIntraday("000001");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().tradingSessions()).isEmpty();
    }

    @Test
    void 昨日分时图不返回() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("fundpilot:market-realtime:v1")).thenReturn("""
                {"estimates":{"000001":{"estimatedChangePct":0.0123,"estimateTime":"2026-07-28 14:50"}},\
                "estimateStatuses":{"000001":"AVAILABLE"},
                "intradayCharts":{"000001":{"estimateDate":"2026-07-28","baseNav":1.0000,
                "points":[{"time":"09:30","nav":1.0010},{"time":"09:31","nav":1.0020}]}}}
                """);

        assertThat(new RealtimeValuationCacheGatewayImpl(redis, CLOCK, marketRealtimeCache).findIntraday("000001")).isEmpty();
    }
}
