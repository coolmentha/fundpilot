package com.fundpilot.backend.productcatalog.infrastructure.remote.feerefresh;

import com.fundpilot.backend.productcatalog.application.gateway.feerefresh.FundFeeSourceGateway.SourceRedemptionTier;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 天天基金 {@code jjfl_<code>.html} 费率页 HTML 解析器(Jsoup)。
 * <p>解析三类数据:
 * <ul>
 *   <li>申购费率:table 首行,strike 标签 = 原费率,| 后 = 优惠费率(天天基金 1折)</li>
 *   <li>赎回费率:table 每行,适用期限(如「小于7天」→ maxDays=7)+ 赎回费率</li>
 *   <li>销售服务费率:运作费用表中「销售服务费率」行的下一列(C类非0)</li>
 * </ul>
 * 所有方法对异常输入返 null/空列表(降级,不抛),由调用方决定是否记 warn。
 */
public final class FundFeeHtmlParser {

    private static final Logger log = LoggerFactory.getLogger(FundFeeHtmlParser.class);
    private static final MathContext MATH = MathContext.DECIMAL64;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final Pattern PERCENT_PATTERN = Pattern.compile("([0-9]+\\.?[0-9]*)\\s*%");
    private static final Pattern LESS_THAN_DAYS = Pattern.compile("小于(\\d+)天");

    private FundFeeHtmlParser() {
    }

    /**
     * 解析申购费率(原 + 优惠),取首档(小于100万元)。
     *
     * @return [originalRate, discountRate];解析失败返 null
     */
    public static PurchaseFeeRate parsePurchaseRate(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        try {
            Document doc = Jsoup.parse(html);
            Element table = findTableAfterH4(doc, "申购费率");
            if (table == null) {
                return null;
            }
            Element firstRow = table.selectFirst("tbody tr");
            if (firstRow == null) {
                return null;
            }
            Elements tds = firstRow.select("td");
            if (tds.size() < 2) {
                return null;
            }
            Element feeTd = tds.get(1);
            Element strike = feeTd.selectFirst("strike");
            BigDecimal original = strike != null ? parsePercent(strike.text()) : null;
            // 优惠费率:td 文本按 | 分割取后半段(如 "1.50% | 0.15%" → "0.15%")
            BigDecimal discount = null;
            String tdText = feeTd.text();
            int pipeIdx = tdText.indexOf('|');
            if (pipeIdx >= 0 && pipeIdx < tdText.length() - 1) {
                discount = parsePercent(tdText.substring(pipeIdx + 1));
            }
            // C 类无 strike 也无 |,单列费率(如 "0.00%"),此时 discount = original = 该值
            if (discount == null && original == null) {
                BigDecimal single = parsePercent(tdText);
                return new PurchaseFeeRate(single, single);
            }
            return new PurchaseFeeRate(original, discount);
        } catch (RuntimeException e) {
            log.warn("申购费率解析失败", e);
            return null;
        }
    }

    /**
     * 解析赎回费率阶梯(按持有期升序)。
     *
     * @return 阶梯列表;解析失败或无表返空列表
     */
    public static List<SourceRedemptionTier> parseRedemptionLadder(String html) {
        if (html == null || html.isBlank()) {
            return List.of();
        }
        try {
            Document doc = Jsoup.parse(html);
            Element table = findTableAfterH4(doc, "赎回费率");
            if (table == null) {
                return List.of();
            }
            List<SourceRedemptionTier> ladder = new ArrayList<>();
            for (Element row : table.select("tbody tr")) {
                Elements tds = row.select("td");
                if (tds.size() < 2) {
                    continue;
                }
                String period = tds.get(0).text();
                BigDecimal rate = parsePercent(tds.get(1).text());
                if (rate == null) {
                    continue;
                }
                Integer maxDays = parseMaxDays(period);
                ladder.add(new SourceRedemptionTier(maxDays, rate));
            }
            return List.copyOf(ladder);
        } catch (RuntimeException e) {
            log.warn("赎回费率阶梯解析失败", e);
            return List.of();
        }
    }

    /**
     * 解析销售服务费率年化(运作费用表中「销售服务费率」行)。
     *
     * @return 费率(小数,如 0.002 表 0.20%/年);解析失败返 null
     */
    public static BigDecimal parseSalesServiceFee(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        try {
            Document doc = Jsoup.parse(html);
            for (Element td : doc.select("td")) {
                if (td.text().contains("销售服务费率")) {
                    Element nextTd = td.nextElementSibling();
                    if (nextTd != null) {
                        return parsePercent(nextTd.text());
                    }
                }
            }
            return null;
        } catch (RuntimeException e) {
            log.warn("销售服务费率解析失败", e);
            return null;
        }
    }

    /**
     * 按 h4 文本关键词定位其后的第一个 table。
     * <p>jjfl 页结构:h4.t > label.left(标题文本) → 同级 div.space0 → table.jjfl。
     * 用 h4.parent().selectFirst("table") 取该 boxitem 内的表。
     */
    private static Element findTableAfterH4(Document doc, String keyword) {
        for (Element h4 : doc.select("h4")) {
            if (h4.text().contains(keyword)) {
                Element table = h4.parent().selectFirst("table");
                if (table != null) {
                    return table;
                }
            }
        }
        return null;
    }

    /**
     * 从文本中提取首个百分比(如 "1.50%" → 0.015、"0.00%（每年）" → 0.0)。
     */
    static BigDecimal parsePercent(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher m = PERCENT_PATTERN.matcher(text);
        if (!m.find()) {
            return null;
        }
        return new BigDecimal(m.group(1)).divide(HUNDRED, MATH);
    }

    /**
     * 从适用期限文本提取 maxDays(持有期上限)。
     * <p>"小于7天" → 7;"大于等于7天，小于30天" → 30;"大于等于730天" → null(最后一档)。
     */
    static Integer parseMaxDays(String period) {
        if (period == null) {
            return null;
        }
        Matcher m = LESS_THAN_DAYS.matcher(period);
        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    /** 申购费率解析结果:原费率 + 优惠费率。 */
    public record PurchaseFeeRate(BigDecimal originalRate, BigDecimal discountRate) {}
}
