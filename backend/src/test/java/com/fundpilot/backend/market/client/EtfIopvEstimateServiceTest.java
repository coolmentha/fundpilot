package com.fundpilot.backend.market.client;

import com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation.EtfIopvEstimateService;
import com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation.EstimateStatus;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyEtfSpotClient;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsEtfSpotClient;
import com.fundpilot.backend.platform.observability.MarketDataMetrics;
import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EtfIopvEstimateServiceTest {

    @Test
    void IOPV与最近确认净值配对计算估算涨跌且批量结果复用() {
        EastmoneyEtfSpotClient eastmoney = mock(EastmoneyEtfSpotClient.class);
        ThsEtfSpotClient ths = mock(ThsEtfSpotClient.class);
        when(ths.fetchSpotRaw()).thenReturn("""
                g({"data":{"data":{"510300":{"code":"510300","newnet":"4.4000",
                "newdate":"2026-08-06"}}}})
                """);
        long updatedAt = Instant.parse("2026-08-07T03:40:00Z").getEpochSecond();
        when(eastmoney.fetchSpotPageRaw(1)).thenReturn("""
                {"data":{"total":1,"diff":{"0":{"f12":"510300","f441":"4.5000",
                "f124":%d,"f297":"20260807"}}}}
                """.formatted(updatedAt));

        EtfIopvEstimateService service = service(eastmoney, ths);

        var first = service.fetchEstimateResult("510300");
        var second = service.fetchEstimateResult("510300");

        assertThat(first.status()).isEqualTo(EstimateStatus.AVAILABLE);
        assertThat(first.snapshot().estimatedChangePct()).isEqualByComparingTo("0.02272727272727273");
        assertThat(first.snapshot().estimateTime()).isEqualTo("2026-08-07 11:40");
        assertThat(first.snapshot().baseNavDate()).isEqualTo("2026-08-06");
        assertThat(second).isEqualTo(first);
        verify(ths, times(1)).fetchSpotRaw();
        verify(eastmoney, times(1)).fetchSpotPageRaw(1);
    }

    @Test
    void 非交易型开放式基金不触发ETF源() {
        EastmoneyEtfSpotClient eastmoney = mock(EastmoneyEtfSpotClient.class);
        ThsEtfSpotClient ths = mock(ThsEtfSpotClient.class);

        assertThat(service(eastmoney, ths).fetchEstimateResult("000001").status())
                .isEqualTo(EstimateStatus.UNAVAILABLE);
        verifyNoInteractions(eastmoney, ths);
    }

    @Test
    void IOPV超时返回TIMEOUT() {
        EastmoneyEtfSpotClient eastmoney = mock(EastmoneyEtfSpotClient.class);
        ThsEtfSpotClient ths = mock(ThsEtfSpotClient.class);
        when(ths.fetchSpotRaw()).thenReturn("g({\"data\":{\"data\":{\"510300\":{\"code\":\"510300\",\"newnet\":\"4.4\",\"newdate\":\"2026-08-06\"}}}})");
        Request request = Request.create(Request.HttpMethod.GET, "https://example.test",
                Map.of(), (byte[]) null, StandardCharsets.UTF_8, new RequestTemplate());
        when(eastmoney.fetchSpotPageRaw(1)).thenThrow(new RetryableException(
                0, "timeout", Request.HttpMethod.GET, new SocketTimeoutException("timeout"), (Long) null, request));

        assertThat(service(eastmoney, ths).fetchEstimateResult("510300").status())
                .isEqualTo(EstimateStatus.TIMEOUT);
    }

    @Test
    void IOPV缓存从响应完成时开始计算有效期() {
        EastmoneyEtfSpotClient eastmoney = mock(EastmoneyEtfSpotClient.class);
        ThsEtfSpotClient ths = mock(ThsEtfSpotClient.class);
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-08-07T03:41:00Z"));
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> now.get());
        when(ths.fetchSpotRaw()).thenReturn("""
                g({"data":{"data":{"510300":{"code":"510300","newnet":"4.4000",
                "newdate":"2026-08-06"}}}})
                """);
        long updatedAt = Instant.parse("2026-08-07T03:40:00Z").getEpochSecond();
        when(eastmoney.fetchSpotPageRaw(1)).thenAnswer(ignored -> {
            now.set(now.get().plus(Duration.ofMinutes(2)));
            return """
                    {"data":{"total":1,"diff":{"0":{"f12":"510300","f441":"4.5000",
                    "f124":%d,"f297":"20260807"}}}}
                    """.formatted(updatedAt);
        });
        EtfIopvEstimateService service = service(eastmoney, ths, clock);

        service.fetchEstimateResult("510300");
        service.fetchEstimateResult("510300");

        verify(ths, times(1)).fetchSpotRaw();
        verify(eastmoney, times(1)).fetchSpotPageRaw(1);
    }

    @Test
    void IOPV分页达到截止时间后从下一页继续() {
        EastmoneyEtfSpotClient eastmoney = mock(EastmoneyEtfSpotClient.class);
        ThsEtfSpotClient ths = mock(ThsEtfSpotClient.class);
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-08-07T03:41:00Z"));
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> now.get());
        when(ths.fetchSpotRaw()).thenReturn("""
                g({"data":{"data":{
                  "510300":{"code":"510300","newnet":"4.4000","newdate":"2026-08-06"},
                  "510500":{"code":"510500","newnet":"4.4000","newdate":"2026-08-06"},
                  "510050":{"code":"510050","newnet":"4.4000","newdate":"2026-08-06"}
                }}})
                """);
        long updatedAt = Instant.parse("2026-08-07T03:40:00Z").getEpochSecond();
        when(eastmoney.fetchSpotPageRaw(1)).thenAnswer(ignored -> {
            now.set(now.get().plusSeconds(20));
            return quotePage("510300", 300, updatedAt);
        });
        when(eastmoney.fetchSpotPageRaw(2)).thenAnswer(ignored -> {
            now.set(now.get().plusSeconds(10));
            return quotePage("510500", 300, updatedAt);
        });
        when(eastmoney.fetchSpotPageRaw(3)).thenReturn(quotePage("510050", 300, updatedAt));
        EtfIopvEstimateService service = service(eastmoney, ths, clock);

        service.fetchEstimateResult("510300", now.get().plusSeconds(25));
        service.fetchEstimateResult("510300", now.get().plusSeconds(25));

        verify(eastmoney, times(1)).fetchSpotPageRaw(1);
        verify(eastmoney, times(1)).fetchSpotPageRaw(2);
        verify(eastmoney, times(1)).fetchSpotPageRaw(3);
    }

    private static EtfIopvEstimateService service(EastmoneyEtfSpotClient eastmoney, ThsEtfSpotClient ths) {
        return service(eastmoney, ths,
                Clock.fixed(Instant.parse("2026-08-07T03:41:00Z"), ZoneOffset.UTC));
    }

    private static EtfIopvEstimateService service(EastmoneyEtfSpotClient eastmoney, ThsEtfSpotClient ths,
                                                  Clock clock) {
        return new EtfIopvEstimateService(eastmoney, ths,
                new MarketDataMetrics(new SimpleMeterRegistry()), clock);
    }

    private static String quotePage(String code, int total, long updatedAt) {
        return """
                {"data":{"total":%d,"diff":{"0":{"f12":"%s","f441":"4.5000",
                "f124":%d,"f297":"20260807"}}}}
                """.formatted(total, code, updatedAt);
    }
}
