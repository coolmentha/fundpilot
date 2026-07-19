package com.fundpilot.backend.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 定时任务指标切面:零侵入地为所有 {@code @Scheduled} 方法记录耗时与成败。
 * <p>无需在每个 Job 里手写埋点,新增的 @Scheduled 方法自动接入。
 * <p>指标:
 * <ul>
 *   <li>{@code job_duration_seconds}(Timer,tag: task/result)——单次执行耗时</li>
 *   <li>{@code job_execution_total}(Counter,tag: task/result)——累计执行次数</li>
 * </ul>
 * task tag 取方法名(如 fetchBatch0 / generateDailySignals),result 为 success/failure。
 * Prometheus 会用 job 作为抓取目标标签,业务指标不能复用该标签名。
 * 异常重抛不吞,失败仍计入一次 failure。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class JobMetricsAspect {

    private final MeterRegistry meterRegistry;

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object recordJob(ProceedingJoinPoint pjp) throws Throwable {
        String job = pjp.getSignature().getName();
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = "success";
        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            result = "failure";
            throw ex;
        } finally {
            sample.stop(Timer.builder("job_duration_seconds")
                    .tag("task", job)
                    .tag("result", result)
                    .publishPercentileHistogram()
                    .register(meterRegistry));
            Counter.builder("job_execution_total")
                    .tag("task", job)
                    .tag("result", result)
                    .register(meterRegistry)
                    .increment();
        }
    }
}
