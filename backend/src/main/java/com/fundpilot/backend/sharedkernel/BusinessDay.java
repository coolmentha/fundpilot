package com.fundpilot.backend.sharedkernel;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * A 股业务自然日与数据库 DATE 标签之间的统一转换规则。
 * <p>数据库以 UTC 00:00 的 {@link Instant} 表示日期标签，业务自然日按 {@code Asia/Shanghai} 判断。
 * 属于跨模块共享的稳定业务规则，因此放在 shared kernel，不依赖任何框架。
 */
public final class BusinessDay {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private BusinessDay() {
    }

    /** 将任意时刻映射为其北京时间自然日对应的 UTC 00:00 Instant。 */
    public static Instant toDateLabel(Instant instant) {
        return instant.atZone(ZONE)
                .truncatedTo(ChronoUnit.DAYS)
                .withZoneSameLocal(ZoneOffset.UTC)
                .toInstant();
    }

    /** 两个时刻之间相差的北京时间自然日天数。 */
    public static long daysBetween(Instant fromInclusive, Instant toInclusive) {
        return ChronoUnit.DAYS.between(toDateLabel(fromInclusive), toDateLabel(toInclusive));
    }
}
