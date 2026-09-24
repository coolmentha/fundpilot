package com.fundpilot.backend.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 行情外部数据源的调用埋点:记录每次外部调用的耗时与调用次数。
 * <p>由 {@code MarketDataSourceChain}、实时估值缓存与估值服务调用,本类只做记录,不判断采集端是否存在。
 * <p>指标:{@code market_data_external_duration}(Timer)、{@code market_data_external_calls}(Counter),
 * tag 为 source/operation/result。
 * <p>经 {@code /actuator/prometheus} 导出;Prometheus/Grafana 抓取栈不随应用部署,埋点保留供随时复用,
 * 勿以「当前无采集端」为由删除。
 */
@Component
@RequiredArgsConstructor
public class MarketDataMetrics {

    private final MeterRegistry meterRegistry;

    public void record(String source, String operation, String result, long startedAtNanos) {
        Timer.builder("market_data_external_duration")
                .tag("source", source)
                .tag("operation", operation)
                .tag("result", result)
                .publishPercentileHistogram()
                .register(meterRegistry)
                .record(Duration.ofNanos(System.nanoTime() - startedAtNanos));
        Counter.builder("market_data_external_calls")
                .tag("source", source)
                .tag("operation", operation)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }
}
