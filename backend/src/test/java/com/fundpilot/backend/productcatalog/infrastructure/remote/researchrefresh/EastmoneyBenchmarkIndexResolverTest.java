package com.fundpilot.backend.productcatalog.infrastructure.remote.researchrefresh;

import feign.RetryableException;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EastmoneyBenchmarkIndexResolverTest {

    private static final String DETAIL = """
            {"Success":true,"Datas":{"FCODE":"008888","SHORTNAME":"华夏国证半导体芯片ETF联接C",
            "FTYPE":"指数型-股票","INDEXCODE":"980017","INDEXNAME":"国证半导体芯片指数"}}""";

    private static String detail(String indexCode, String indexName) {
        return "{\"Success\":true,\"Datas\":{\"INDEXCODE\":\"%s\",\"INDEXNAME\":\"%s\"}}"
                .formatted(indexCode, indexName);
    }

    @Test
    void resolvesTrackedIndexToSystemFormat() {
        var client = mock(EastmoneyFundResearchMobileClient.class);
        when(client.fetchDetailInformation("008888")).thenReturn(DETAIL);
        var resolver = new EastmoneyBenchmarkIndexResolver(client);

        assertThat(resolver.resolve("008888")).contains("980017.SZ");
    }

    @Test
    void mapsShanghaiIndexCodeToShSuffix() {
        var client = mock(EastmoneyFundResearchMobileClient.class);
        when(client.fetchDetailInformation("000311")).thenReturn(detail("000300", "沪深300指数"));
        var resolver = new EastmoneyBenchmarkIndexResolver(client);

        assertThat(resolver.resolve("000311")).contains("000300.SH");
    }

    @Test
    void mapsCsiThemeIndexCodeToCsiSuffix() {
        var client = mock(EastmoneyFundResearchMobileClient.class);
        when(client.fetchDetailInformation("020464")).thenReturn(detail("931865", "中证半导体产业指数"));
        var resolver = new EastmoneyBenchmarkIndexResolver(client);

        assertThat(resolver.resolve("020464")).contains("931865.CSI");
    }

    @Test
    void activeFundWithoutTrackedIndexReturnsEmpty() {
        var client = mock(EastmoneyFundResearchMobileClient.class);
        when(client.fetchDetailInformation("000001"))
                .thenReturn("{\"Success\":true,\"Datas\":{\"INDEXCODE\":\"--\",\"INDEXNAME\":\"--\"}}");
        var resolver = new EastmoneyBenchmarkIndexResolver(client);

        assertThat(resolver.resolve("000001")).isEmpty();
    }

    @Test
    void malformedResponseReturnsEmpty() {
        var client = mock(EastmoneyFundResearchMobileClient.class);
        when(client.fetchDetailInformation("000001")).thenReturn("not-json");
        var resolver = new EastmoneyBenchmarkIndexResolver(client);

        assertThat(resolver.resolve("000001")).isEmpty();
    }

    @Test
    void networkFailureReturnsEmpty() {
        var client = mock(EastmoneyFundResearchMobileClient.class);
        feign.Request request = feign.Request.create(feign.Request.HttpMethod.GET,
                "https://fundmobapi.eastmoney.com", Map.of(), (byte[]) null, StandardCharsets.UTF_8,
                new feign.RequestTemplate());
        when(client.fetchDetailInformation("000001")).thenThrow(new RetryableException(0, "timeout",
                feign.Request.HttpMethod.GET, new SocketTimeoutException("timeout"), (Long) null, request));
        var resolver = new EastmoneyBenchmarkIndexResolver(client);

        assertThat(resolver.resolve("000001")).isEmpty();
    }
}