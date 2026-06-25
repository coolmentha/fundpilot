package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.market.client.EastmoneyClient;
import com.fundpilot.backend.market.client.IndexKline;
import com.fundpilot.backend.strategy.service.support.BenchmarkMetrics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * issue #11 循环 B-2:{@link DefaultHs300BenchmarkProvider} 沪深300 基准线拉取 + 缓存 + 降级。
 */
@ExtendWith(MockitoExtension.class)
class DefaultHs300BenchmarkProviderTest {

    @Mock
    EastmoneyClient eastmoneyClient;

    @InjectMocks
    DefaultHs300BenchmarkProvider provider;

    @Test
    void fetch_正常返回_收益与回撤正确() {
        // 沪深300 日K:2025-01 收盘 4000 → 2025-06 收盘 4400(收益 0.1),中间无回撤
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-06-30T00:00:00Z");
        when(eastmoneyClient.fetchIndexKline(anyString(), anyString()))
                .thenReturn(new IndexKline(List.of(
                        new IndexKline.Bar(LocalDate.of(2025, 1, 5), new BigDecimal("4000"), new BigDecimal("4000"),
                                new BigDecimal("4010"), new BigDecimal("3990"), 1000),
                        new IndexKline.Bar(LocalDate.of(2025, 6, 28), new BigDecimal("4380"), new BigDecimal("4400"),
                                new BigDecimal("4410"), new BigDecimal("4370"), 1000))));

        BenchmarkMetrics metrics = provider.fetch(start, end);

        assertThat(metrics.returnRate()).isCloseTo(new BigDecimal("0.1"), within(new BigDecimal("0.001")));
        assertThat(metrics.maxDrawdown()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void fetch_日期窗口外数据被过滤() {
        // 窗口 2025-03 ~ 2025-06;K 线含窗口外的 2024-12 数据,应被过滤
        Instant start = Instant.parse("2025-03-01T00:00:00Z");
        Instant end = Instant.parse("2025-06-30T00:00:00Z");
        when(eastmoneyClient.fetchIndexKline(anyString(), anyString()))
                .thenReturn(new IndexKline(List.of(
                        new IndexKline.Bar(LocalDate.of(2024, 12, 1), new BigDecimal("3000"), new BigDecimal("3000"),
                                new BigDecimal("3010"), new BigDecimal("2990"), 1000),
                        new IndexKline.Bar(LocalDate.of(2025, 3, 5), new BigDecimal("4000"), new BigDecimal("4000"),
                                new BigDecimal("4010"), new BigDecimal("3990"), 1000),
                        new IndexKline.Bar(LocalDate.of(2025, 6, 28), new BigDecimal("4380"), new BigDecimal("4400"),
                                new BigDecimal("4410"), new BigDecimal("4370"), 1000))));

        BenchmarkMetrics metrics = provider.fetch(start, end);

        // 只用窗口内 2 个点:4000 → 4400,收益 0.1
        assertThat(metrics.returnRate()).isCloseTo(new BigDecimal("0.1"), within(new BigDecimal("0.001")));
    }

    @Test
    void fetch_相同窗口_命中缓存_不重复请求() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-06-30T00:00:00Z");
        when(eastmoneyClient.fetchIndexKline(anyString(), anyString()))
                .thenReturn(new IndexKline(List.of(
                        new IndexKline.Bar(LocalDate.of(2025, 1, 5), new BigDecimal("4000"), new BigDecimal("4000"),
                                new BigDecimal("4010"), new BigDecimal("3990"), 1000),
                        new IndexKline.Bar(LocalDate.of(2025, 6, 28), new BigDecimal("4380"), new BigDecimal("4400"),
                                new BigDecimal("4410"), new BigDecimal("4370"), 1000))));

        BenchmarkMetrics first = provider.fetch(start, end);
        BenchmarkMetrics second = provider.fetch(start, end);

        // 第二次应命中缓存,返回同一实例
        assertThat(second).isSameAs(first);
    }

    @Test
    void fetch_拉取异常_降级返回零指标() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-06-30T00:00:00Z");
        when(eastmoneyClient.fetchIndexKline(anyString(), anyString()))
                .thenThrow(new RuntimeException("网络异常"));

        BenchmarkMetrics metrics = provider.fetch(start, end);

        assertThat(metrics.returnRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(metrics.maxDrawdown()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void fetch_窗口内不足两点_降级返回零指标() {
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        Instant end = Instant.parse("2025-06-30T00:00:00Z");
        when(eastmoneyClient.fetchIndexKline(anyString(), anyString()))
                .thenReturn(new IndexKline(List.of(
                        new IndexKline.Bar(LocalDate.of(2025, 3, 5), new BigDecimal("4000"), new BigDecimal("4000"),
                                new BigDecimal("4010"), new BigDecimal("3990"), 1000))));

        BenchmarkMetrics metrics = provider.fetch(start, end);

        assertThat(metrics.returnRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
