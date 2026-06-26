package com.fundpilot.backend.signal.enums;

/**
 * 信号强提示值(issue #12):{@link SignalWarning} 类型 + 可选动态详情。
 * <p>纯枚举值(如 {@code WEEKLY_COOLDOWN})detail 为 {@code null};
 * {@link SignalWarning#TIER_CLEARED} 的 detail 为逗号分隔档位(如 {@code "1,2,3"})。
 * <p>持久化到 {@code signal_log.warnings} 列(逗号连接):detail 为 null 存 {@code name()},
 * 非空存 {@code name() + ":" + detail},与历史字符串格式完全一致。
 *
 * @param warning 强提示类型
 * @param detail  动态详情(可空)
 */
public record SignalWarningValue(SignalWarning warning, String detail) {

    /** 纯枚举值的便捷构造(无 detail)。 */
    public static SignalWarningValue of(SignalWarning warning) {
        return new SignalWarningValue(warning, null);
    }

    /** 带详情的便捷构造。 */
    public static SignalWarningValue of(SignalWarning warning, String detail) {
        return new SignalWarningValue(warning, detail);
    }

    /** 持久化格式:detail 为 null 返回 name(),否则返回 name():detail。 */
    public String toPersistedString() {
        return detail == null ? warning.name() : warning.name() + ":" + detail;
    }
}
