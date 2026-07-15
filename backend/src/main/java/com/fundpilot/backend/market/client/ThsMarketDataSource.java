package com.fundpilot.backend.market.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ThsMarketDataSource implements MarketDataSource {

    private final ThsClient fundClient;
    private final ThsFundInfoClient fundInfoClient;
    private final ThsIndexClient indexClient;

    @Override
    public List<FundNavSnapshot> fetchNavHistory(String fundCode) {
        return ThsJsParser.parseNavHistory(
                fundClient.fetchUnitNavRaw(fundCode),
                fundClient.fetchAccumulatedNavRaw(fundCode));
    }

    @Override
    public List<FundDictEntry> fetchFundDict() {
        return ThsJsParser.parseFundDict(fundInfoClient.fetchFundDictRaw());
    }

    @Override
    public IndexKline fetchIndexKline(String indexCode, String range) {
        return fetchIndexKlineWithPeriod(indexCode, "101", "400");
    }

    @Override
    public IndexKline fetchIndexKlineWithPeriod(String indexCode, String klt, String lmt) {
        IndexKline daily = ThsJsParser.parseIndexKline(indexClient.fetchDailyKlineRaw(toInternalCode(indexCode)));
        return CsindexJsParser.aggregate(daily, periodFromKlt(klt));
    }

    static String toInternalCode(String indexCode) {
        if (indexCode == null || indexCode.isBlank()) {
            throw new IllegalArgumentException("指数代码为空");
        }
        String code = indexCode;
        String market = "";
        int dot = indexCode.indexOf('.');
        if (dot == 1 && Character.isDigit(indexCode.charAt(0))) {
            String prefix = indexCode.substring(0, dot);
            code = indexCode.substring(dot + 1);
            market = switch (prefix) {
                case "1" -> "SH";
                case "0" -> "SZ";
                case "2" -> "CSI";
                default -> prefix;
            };
        } else if (dot > 0) {
            int suffixDot = indexCode.lastIndexOf('.');
            code = indexCode.substring(0, suffixDot);
            market = indexCode.substring(suffixDot + 1).toUpperCase();
        }
        if ("CSI".equals(market)) {
            return "120_" + code;
        }
        if ("SZ".equals(market) || code.startsWith("399")) {
            return "hs_" + code;
        }
        if ("000001".equals(code)) {
            return "hs_1A0001";
        }
        if (code.startsWith("000") && code.length() == 6) {
            return "hs_1B" + code.substring(2);
        }
        return "120_" + code;
    }

    private static String periodFromKlt(String klt) {
        return switch (klt) {
            case "102" -> "weekly";
            case "103" -> "monthly";
            default -> "daily";
        };
    }
}
