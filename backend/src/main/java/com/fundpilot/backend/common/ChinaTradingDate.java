package com.fundpilot.backend.common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * A 股业务自然日与数据库 DATE 标签之间的统一转换。
 * 数据库存储 UTC 00:00 Instant 表示日期，业务日期按 Asia/Shanghai 判断。
 */
public final class ChinaTradingDate {

    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private ChinaTradingDate() {
    }

    /** 将任意时刻映射为其北京时间自然日对应的 UTC 00:00 Instant。 */
    public static Instant toUtcDate(Instant instant) {
        return instant.atZone(ZONE)
                .truncatedTo(ChronoUnit.DAYS)
                .withZoneSameLocal(ZoneOffset.UTC)
                .toInstant();
    }

    /** 将任意时刻映射为其北京时间前一自然日对应的 UTC 00:00 Instant。 */
    public static Instant previousUtcDate(Instant instant) {
        return instant.atZone(ZONE)
                .minusDays(1)
                .truncatedTo(ChronoUnit.DAYS)
                .withZoneSameLocal(ZoneOffset.UTC)
                .toInstant();
    }
}
