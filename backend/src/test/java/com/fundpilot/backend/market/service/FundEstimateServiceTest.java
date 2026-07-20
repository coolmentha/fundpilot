package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.client.EastmoneyFundGzClient;
import com.fundpilot.backend.market.client.ThsFundEstimateClient;
import feign.Request;
import feign.RequestTemplate;
import feign.RetryableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
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
    void 东方财富失效时降级同花顺估值() {
        EastmoneyFundGzClient client = mock(EastmoneyFundGzClient.class);
        ThsFundEstimateClient thsClient = mock(ThsFundEstimateClient.class);
        when(client.fetchGzRaw("016664")).thenReturn("<html>页面未找到</html>");
        when(thsClient.fetchEstimateRaw("016664")).thenReturn(
                "vm_fd_016664='2026-07-17;0930-1130,1300-1500|2026-07-20~2.9763~0930,3.05294,2.9763,0;1500,3.10100,2.9763,0;';");

        FundEstimateResult result = service(client, thsClient).fetchEstimateResult("016664");

        assertThat(result.status()).isEqualTo(EstimateStatus.AVAILABLE);
        assertThat(result.snapshot().estimatedChangePct()).isEqualByComparingTo("0.04189765816617948");
        assertThat(result.snapshot().estimateTime()).isEqualTo("2026-07-20 15:00");
        assertThat(result.snapshot().baseNavDate()).isEqualTo("2026-07-17");
    }

    private static FundEstimateService service(EastmoneyFundGzClient client, ThsFundEstimateClient thsClient) {
        return new FundEstimateService(client, thsClient, new MarketDataMetrics(new SimpleMeterRegistry()));
    }
}
