package com.fundpilot.backend.platform.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JobMetricsAspectTest {

    @Test
    void recordJob_usesTaskTagWithoutConflictingPrometheusJobTag() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        Signature signature = mock(Signature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn("refreshMarketData");
        when(joinPoint.proceed()).thenReturn(null);

        new JobMetricsAspect(registry).recordJob(joinPoint);

        assertThat(registry.get("job_execution_total")
                .tags("task", "refreshMarketData", "result", "success")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.find("job_execution_total").tagKeys("job").counter()).isNull();
    }
}
