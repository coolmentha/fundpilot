package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 中证指数公司行情数据源(实现 {@link MarketDataSource}):借鉴 akshare
 * {@code stock_zh_index_hist_csindex},用 csindex.com.cn 官方接口替代被 IP 限流的东方财富 push2his
 * 拉取指数 K 线。
 *
 * <p><b>仅实现指数 K 线两条线</b>({@link #fetchIndexKline} / {@link #fetchIndexKlineWithPeriod});
 * 基金净值/字典抛 {@link UnsupportedOperationException}——{@link MarketDataSourceChain#tryEach}
 * 对该异常静默跳过,继续尝试后续专业源。故本源置于降级链链首时不污染日志。
 *
 * <p><b>secid 处理</b>:链路传入的是 secid 格式("2.930713"/"1.000300"),中证接口要裸代码,
 * 故 {@link #bareCode(String)} 剥 "X." 前缀。CSI(2.)与沪市中证编制指数(1. 的 000xxx)均在覆盖范围;
 * 深交所(0. 的 399xxx)csindex 返空 data → 解析器抛异常 → 链继续腾讯/同花顺/东方财富。
 *
 * <p><b>周期</b>:中证接口仅提供日 K。{@code fetchIndexKlineWithPeriod} 先拉日 K,
 * 再用 {@link CsindexJsParser#aggregate} 在源内聚合周/月 K(klt 102→weekly、103→monthly),
 * 与 {@code KlineService} 缓存路径的聚合语义一致。
 *
 * <p><b>范围</b>:拉最近 5 年日 K(够算 MA/MACD/周月 K + 填充 index_kline 缓存)。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CsindexMarketDataSource implements MarketDataSource {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    /** 拉取年限:5 年日 K 覆盖周/月 K 图表与 MA/MACD 指标所需历史。 */
    private static final int HISTORY_YEARS = 5;

    private final CsindexClient csindexClient;

    @Override
    public IndexKline fetchIndexKline(String secid, String range) {
        if (secid != null && secid.startsWith("0.")) {
            throw new UnsupportedOperationException("中证源不支持深交所指数");
        }
        String code = bareCode(secid);
        LocalDate end = LocalDate.now(ZoneOffset.UTC);
        LocalDate start = "10".equals(range) ? end.minusDays(30) : end.minusYears(HISTORY_YEARS);
        String raw = csindexClient.fetchIndexPerf(code, start.format(YYYYMMDD), end.format(YYYYMMDD));
        return CsindexJsParser.parseIndexKline(raw, code);
    }

    @Override
    public IndexKline fetchIndexKlineWithPeriod(String secid, String klt, String lmt) {
        // 中证仅日 K:先拉日 K,再按 klt 聚合(101=日、102=周、103=月)
        IndexKline daily = fetchIndexKline(secid, lmt);
        return CsindexJsParser.aggregate(daily, periodFromKlt(klt));
    }

    @Override
    public List<FundNavSnapshot> fetchNavHistory(String fundCode) {
        throw new UnsupportedOperationException("csindex 仅提供指数 K 线,不支持基金净值");
    }

    @Override
    public List<FundDictEntry> fetchFundDict() {
        throw new UnsupportedOperationException("csindex 仅提供指数 K 线,不支持基金字典");
    }

    /** secid "2.930713" / "1.000300" → 裸代码 "930713" / "000300"。无前缀则原样返回。 */
    private static String bareCode(String secid) {
        if (secid == null) {
            throw new IllegalArgumentException("secid 为空");
        }
        int dot = secid.indexOf('.');
        return dot >= 0 && dot < secid.length() - 1 ? secid.substring(dot + 1) : secid;
    }

    /** 东方财富 klt 周期码 → 聚合 period 串。 */
    private static String periodFromKlt(String klt) {
        if (klt == null) {
            return "daily";
        }
        return switch (klt) {
            case "102" -> "weekly";
            case "103" -> "monthly";
            default -> "daily"; // 101 或未知
        };
    }
}
