package com.fundpilot.backend.market.client;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 赎回费档位解析器(issue #60):从东方财富详情页文本解析赎回费档位表。纯函数。
 * <p>识别两类档位描述(费率形如 X.XX%):
 * <ul>
 *   <li>"少于 N 日 ... X%" / "不足 N 日 ... X%" → holdingDays=1(从首日起算到 N 日)</li>
 *   <li>"不少于 N 日 ... X%" → holdingDays=N(从 N 日起算)</li>
 * </ul>
 * 也支持无持有期前缀的裸费率(如 "赎回费率:0.00%" → 单档 holdingDays=1)。
 * 无匹配返空列表(调用方按数据缺失降级)。
 */
public final class RedemptionFeeParser {

    /**
     * 档位正则:匹配"持有 [不少于|少于|不足] N 日 [少于 M 日] X.XX%"整段。
     * group1=起算持有期方向(不少于/少于/不足),group2=N,group3=费率百分比。
     * "少于/不足 N 日"→ holdingDays=1;"不少于 N 日"→ holdingDays=N。可选的"少于 M 日"上界被消费不产档。
     */
    private static final Pattern TIER_PATTERN = Pattern.compile(
            "(?:持有)?(不少于|少于|不足)\\s*(\\d+)\\s*日(?:[^0-9]*?少于\\s*\\d+\\s*日)?[^0-9]*?(\\d+(?:\\.\\d+)?)\\s*%");
    /** 裸费率(无持有期前缀):作为单档 holdingDays=1。 */
    private static final Pattern BARE_RATE_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%");

    private RedemptionFeeParser() {
    }

    /**
     * @param text 详情页赎回费片段文本
     * @return 档位列表(持有期升序);无匹配返空
     */
    public static List<RedemptionFeeTier> parse(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<RedemptionFeeTier> tiers = new ArrayList<>();
        Matcher m = TIER_PATTERN.matcher(text);
        while (m.find()) {
            String direction = m.group(1);
            int days = Integer.parseInt(m.group(2));
            BigDecimal rate = parseRate(m.group(3));
            // "少于/不足 N 日" 表示首日到 N 日,起算 holdingDays=1;"不少于 N 日" 起算 N
            int holdingDays = "不少于".equals(direction) ? days : 1;
            tiers.add(new RedemptionFeeTier(holdingDays, rate));
        }
        // 按持有期升序
        tiers.sort(java.util.Comparator.comparingInt(RedemptionFeeTier::holdingDays));
        // 无持有期档位但有裸费率 → 单档 holdingDays=1
        if (tiers.isEmpty()) {
            Matcher bare = BARE_RATE_PATTERN.matcher(text);
            if (bare.find()) {
                tiers.add(new RedemptionFeeTier(1, parseRate(bare.group(1))));
            }
        }
        return tiers;
    }

    /** "1.50" + "%" → 0.015。 */
    private static BigDecimal parseRate(String percentText) {
        return new BigDecimal(percentText).divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
    }
}