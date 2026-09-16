package com.fundpilot.backend.productcatalog.infrastructure.remote.researchrefresh;

import com.fundpilot.backend.productcatalog.application.gateway.benchmark.BenchmarkIndexResolver;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 基于东方财富基金详情接口(FundMNDetailInformation)解析基金跟踪的基准指数。
 * <p>接口返回 {@code INDEXCODE}(裸 6 位代码,如 "980017")与 {@code INDEXNAME}(如 "国证半导体芯片指数"),
 * 主动/非指数基金为占位符 "--"。解析后转换为系统使用的 {@code 代码.市场} 格式。
 * <p>所有失败(网络/解析/无跟踪标的)一律返回 {@link Optional#empty()},
 * 由调用方回退 {@code ProductClassifier} 关键词分类,不阻断基金创建。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EastmoneyBenchmarkIndexResolver implements BenchmarkIndexResolver {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    /** 接口无跟踪标的时的占位值(主动/债券等非指数基金)。 */
    private static final String NO_INDEX = "--";

    private final EastmoneyFundResearchMobileClient mobileClient;

    @Override
    public Optional<String> resolve(String fundCode) {
        try {
            String raw = mobileClient.fetchDetailInformation(fundCode);
            if (raw == null || raw.isBlank()) return Optional.empty();
            JsonNode datas = JSON.readTree(raw).path("Datas");
            String indexCode = datas.path("INDEXCODE").asText("").trim();
            if (indexCode.isEmpty() || NO_INDEX.equals(indexCode)) return Optional.empty();
            return Optional.of(withMarketSuffix(indexCode));
        } catch (RuntimeException exception) {
            log.warn("解析基金 {} 跟踪指数失败，回退关键词分类", fundCode, exception);
            return Optional.empty();
        }
    }

    /**
     * 东财裸指数代码 → 系统 {@code 代码.市场} 格式(与 K 线链 secid 转换约定一致)。
     * <p>规则(经东财指数接口逐码验证):000xxx 为沪市指数(.SH);399xxx 深证/980xxx 国证为深市(.SZ);
     * 其余(93xxxx/95xxxx/Hxxxxx 等中证系主题指数)为 .CSI。
     */
    private static String withMarketSuffix(String indexCode) {
        String code = indexCode.toUpperCase(Locale.ROOT);
        if (code.startsWith("000")) return code + ".SH";
        if (code.startsWith("399") || code.startsWith("980")) return code + ".SZ";
        return code + ".CSI";
    }
}