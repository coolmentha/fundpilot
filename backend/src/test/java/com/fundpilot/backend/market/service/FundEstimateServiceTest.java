package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.client.EastmoneyFundGzClient;
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
        when(client.fetchGzRaw("000001")).thenReturn("");

        FundEstimateResult result = service(client).fetchEstimateResult("000001");

        assertThat(result.status()).isEqualTo(EstimateStatus.UNAVAILABLE);
    }

    @Test
    void 结构损坏标记为解析失败() {
        EastmoneyFundGzClient client = mock(EastmoneyFundGzClient.class);
        when(client.fetchGzRaw("000001")).thenReturn("jsonpgz({broken});");

        FundEstimateResult result = service(client).fetchEstimateResult("000001");

        assertThat(result.status()).isEqualTo(EstimateStatus.PARSE_ERROR);
    }

    @Test
    void Feign超时标记为TIMEOUT() {
        EastmoneyFundGzClient client = mock(EastmoneyFundGzClient.class);
        Request request = Request.create(Request.HttpMethod.GET, "https://example.test",
                Map.of(), (byte[]) null, StandardCharsets.UTF_8, new RequestTemplate());
        when(client.fetchGzRaw("000001")).thenThrow(new RetryableException(
                0, "timeout", Request.HttpMethod.GET, new SocketTimeoutException("timeout"), (Long) null, request));

        FundEstimateResult result = service(client).fetchEstimateResult("000001");

        assertThat(result.status()).isEqualTo(EstimateStatus.TIMEOUT);
    }

    private static FundEstimateService service(EastmoneyFundGzClient client) {
        return new FundEstimateService(client, new MarketDataMetrics(new SimpleMeterRegistry()));
    }
}
