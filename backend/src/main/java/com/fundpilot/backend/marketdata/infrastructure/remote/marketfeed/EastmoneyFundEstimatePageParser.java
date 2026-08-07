package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 解析东方财富静态基金估值页。 */
public final class EastmoneyFundEstimatePageParser {

    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private EastmoneyFundEstimatePageParser() {
    }

    /**
     * @return 基金代码到估值行的映射；404 页面、空页面或没有表格时返回空映射
     */
    public static Map<String, FundEstimatePageRow> parse(String html) {
        if (html == null || html.isBlank()) {
            return Map.of();
        }
        Document document = Jsoup.parse(html);
        if (document.selectFirst("#tContent") == null) {
            return Map.of();
        }
        String estimateDate = dateFrom(document.selectFirst("#gsdata"));
        String baseNavDate = dateFrom(document.selectFirst("#dwjzdata"));
        if (estimateDate == null || baseNavDate == null) {
            throw new IllegalStateException("东方财富静态估值页缺少估算日期或净值日期");
        }

        Map<String, FundEstimatePageRow> result = new LinkedHashMap<>();
        for (Element row : document.select("#tableContent tr")) {
            List<Element> cells = row.children().stream()
                    .filter(element -> "td".equals(element.tagName()))
                    .toList();
            if (cells.size() < 10) {
                continue;
            }
            String fundCode = cells.get(2).text().trim();
            BigDecimal changePct = parsePercent(cells.get(5));
            if (!fundCode.matches("\\d{6}") || changePct == null) {
                continue;
            }
            result.put(fundCode, new FundEstimatePageRow(changePct, estimateDate, baseNavDate));
        }
        return Map.copyOf(result);
    }

    private static String dateFrom(Element element) {
        if (element == null) {
            return null;
        }
        Matcher matcher = DATE_PATTERN.matcher(element.text());
        return matcher.find() ? matcher.group() : null;
    }

    private static BigDecimal parsePercent(Element cell) {
        String value = cell.attr("data-gz");
        if (value == null || value.isBlank()) {
            value = cell.text();
        }
        value = value.replace("%", "").trim();
        if (value.isBlank() || value.startsWith("- -") || value.equals("--") || value.equals("---")) {
            return null;
        }
        try {
            return new BigDecimal(value).divide(new BigDecimal("100"), MathContext.DECIMAL64);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
