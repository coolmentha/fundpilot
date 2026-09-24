package com.fundpilot.backend.platform.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JobMetricsAspectTest {

    private static final Instant NOW = Instant.parse("2026-07-27T00:00:00Z");
    private static final String TASK = "SampleJob.runRefresh";
    private static final String ANNOTATED_TASK = "净值确认";

    /** 仅用于提供声明类型与真实方法对象:runRefresh 无 @JobName,refreshNav 有。 */
    private static final class SampleJob {
        void runRefresh() {
        }

        @JobName("净值确认")
        void refreshNav() {
        }
    }

    @Test
    void recordJob_usesQualifiedTaskTagAndRecordsSuccess() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobExecutionStatusStore statuses = mock(JobExecutionStatusStore.class);
        ProceedingJoinPoint joinPoint = joinPoint("runRefresh");
        when(joinPoint.proceed()).thenReturn(null);

        aspect(registry, statuses).recordJob(joinPoint);

        assertThat(registry.get("job_execution_total")
                .tags("task", TASK, "result", "success")
                .counter().count()).isEqualTo(1.0);
        assertThat(registry.find("job_execution_total").tagKeys("job").counter()).isNull();

        JobExecutionReport report = capture(statuses);
        assertThat(report.task()).isEqualTo(TASK);
        assertThat(report.startedAt()).isEqualTo(NOW);
        assertThat(report.success()).isTrue();
        assertThat(report.failureMessage()).isNull();
        assertThat(report.durationMillis()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void recordJob_prefersJobNameAnnotationOverQualifiedMethodName() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobExecutionStatusStore statuses = mock(JobExecutionStatusStore.class);
        ProceedingJoinPoint joinPoint = joinPoint("refreshNav");
        when(joinPoint.proceed()).thenReturn(null);

        aspect(registry, statuses).recordJob(joinPoint);

        assertThat(registry.get("job_execution_total")
                .tags("task", ANNOTATED_TASK, "result", "success")
                .counter().count()).isEqualTo(1.0);
        assertThat(capture(statuses).task()).isEqualTo(ANNOTATED_TASK);
    }

    @Test
    void recordJob_recordsFailureWithMessageAndRethrows() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobExecutionStatusStore statuses = mock(JobExecutionStatusStore.class);
        ProceedingJoinPoint joinPoint = joinPoint("runRefresh");
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("boom"));

        assertThatThrownBy(() -> aspect(registry, statuses).recordJob(joinPoint))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(registry.get("job_execution_total")
                .tags("task", TASK, "result", "failure")
                .counter().count()).isEqualTo(1.0);

        JobExecutionReport report = capture(statuses);
        assertThat(report.task()).isEqualTo(TASK);
        assertThat(report.success()).isFalse();
        assertThat(report.failureMessage()).isEqualTo("boom");
    }

    @Test
    void recordJob_missingMessage_fallsBackToExceptionSimpleName() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        JobExecutionStatusStore statuses = mock(JobExecutionStatusStore.class);
        ProceedingJoinPoint joinPoint = joinPoint("runRefresh");
        when(joinPoint.proceed()).thenThrow(new IllegalStateException());

        assertThatThrownBy(() -> aspect(registry, statuses).recordJob(joinPoint))
                .isInstanceOf(IllegalStateException.class);

        assertThat(capture(statuses).failureMessage()).isEqualTo("IllegalStateException");
    }

    private static JobMetricsAspect aspect(SimpleMeterRegistry registry, JobExecutionStatusStore statuses) {
        return new JobMetricsAspect(registry, statuses, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static ProceedingJoinPoint joinPoint(String methodName) {
        Method method;
        try {
            method = SampleJob.class.getDeclaredMethod(methodName);
        } catch (NoSuchMethodException exception) {
            throw new IllegalStateException(exception);
        }
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getName()).thenReturn(methodName);
        when(signature.getDeclaringType()).thenReturn(SampleJob.class);
        when(signature.getMethod()).thenReturn(method);
        return joinPoint;
    }

    private static JobExecutionReport capture(JobExecutionStatusStore statuses) {
        ArgumentCaptor<JobExecutionReport> captor = ArgumentCaptor.forClass(JobExecutionReport.class);
        verify(statuses).record(captor.capture());
        return captor.getValue();
    }
}