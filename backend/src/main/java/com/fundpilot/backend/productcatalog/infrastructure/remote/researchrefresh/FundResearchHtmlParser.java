package com.fundpilot.backend.productcatalog.infrastructure.remote.researchrefresh;

import com.fundpilot.backend.productcatalog.domain.research.FundResearch.AmountUnit;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.DatedAmount;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Holding;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.HoldingKind;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Holdings;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Industry;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Profile;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Reference;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Scale;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.ShareClass;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.WeightedName;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

final class FundResearchHtmlParser {
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final MathContext MATH = MathContext.DECIMAL64;
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Pattern CODE = Pattern.compile("(?<!\\d)(\\d{5,6})(?!\\d)");
    private static final Pattern NUMBER = Pattern.compile("(-?[0-9]+(?:\\.[0-9]+)?)");
    private static final Pattern PERCENT = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)\\s*%");
    private static final Pattern DATE = Pattern.compile("(20\\d{2})[-年/.](\\d{1,2})[-月/.](\\d{1,2})日?");
    private static final Pattern JSON_OBJECT = Pattern.compile("\\{[^{}]*}");

    private FundResearchHtmlParser() {}

    static Parsed<Profile> parseProfile(String html) {
        if (html == null || html.isBlank()) return null;
        Document doc = Jsoup.parse(html);
        String category = valueAfterLabel(doc, "基金类型");
        String name = firstNonBlank(valueAfterLabel(doc, "基金简称"), doc.selectFirst("h1") == null
                ? null : doc.selectFirst("h1").text());
        Reference index = referenceAfterLabel(doc, "跟踪标的", "跟踪指数");
        Reference target = referenceAfterLabel(doc, "目标ETF", "目标 ETF");
        if (category == null && name == null && index == null && target == null) return null;
        return new Parsed<>(new Profile(category, shareClass(name), index, target,
                date(valueAfterLabel(doc, "发行日期"))), labelledDate(doc));
    }

    static Parsed<Scale> parseScale(String html) {
        if (html == null || html.isBlank()) return null;
        Document doc = Jsoup.parse(html);
        DatedAmount shares = firstAmount(doc, AmountUnit.HUNDRED_MILLION_SHARES,
                "期末总份额", "基金份额", "份额规模");
        DatedAmount categoryAssets = firstNonNull(
                firstAmount(doc, AmountUnit.CNY_100_MILLION, "期末净资产", "净资产规模"),
                labelledAmount(doc, "净资产规模", AmountUnit.CNY_100_MILLION));
        DatedAmount combinedAssets = firstAmount(doc, AmountUnit.CNY_100_MILLION, "合并资产规模");
        if (shares == null && categoryAssets == null && combinedAssets == null) return null;
        Instant reportDate = latest(latest(shares == null ? null : shares.asOf(),
                categoryAssets == null ? null : categoryAssets.asOf()),
                combinedAssets == null ? null : combinedAssets.asOf());
        return new Parsed<>(new Scale(shares, categoryAssets, combinedAssets), reportDate);
    }

    static Parsed<Holdings> parseHoldings(String html) {
        if (html == null || html.isBlank()) return null;
        Document doc = Jsoup.parse(html);
        List<Holding> holdings = new ArrayList<>();
        List<WeightedName> industries = null;
        List<WeightedName> regions = null;
        List<WeightedName> currencies = null;

        List<Element> tables = doc.select("table").stream()
                .filter(FundResearchHtmlParser::isHoldingTable).toList();
        Instant reportDate = tables.stream().map(table -> tableReportDate(table, doc))
                .filter(java.util.Objects::nonNull).max(Instant::compareTo).orElse(null);
        for (Element table : tables) {
            Instant tableDate = tableReportDate(table, doc);
            if (reportDate != null && !reportDate.equals(tableDate)) continue;
            String title = tableTitle(table);
            if (containsAny(title, "股票", "持仓明细", "基金投资")) {
                parseAssetRows(table, holdings, title.contains("基金投资"));
            } else if (title.contains("行业")) {
                industries = parseWeightedNames(table);
            } else if (title.contains("地区") || title.contains("区域")) {
                regions = parseWeightedNames(table);
            } else if (title.contains("币种") || title.contains("货币")) {
                currencies = parseWeightedNames(table);
            }
        }
        if (holdings.isEmpty() && industries == null && regions == null && currencies == null) return null;
        BigDecimal coverage = holdings.stream().map(Holding::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (coverage.compareTo(BigDecimal.ONE) < 0) {
            holdings.add(new Holding(HoldingKind.UNKNOWN, null, "未披露", BigDecimal.ONE.subtract(coverage)));
        }
        return new Parsed<>(new Holdings(holdings, industries, regions, currencies, coverage,
                false, null, FundResearch.LookThroughQuality.DIRECT), reportDate);
    }

    static Parsed<Profile> addTargetEtf(Parsed<Profile> parsed, String positionJson) {
        if (parsed == null) return null;
        if (positionJson == null || positionJson.isBlank()) return parsed;
        String code = jsonString(positionJson, "ETFCODE");
        if (code == null || code.isBlank()) return parsed;
        Profile value = parsed.data();
        return new Parsed<>(new Profile(value.fundCategory(), value.shareClass(), value.trackingIndex(),
                new Reference(code, jsonString(positionJson, "ETFSHORTNAME")), value.launchDate()),
                parsed.reportDate());
    }

    static Parsed<Holdings> addAllocation(Parsed<Holdings> parsed, String allocationJson) {
        if (parsed == null) return null;
        if (allocationJson == null || allocationJson.isBlank()) return parsed;
        Matcher object = JSON_OBJECT.matcher(allocationJson);
        if (!object.find()) return parsed;
        String allocation = object.group();
        Instant allocationDate;
        try {
            allocationDate = date(jsonString(allocation, "FSRQ"));
        } catch (RuntimeException exception) {
            return parsed;
        }
        if (allocationDate == null || !allocationDate.equals(parsed.reportDate())) return parsed;
        List<Holding> values = new ArrayList<>(parsed.data().stockHoldings().stream()
                .filter(value -> value.kind() != HoldingKind.UNKNOWN).toList());
        BigDecimal cashWeight = jsonPercent(allocation, "HB");
        if (cashWeight == null) return parsed;
        if (cashWeight.signum() > 0) {
            values.add(new Holding(HoldingKind.CASH, null, "现金", cashWeight));
        }
        BigDecimal coverage = values.stream().map(Holding::weight).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (coverage.compareTo(BigDecimal.ONE) < 0) {
            values.add(new Holding(HoldingKind.UNKNOWN, null, "未披露", BigDecimal.ONE.subtract(coverage)));
        }
        Holdings original = parsed.data();
        return new Parsed<>(new Holdings(values, original.industryHoldings(),
                original.regionHoldings(), original.currencyHoldings(), coverage, false, null,
                FundResearch.LookThroughQuality.DIRECT),
                parsed.reportDate());
    }

    static Parsed<Industry> parseIndustry(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode latest = null;
            Instant latestDate = null;
            for (JsonNode quarter : JSON.readTree(json).path("Data").path("QuarterInfos")) {
                Instant candidate = date(quarter.path("JZRQ").asText(null));
                if (candidate != null && (latestDate == null || candidate.isAfter(latestDate))) {
                    latest = quarter;
                    latestDate = candidate;
                }
            }
            if (latest == null) return null;
            List<WeightedName> values = new ArrayList<>();
            for (JsonNode item : latest.path("HYPZInfo")) {
                String name = item.path("HYMC").asText(null);
                BigDecimal weight = decimalPercent(item.path("ZJZBL").asText(null));
                if (name != null && !name.isBlank() && weight != null) {
                    values.add(new WeightedName(name, weight));
                }
            }
            return new Parsed<>(new Industry(values), latestDate);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static void parseAssetRows(Element table, List<Holding> result, boolean fundInvestment) {
        for (Element row : table.select("tr")) {
            Elements cells = row.select("td");
            if (cells.size() < 2) continue;
            BigDecimal weight = percent(row.text());
            if (weight == null) continue;
            String text = row.text();
            if (text.contains("现金")) {
                result.add(new Holding(HoldingKind.CASH, null, "现金", weight));
                continue;
            }
            Matcher code = CODE.matcher(text);
            if (!code.find()) continue;
            String name = cells.stream().map(Element::text)
                    .filter(value -> !value.contains(code.group(1)) && percent(value) == null
                            && !value.matches("\\d+"))
                    .findFirst().orElse(code.group(1));
            result.add(new Holding(fundInvestment ? HoldingKind.TARGET_ETF : HoldingKind.STOCK,
                    code.group(1), name, weight));
        }
    }

    private static List<WeightedName> parseWeightedNames(Element table) {
        List<WeightedName> values = new ArrayList<>();
        for (Element row : table.select("tr")) {
            Elements cells = row.select("td");
            if (cells.size() < 2) continue;
            BigDecimal weight = percent(row.text());
            if (weight != null) values.add(new WeightedName(cells.first().text(), weight));
        }
        return List.copyOf(values);
    }

    private static DatedAmount amount(Document doc, String label, AmountUnit unit) {
        for (Element table : doc.select("table")) {
            int column = -1;
            for (Element row : table.select("tr")) {
                Elements cells = row.select("th,td");
                if (column < 0) {
                    for (int index = 0; index < cells.size(); index++) {
                        if (cells.get(index).text().contains(label)) column = index;
                    }
                    continue;
                }
                if (cells.size() <= column) continue;
                Matcher number = NUMBER.matcher(cells.get(column).text().replace(",", ""));
                if (number.find()) return new DatedAmount(new BigDecimal(number.group(1)), unit, date(row.text()));
            }
        }
        return null;
    }

    private static DatedAmount firstAmount(Document doc, AmountUnit unit, String... labels) {
        for (String label : labels) {
            DatedAmount value = amount(doc, label, unit);
            if (value != null) return value;
        }
        return null;
    }

    private static DatedAmount labelledAmount(Document doc, String label, AmountUnit unit) {
        for (Element element : doc.select("label")) {
            String text = element.text().replace(",", "");
            int labelAt = text.indexOf(label);
            if (labelAt < 0) continue;
            Matcher number = NUMBER.matcher(text.substring(labelAt + label.length()));
            if (number.find()) return new DatedAmount(new BigDecimal(number.group(1)), unit, date(text));
        }
        return null;
    }

    private static String valueAfterLabel(Document doc, String label) {
        Element value = elementAfterLabel(doc, label);
        return value == null || value.text().isBlank() ? null : value.text().trim();
    }

    private static Element elementAfterLabel(Document doc, String label) {
        for (Element row : doc.select("tr")) {
            Elements cells = row.select("th,td");
            for (int index = 0; index < cells.size() - 1; index++) {
                if (cells.get(index).text().replace(" ", "").contains(label.replace(" ", ""))) {
                    return cells.get(index + 1);
                }
            }
        }
        return null;
    }

    private static Reference referenceAfterLabel(Document doc, String... labels) {
        for (String label : labels) {
            Element element = elementAfterLabel(doc, label);
            if (element == null) continue;
            String href = element.selectFirst("a") == null ? "" : element.selectFirst("a").attr("href");
            Matcher hrefCode = CODE.matcher(href);
            Reference parsed = parseReference(element.text());
            if (parsed != null && parsed.code() == null && hrefCode.find()) {
                return new Reference(hrefCode.group(1), parsed.name());
            }
            return parsed;
        }
        return null;
    }

    private static Reference parseReference(String value) {
        if (value == null || value.isBlank() || value.contains("无")) return null;
        Matcher matcher = CODE.matcher(value);
        String code = matcher.find() ? matcher.group(1) : null;
        String name = code == null ? value.trim() : value.replace(code, "").replaceAll("^[：:()（）\\s]+|[：:()（）\\s]+$", "");
        return new Reference(code, name.isBlank() ? null : name);
    }

    private static BigDecimal jsonPercent(String json, String field) {
        String value = jsonString(json, field);
        return decimalPercent(value);
    }

    private static BigDecimal decimalPercent(String value) {
        if (value == null || value.equals("--")) return null;
        try {
            return new BigDecimal(value).divide(HUNDRED, MATH);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private static String jsonString(String json, String field) {
        if (json == null) return null;
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
                .matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static ShareClass shareClass(String name) {
        if (name == null) return null;
        String value = name.trim().toUpperCase();
        if (value.endsWith("A") || value.contains("A类")) return ShareClass.A;
        if (value.endsWith("C") || value.contains("C类")) return ShareClass.C;
        return ShareClass.OTHER;
    }

    private static BigDecimal percent(String value) {
        Matcher matcher = PERCENT.matcher(value);
        return matcher.find() ? new BigDecimal(matcher.group(1)).divide(HUNDRED, MATH) : null;
    }

    private static Instant labelledDate(Document doc) {
        String labelled = firstNonBlank(valueAfterLabel(doc, "报告期"), valueAfterLabel(doc, "截止日期"),
                valueAfterLabel(doc, "更新日期"));
        return date(labelled);
    }

    private static Instant date(String value) {
        if (value == null) return null;
        Matcher matcher = DATE.matcher(value);
        if (!matcher.find()) return null;
        return LocalDate.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)),
                Integer.parseInt(matcher.group(3))).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private static Instant latest(Instant left, Instant right) {
        if (left == null) return right;
        if (right == null) return left;
        return left.isAfter(right) ? left : right;
    }

    private static String tableTitle(Element table) {
        String explicit = table.attr("data-kind");
        if (!explicit.isBlank()) return explicit;
        Element previous = table.previousElementSibling();
        return (previous == null ? "" : previous.text()) + " " + table.select("tr").stream()
                .limit(1).map(Element::text).findFirst().orElse("");
    }

    private static boolean isHoldingTable(Element table) {
        String title = tableTitle(table);
        return containsAny(title, "股票", "持仓明细", "基金投资", "行业", "地区", "区域", "币种", "货币");
    }

    private static Instant tableReportDate(Element table, Document doc) {
        Element box = table.closest("div.box");
        Instant result = date(box == null || box.selectFirst("h4") == null
                ? null : box.selectFirst("h4").text());
        if (result != null) return result;
        result = date(tableTitle(table));
        return result == null ? labelledDate(doc) : result;
    }

    private static boolean containsAny(String value, String... needles) {
        for (String needle : needles) if (value.contains(needle)) return true;
        return false;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private static <T> T firstNonNull(T... values) {
        for (T value : values) if (value != null) return value;
        return null;
    }

    record Parsed<T>(T data, Instant reportDate) {}
}
