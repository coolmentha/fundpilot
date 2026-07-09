package com.fundpilot.backend.market.client;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 新浪交易日历解码器(task 07-09):用 GraalVM JS 跑 {@code hk_js_decode} 解码 KLC 自定义编码,
 * 提取交易日列表。
 * <p>新浪 {@code klc_td_sh.txt} 返回 {@code var datelist="LC/AAA..."} 的 KLC 编码文本,
 * 需 akshare 内嵌的 {@code hk_js_decode}(17KB JS,位流解码)还原为 ISO 日期字符串数组。
 * 项目已有 GraalVM JS 引擎({@link EastmoneyJsParser}),复用同一模式。
 *
 * <p>解码结果仅含交易日(不含非交易日),覆盖 1990-12-19 ~ 当年底。
 * 调休补班周末(如 2024-09-29 周日上班)正确判为非交易日--股市调休补班但休市。
 * 新浪数据缺失 1992-05-04(历史交易日),此处补上(与 akshare 同口径)。
 *
 * <p>解码失败/空结果抛 {@link IllegalStateException}(不降级,避免污染日历;
 * 同步失败由 {@code TradingCalendarSyncJob} 层 catch 记 warn 不阻断)。
 */
public final class SinaTradingCalendarParser {

    /** 新浪数据缺失的历史交易日,akshare 同样手动补上。 */
    private static final LocalDate MISSING_1992_05_04 = LocalDate.of(1992, 5, 4);

    private SinaTradingCalendarParser() {
    }

    /**
     * 解码新浪交易日历原始文本,返回交易日列表(UTC 0 点,升序,含 1992-05-04)。
     *
     * @param rawText {@code klc_td_sh.txt} 原始响应
     * @return 交易日列表;空结果抛异常
     */
    public static List<Instant> parse(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            throw new IllegalStateException("新浪交易日历响应为空");
        }
        // 提取 datelist 载荷:var datelist="LC/AAA...";var KLC_TD_SH=datelist;
        String payload = rawText.split("=")[1].split(";")[0].replace("\"", "");

        String decodeJs = loadDecodeJs();
        TreeSet<Instant> dates = new TreeSet<>();
        try (Context context = Context.create("js")) {
            context.eval("js", decodeJs);
            Value result = context.getBindings("js").getMember("d").execute(payload);
            if (!result.hasArrayElements()) {
                throw new IllegalStateException("新浪交易日历解码结果非数组");
            }
            long size = result.getArraySize();
            if (size == 0) {
                throw new IllegalStateException("新浪交易日历解码结果为空");
            }
            for (long i = 0; i < size; i++) {
                Value element = result.getArrayElement(i);
                // GraalVM JS 会把 ISO 8601 字符串("1990-12-19T00:00:00.000Z")自动识别为 JS Date 对象,
                // 不能直接 asString()。用类型判断兼容:String 直取,Date 取 asInstant()。
                Instant date;
                if (element.isString()) {
                    date = Instant.parse(element.asString());
                } else {
                    date = element.asInstant();
                }
                dates.add(date);
            }
        }

        // 补 1992-05-04(新浪历史缺失,akshare 同口径)
        dates.add(MISSING_1992_05_04.atStartOfDay(ZoneOffset.UTC).toInstant());

        return new ArrayList<>(dates);
    }

    /** 从 classpath 加载 hk_js_decode.js。 */
    private static String loadDecodeJs() {
        try (InputStream in = SinaTradingCalendarParser.class.getResourceAsStream("/hk_js_decode.js")) {
            if (in == null) {
                throw new IllegalStateException("classpath 未找到 hk_js_decode.js");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("加载 hk_js_decode.js 失败", e);
        }
    }
}
