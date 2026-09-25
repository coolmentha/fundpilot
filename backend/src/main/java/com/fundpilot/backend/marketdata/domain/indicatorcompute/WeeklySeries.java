package com.fundpilot.backend.marketdata.domain.indicatorcompute;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** 日频序列按「周日为一周结束」聚合成周频序列，取每周最后一个交易日的取值。 */
public final class WeeklySeries {

    private WeeklySeries() {
    }

    public static List<WeekPoint> lastValuePerWeek(List<Instant> dates, List<BigDecimal> values) {
        if (dates.size() != values.size()) {
            throw new IllegalArgumentException("日期与取值长度必须一致");
        }
        Map<Instant, WeekPoint> weekly = new TreeMap<>();
        for (int index = 0; index < dates.size(); index++) {
            Instant weekEnd = weekEnd(dates.get(index));
            weekly.put(weekEnd, new WeekPoint(weekEnd, values.get(index)));
        }
        return List.copyOf(weekly.values());
    }

    private static Instant weekEnd(Instant date) {
        return date.atZone(ZoneOffset.UTC).toLocalDate()
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
                .atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public record WeekPoint(Instant weekEnd, BigDecimal value) {
    }
}