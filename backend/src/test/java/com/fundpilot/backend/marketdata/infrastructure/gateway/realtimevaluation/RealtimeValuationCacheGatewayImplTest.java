package com.fundpilot.backend.marketdata.infrastructure.gateway.realtimevaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RealtimeValuationCacheGatewayImplTest {
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

        var result = new RealtimeValuationCacheGatewayImpl(redis)
                .findByFundCodes(List.of("000001", "000002", "000003"));

        assertThat(result.get("000001").estimatedChangePct()).isEqualByComparingTo("0.0123");
        assertThat(result.get("000001").status()).isEqualTo("AVAILABLE");
        assertThat(result.get("000002").status()).isEqualTo("TIMEOUT");
        assertThat(result.get("000003").status()).isEqualTo("NOT_ATTEMPTED");
    }

    @Test
    void redisFailureDegradesToEmptyResult() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("redis unavailable"));

        assertThat(new RealtimeValuationCacheGatewayImpl(redis).findByFundCodes(List.of("000001"))).isEmpty();
    }

    @Test
    void readsAvailableIntradayChartOnly() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("fundpilot:market-realtime:v1")).thenReturn("""
                {"estimates":{"000001":{"estimatedChangePct":0.0123}},"estimateStatuses":{"000001":"AVAILABLE"},
                "intradayCharts":{"000001":{"estimateDate":"2026-07-29","baseNav":1.0000,
                "points":[{"time":"09:30","nav":1.0010},{"time":"09:31","nav":1.0020}]}}}
                """);

        var result = new RealtimeValuationCacheGatewayImpl(redis).findIntraday("000001");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().points()).extracting(value -> value.time())
                .containsExactly("09:30", "09:31");
    }
}
