package com.fundpilot.backend.marketdata.infrastructure.cache.realtimevaluation;

import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyFundGzClient;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.EastmoneyFundEstimatePageClient;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.FundEstimateSnapshot;
import com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed.ThsFundEstimateClient;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundEstimateServiceTest {

    @Test
    void 空响应标记为不可用而不是失败() {
        EastmoneyFundGzClient client = mock(EastmoneyFundGzClient.class);
        ThsFundEstimateClient thsClient = mock(ThsFundEstimateClient.class);
        when(client.fetchGzRaw("000001")).thenReturn("");
        when(thsClient.fetchEstimateRaw("000001")).thenReturn("");

        FundEstimateResult result = service(client, thsClient).fetchEstimateResult("000001");

        assertThat(result.status()).isEqualTo(EstimateStatus.UNAVAILABLE);
    }

    @Test
    void 结构损坏标记为解析失败() {
        EastmoneyFundGzClient client = mock(EastmoneyFundGzClient.class);
        ThsFundEstimateClient thsClient = mock(ThsFundEstimateClient.class);
        when(client.fetchGzRaw("000001")).thenReturn("jsonpgz({broken});");
        when(thsClient.fetchEstimateRaw("000001")).thenReturn("vm_fd_000001='broken';");

        FundEstimateResult result = service(client, thsClient).fetchEstimateResult("000001");

        assertThat(result.status()).isEqualTo(EstimateStatus.PARSE_ERROR);
    }

    @Test
    void Feign超时标记为TIMEOUT() {
        EastmoneyFundGzClient client = mock(EastmoneyFundGzClient.class);
        ThsFundEstimateClient thsClient = mock(ThsFundEstimateClient.class);
        Request request = Request.create(Request.HttpMethod.GET, "https://example.test",
                Map.of(), (byte[]) null, StandardCharsets.UTF_8, new RequestTemplate());
        when(client.fetchGzRaw("000001")).thenThrow(new RetryableException(
                0, "timeout", Request.HttpMethod.GET, new SocketTimeoutException("timeout"), (Long) null, request));
        when(thsClient.fetchEstimateRaw("000001")).thenThrow(new RetryableException(
                0, "timeout", Request.HttpMethod.GET, new SocketTimeoutException("timeout"), (Long) null, request));

        FundEstimateResult result = service(client, thsClient).fetchEstimateResult("000001");

        assertThat(result.status()).isEqualTo(EstimateStatus.TIMEOUT);
    }

    @Test
    void 同花顺优先返回估值且不访问东方财富() {
        EastmoneyFundGzClient client = mock(EastmoneyFundGzClient.class);
        ThsFundEstimateClient thsClient = mock(ThsFundEstimateClient.class);
        when(thsClient.fetchEstimateRaw("016664")).thenReturn(
                "vm_fd_016664='2026-07-17;0930-1130,1300-1500|2026-07-20~2.9763~0930,3.05294,2.9763,0;1500,3.10100,2.9763,0;';");

        FundEstimateResult result = service(client, thsClient).fetchEstimateResult("016664");

        assertThat(result.status()).isEqualTo(EstimateStatus.AVAILABLE);
        assertThat(result.snapshot().estimatedChangePct()).isEqualByComparingTo("0.04189765816617948");
        assertThat(result.snapshot().estimateTime()).isEqualTo("2026-07-20 15:00");
        assertThat(result.snapshot().baseNavDate()).isEqualTo("2026-07-17");
        assertThat(result.intradayChart().points()).hasSize(2);
        assertThat(result.intradayChart().points().getLast().time()).isEqualTo("15:00");
        verify(client, never()).fetchGzRaw("016664");
    }

    @Test
    void 同花顺解析失败后在冷却期内直接使用东方财富() {
        EastmoneyFundGzClient client = mock(EastmoneyFundGzClient.class);
        ThsFundEstimateClient thsClient = mock(ThsFundEstimateClient.class);
        when(thsClient.fetchEstimateRaw("000001")).thenReturn("vm_fd_000001='broken';");
        when(client.fetchGzRaw("000001")).thenReturn("jsonpgz({\"fundcode\":\"000001\",\"gsz\":\"1.234\","
                + "\"gszzl\":\"1.20\",\"gztime\":\"2026-07-20 15:00\",\"jzrq\":\"2026-07-17\"});");
        FundEstimateService service = service(client, thsClient);

        assertThat(service.fetchEstimateResult("000001").status()).isEqualTo(EstimateStatus.AVAILABLE);
        assertThat(service.fetchEstimateResult("000001").status()).isEqualTo(EstimateStatus.AVAILABLE);

        verify(thsClient, times(1)).fetchEstimateRaw("000001");
        verify(client, times(2)).fetchGzRaw("000001");
    }

    @Test
    void 同花顺失败后AKShare静态页批量结果可复用且不访问旧备用源() {
        EastmoneyFundGzClient client = mock(EastmoneyFundGzClient.class);
        ThsFundEstimateClient thsClient = mock(ThsFundEstimateClient.class);
        EastmoneyFundEstimatePageClient pageClient = mock(EastmoneyFundEstimatePageClient.class);
        when(thsClient.fetchEstimateRaw("000001")).thenReturn("vm_fd_000001='broken';");
        when(pageClient.fetchPageRaw(1)).thenReturn("""
                <div id="gsdata">2026-07-20 估算数据</div>
                <div id="dwjzdata">2026-07-17</div>
                <table id="tContent"><tbody id="tableContent">
                  <tr><td></td><td>1</td><td>000001</td><td>测试基金</td>
                    <td data-gz="1.2345">--</td><td data-gz="1.20%">--</td>
                    <td>---</td><td>---</td><td>---</td><td>1.3000</td><td></td>
                  </tr>
                </tbody></table>
                """);
        when(pageClient.fetchPageRaw(2)).thenReturn("");
        FundEstimateService service = service(client, thsClient, pageClient);

        assertThat(service.fetchEstimateResult("000001").snapshot().estimatedChangePct())
                .isEqualByComparingTo("0.012");
        assertThat(service.fetchEstimateResult("000001").snapshot().estimateTime())
                .isEqualTo("2026-07-20 15:00");

        verify(pageClient, times(1)).fetchPageRaw(1);
        verify(pageClient, never()).fetchPageRaw(2);
        verify(client, never()).fetchGzRaw("000001");
    }

    @Test
    void 静态页缓存从响应完成时开始计算有效期() {
        EastmoneyFundGzClient client = mock(EastmoneyFundGzClient.class);
        ThsFundEstimateClient thsClient = mock(ThsFundEstimateClient.class);
        EastmoneyFundEstimatePageClient pageClient = mock(EastmoneyFundEstimatePageClient.class);
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-20T07:00:00Z"));
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(ignored -> now.get());
        when(clock.withZone(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation ->
                Clock.fixed(now.get(), invocation.getArgument(0)));
        when(thsClient.fetchEstimateRaw("000001")).thenReturn("");
        when(pageClient.fetchPageRaw(1)).thenAnswer(ignored -> {
            now.set(now.get().plus(Duration.ofMinutes(2)));
            return """
                    <div id="gsdata">2026-07-20 估算数据</div>
                    <div id="dwjzdata">2026-07-17</div>
                    <table id="tContent"><tbody id="tableContent">
                      <tr><td></td><td>1</td><td>000001</td><td>测试基金</td>
                        <td data-gz="1.2345">--</td><td data-gz="1.20%">--</td>
                        <td>---</td><td>---</td><td>---</td><td>1.3000</td><td></td>
                      </tr>
                    </tbody></table>
                    """;
        });
        when(pageClient.fetchPageRaw(2)).thenReturn("");
        FundEstimateService service = service(client, thsClient, pageClient, unavailableEtfService(), clock);

        service.fetchEstimateResult("000001");
        service.fetchEstimateResult("000001");

        verify(pageClient, times(1)).fetchPageRaw(1);
        verify(pageClient, never()).fetchPageRaw(2);
    }

    @Test
    void 静态页只保留本轮基金且命中当前基金后从下一页继续() {
        EastmoneyFundGzClient client = mock(EastmoneyFundGzClient.class);
        ThsFundEstimateClient thsClient = mock(ThsFundEstimateClient.class);
        EastmoneyFundEstimatePageClient pageClient = mock(EastmoneyFundEstimatePageClient.class);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-20T07:00:00Z"));
        when(thsClient.fetchEstimateRaw("000001")).thenReturn("");
        when(pageClient.fetchPageRaw(1)).thenAnswer(ignored -> {
            clock.advance(Duration.ofSeconds(20));
            return estimatePage("000001");
        });
        when(pageClient.fetchPageRaw(2)).thenAnswer(ignored -> {
            clock.advance(Duration.ofSeconds(10));
            return estimatePage("000002");
        });
        when(pageClient.fetchPageRaw(3)).thenReturn("");
        FundEstimateService service = service(client, thsClient, pageClient, unavailableEtfService(), clock);

        var targets = java.util.Set.of("000001", "000002");
        service.fetchEstimateResult("000001", clock.instant().plusSeconds(25), targets);
        service.fetchEstimateResult("000002", clock.instant().plusSeconds(25), targets);

        verify(pageClient, times(1)).fetchPageRaw(1);
        verify(pageClient, times(1)).fetchPageRaw(2);
        verify(pageClient, never()).fetchPageRaw(3);
    }

    @Test
    void 静态页未命中后ETF_IOPV估值可用且不访问旧备用源() {
        EastmoneyFundGzClient client = mock(EastmoneyFundGzClient.class);
        ThsFundEstimateClient thsClient = mock(ThsFundEstimateClient.class);
        EastmoneyFundEstimatePageClient pageClient = mock(EastmoneyFundEstimatePageClient.class);
        EtfIopvEstimateService etfService = mock(EtfIopvEstimateService.class);
        FundEstimateSnapshot snapshot = new FundEstimateSnapshot(
                new java.math.BigDecimal("0.0123"), "2026-07-20 15:00", "2026-07-17");
        when(thsClient.fetchEstimateRaw("510300")).thenReturn("vm_fd_510300='broken';");
        when(pageClient.fetchPageRaw(1)).thenReturn("");
        when(etfService.fetchEstimateResult(org.mockito.ArgumentMatchers.eq("510300"),
                org.mockito.ArgumentMatchers.any(Instant.class))).thenReturn(FundEstimateResult.available(snapshot));

        FundEstimateService service = service(client, thsClient, pageClient, etfService);

        assertThat(service.fetchEstimateResult("510300").snapshot()).isEqualTo(snapshot);
        verify(client, never()).fetchGzRaw("510300");
    }

    private static FundEstimateService service(EastmoneyFundGzClient client, ThsFundEstimateClient thsClient) {
        return service(client, thsClient, mock(EastmoneyFundEstimatePageClient.class),
                unavailableEtfService());
    }

    private static FundEstimateService service(EastmoneyFundGzClient client, ThsFundEstimateClient thsClient,
                                               EastmoneyFundEstimatePageClient pageClient) {
        return service(client, thsClient, pageClient, unavailableEtfService());
    }

    private static EtfIopvEstimateService unavailableEtfService() {
        EtfIopvEstimateService service = mock(EtfIopvEstimateService.class);
        when(service.fetchEstimateResult(anyString(), org.mockito.ArgumentMatchers.any(Instant.class)))
                .thenReturn(FundEstimateResult.unavailable());
        return service;
    }

    private static FundEstimateService service(EastmoneyFundGzClient client, ThsFundEstimateClient thsClient,
                                               EastmoneyFundEstimatePageClient pageClient,
                                               EtfIopvEstimateService etfService) {
        return service(client, thsClient, pageClient, etfService,
                Clock.fixed(Instant.parse("2026-07-20T07:00:00Z"), ZoneOffset.UTC));
    }

    private static FundEstimateService service(EastmoneyFundGzClient client, ThsFundEstimateClient thsClient,
                                               EastmoneyFundEstimatePageClient pageClient,
                                               EtfIopvEstimateService etfService, Clock clock) {
        return new FundEstimateService(client, pageClient, etfService, thsClient,
                new MarketDataMetrics(new SimpleMeterRegistry()), clock);
    }

    private static String estimatePage(String code) {
        return """
                <div id="gsdata">2026-07-20 估算数据</div>
                <div id="dwjzdata">2026-07-17</div>
                <table id="tContent"><tbody id="tableContent">
                  <tr><td></td><td>1</td><td>%s</td><td>测试基金</td>
                    <td data-gz="1.2345">--</td><td data-gz="1.20%%">--</td>
                    <td>---</td><td>---</td><td>---</td><td>1.3000</td><td></td>
                  </tr>
                </tbody></table>
                """.formatted(code);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        private MutableClock(Instant now) { this.now = new AtomicReference<>(now); }
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
        private void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
    }
}
