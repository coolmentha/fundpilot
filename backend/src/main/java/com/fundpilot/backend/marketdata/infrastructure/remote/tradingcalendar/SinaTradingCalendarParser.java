package com.fundpilot.backend.marketdata.infrastructure.remote.tradingcalendar;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

public final class SinaTradingCalendarParser {
    private static final LocalDate MISSING_1992_05_04 = LocalDate.of(1992, 5, 4);

    private SinaTradingCalendarParser() {}

    public static List<Instant> parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalStateException("新浪交易日历响应为空");
        }
        String[] assignment = rawText.split("=", 2);
        if (assignment.length != 2) {
            throw new IllegalStateException("新浪交易日历响应格式错误");
        }
        String payload = assignment[1].split(";", 2)[0].replace("\"", "");
        TreeSet<Instant> dates = new TreeSet<>();
        try (Context context = Context.create("js")) {
            context.eval("js", loadDecodeJs());
            Value result = context.getBindings("js").getMember("d").execute(payload);
            if (!result.hasArrayElements() || result.getArraySize() == 0) {
                throw new IllegalStateException("新浪交易日历解码结果为空");
            }
            for (long index = 0; index < result.getArraySize(); index++) {
                Value value = result.getArrayElement(index);
                dates.add(value.isString() ? Instant.parse(value.asString()) : value.asInstant());
            }
        }
        dates.add(MISSING_1992_05_04.atStartOfDay(ZoneOffset.UTC).toInstant());
        return new ArrayList<>(dates);
    }

    private static String loadDecodeJs() {
        try (InputStream input = SinaTradingCalendarParser.class.getResourceAsStream("/hk_js_decode.js")) {
            if (input == null) throw new IllegalStateException("classpath 未找到 hk_js_decode.js");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("加载 hk_js_decode.js 失败", exception);
        }
    }
}
