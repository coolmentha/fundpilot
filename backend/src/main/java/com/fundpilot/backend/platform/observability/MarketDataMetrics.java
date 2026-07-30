package com.fundpilot.backend.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;

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
