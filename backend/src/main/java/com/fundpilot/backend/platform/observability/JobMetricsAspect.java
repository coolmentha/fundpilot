package com.fundpilot.backend.platform.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

/**
 * 定时任务指标切面:零侵入地为所有 {@code @Scheduled} 方法记录耗时与成败。
 * <p>无需在每个 Job 里手写埋点,新增的 @Scheduled 方法自动接入。
 * <p>指标:
 * <ul>
 *   <li>{@code job_duration_seconds}(Timer,tag: task/result)——单次执行耗时</li>
 *   <li>{@code job_execution_total}(Counter,tag: task/result)——累计执行次数</li>
 * </ul>
 * task tag 取 {@link JobName} 注解的中文名(如「净值确认」);未贴注解时回退为「类简名.方法名」
 * (如 NavConfirmJob.run)——仅方法名不足以区分任务,run / refreshDaily / synchronizeDaily 在多个 Job 类中重名。
 * Prometheus 会用 job 作为抓取目标标签,业务指标不能复用该标签名;抓取栈不随应用部署,指标经
 * {@code /actuator/prometheus} 保留导出。
 * 异常重抛不吞,失败仍计入一次 failure。
 * <p>同一结果同时写入 {@link JobExecutionStatusStore},供 /admin「系统监控」展示,这是当前的主要可见通道;
 * 该写入自带独立事务与异常隔离,不影响任务本身与上面的指标。
 */
@Aspect
@Component
@RequiredArgsConstructor
public class JobMetricsAspect {

    private static final String RESULT_SUCCESS = "success";
    private static final String RESULT_FAILURE = "failure";
    private static final long NANOS_PER_MILLI = 1_000_000L;

    private final MeterRegistry meterRegistry;
    private final JobExecutionStatusStore statuses;
    private final Clock clock;

    @Around("@annotation(org.springframework.scheduling.annotation.Scheduled)")
    public Object recordJob(ProceedingJoinPoint pjp) throws Throwable {
        String job = taskName(pjp);
        Instant startedAt = clock.instant();
        long startNanos = System.nanoTime();
        Timer.Sample sample = Timer.start(meterRegistry);
        String result = RESULT_SUCCESS;
        Throwable failure = null;
        try {
            return pjp.proceed();
        } catch (Throwable ex) {
            result = RESULT_FAILURE;
            failure = ex;
            throw ex;
        } finally {
            Instant finishedAt = clock.instant();
            long durationMillis = (System.nanoTime() - startNanos) / NANOS_PER_MILLI;
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
            statuses.record(new JobExecutionReport(job, startedAt, finishedAt, durationMillis,
                    failure == null, failure == null ? null : message(failure)));
        }
    }

    private static String taskName(ProceedingJoinPoint pjp) {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        JobName name = signature.getMethod().getAnnotation(JobName.class);
        if (name != null && !name.value().isBlank()) {
            return name.value();
        }
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    /** 失败摘要:优先取异常消息,消息为空时退回异常类简名,避免丢失失败语义。 */
    private static String message(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
