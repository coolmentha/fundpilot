package com.fundpilot.backend.platform.observability;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定时任务的可读名称,贴在 {@code @Scheduled} 方法上,供指标标签与 /admin 任务状态展示使用。
 * <p>未标注时回退为「类简名.方法名」(如 NavConfirmJob.run),因此新增 Job 漏贴注解不会导致失败,
 * 只是展示名不够友好。名称需在同一应用内唯一:同一 Job 类的不同方法应各自贴注解。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JobName {

    /** 任务展示名,如「净值确认」。 */
    String value();
}