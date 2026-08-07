package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 腾讯指数行情数据源。
 *
 * <p>仅覆盖带 {@code sh}/ {@code sz} 市场标识的交易所指数；CSI 主题指数
 * ({@code 2.*}) 不在腾讯证券覆盖范围，直接交给后续同花顺/东方财富源。
 * AKShare 的腾讯 A 股历史接口使用同一条日线 HTTP 接口；当前领域只需要
 * benchmark index K 线，因此不额外暴露股票历史/分笔数据。
 * 周/月 K 参考现有中证解析器在日 K 上聚合，保持各数据源的 K 线契约一致。
 */
@Component
@RequiredArgsConstructor
public class TencentIndexMarketDataSource implements MarketDataSource {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final int HISTORY_YEARS = 5;
    private static final int DEFAULT_LIMIT = 400;

    private final TencentIndexClient tencentIndexClient;

    @Override
    public IndexKline fetchIndexKline(String indexCode, String range) {
        return fetchIndexKlineWithPeriod(indexCode, "101", normalizeLimit(range));
    }

    @Override
    public IndexKline fetchIndexKlineWithPeriod(String indexCode, String klt, String lmt) {
        String symbol = toTencentSymbol(indexCode);
        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = end.minusYears(HISTORY_YEARS);
        String raw = tencentIndexClient.fetchKlineRaw(symbol, start.format(YYYYMMDD), end.format(YYYYMMDD));
        IndexKline daily = TencentJsParser.parseIndexKline(raw, symbol);
        IndexKline aggregated = CsindexJsParser.aggregate(daily, periodFromKlt(klt));
        return tail(aggregated, parseLimit(lmt));
    }

    @Override
    public List<FundNavSnapshot> fetchNavHistory(String fundCode) {
        throw new UnsupportedOperationException("腾讯指数源不提供基金净值");
    }

    @Override
    public List<FundDictEntry> fetchFundDict() {
        throw new UnsupportedOperationException("腾讯指数源不提供基金字典");
    }

    /** secid 1.000300/0.399001 → 腾讯 symbol sh000300/sz399001。 */
    static String toTencentSymbol(String indexCode) {
        if (indexCode == null || indexCode.isBlank()) {
            throw new IllegalArgumentException("指数代码为空");
        }
        String value = indexCode.trim().toLowerCase();
        if (value.matches("(?:sh|sz)\\d{6}")) {
            return value;
        }
        int dot = value.indexOf('.');
        if (dot > 0 && dot < value.length() - 1) {
            String market = value.substring(0, dot);
            String code = value.substring(dot + 1);
            return switch (market) {
                case "1" -> "sh" + code;
                case "0" -> "sz" + code;
                case "2" -> throw new UnsupportedOperationException("腾讯不覆盖 CSI 指数: " + indexCode);
                default -> throw new UnsupportedOperationException("腾讯不支持指数市场前缀: " + indexCode);
            };
        }
        if (value.matches("\\d{6}")) {
            return value.startsWith("399") ? "sz" + value : "sh" + value;
        }
        throw new IllegalArgumentException("无法转换腾讯指数代码: " + indexCode);
    }

    private static String normalizeLimit(String range) {
        return "6".equals(range) ? Integer.toString(DEFAULT_LIMIT) : range;
    }

    private static int parseLimit(String value) {
        try {
            int limit = Integer.parseInt(value);
            return limit > 0 ? limit : DEFAULT_LIMIT;
        } catch (RuntimeException e) {
            return DEFAULT_LIMIT;
        }
    }

    private static IndexKline tail(IndexKline kline, int limit) {
        if (kline.bars().size() <= limit) {
            return kline;
        }
        return new IndexKline(kline.bars().subList(kline.bars().size() - limit, kline.bars().size()));
    }

    private static String periodFromKlt(String klt) {
        return switch (klt) {
            case "102" -> "weekly";
            case "103" -> "monthly";
            default -> "daily";
        };
    }
}
