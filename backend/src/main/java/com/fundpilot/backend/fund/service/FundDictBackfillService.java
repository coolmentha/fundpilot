package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.support.FundSubTypeResult;
import com.fundpilot.backend.market.client.FundDictEntry;
import com.fundpilot.backend.market.client.MarketDataSource;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 基金字典回填服务(issue #8):从东方财富 {@code fundcode_search.js} 全量字典批量识别
 * 已有 fund 表 {@code fund_sub_type IS NULL} 或 {@code benchmark_index_code} 为空的行,
 * 填回 fundSubType + benchmarkIndexCode。
 * <p>调用时机:{@code MarketDataFetchJob} 第一次拉到字典后可触发,或运维手动调。
 * 字典无匹配 fundCode 的行跳过,不报错(可能基金已下架)。
 */
@Service
@RequiredArgsConstructor
public class FundDictBackfillService {

    private static final Logger log = LoggerFactory.getLogger(FundDictBackfillService.class);

    private final MarketDataSource marketDataSource;
    private final FundRepository fundRepository;

    /**
     * 拉取全量字典,对 {@code fund_sub_type IS NULL} 或 {@code benchmark_index_code} 为空的 fund 行按字典名识别并填回。
     *
     * @return 实际填回的行数
     */
    @Transactional
    public int backfillAll() {
        List<FundDictEntry> dict = marketDataSource.fetchFundDict();
        if (dict == null || dict.isEmpty()) {
            log.warn("字典为空,跳过 backfill");
            return 0;
        }
        Map<String, FundDictEntry> dictByCode = dict.stream()
                .collect(Collectors.toMap(FundDictEntry::fundCode, Function.identity(), (a, b) -> a));

        List<FundEntity> pending = fundRepository.findAll().stream()
                .filter(fund -> fund.getFundSubType() == null || isBlank(fund.getBenchmarkIndexCode()))
                .toList();
        int updated = 0;
        for (FundEntity fund : pending) {
            FundDictEntry entry = dictByCode.get(fund.getFundCode());
            if (entry == null) {
                log.debug("fund_code={} 在字典中无匹配,跳过", fund.getFundCode());
                continue;
            }
            FundSubTypeResult result = com.fundpilot.backend.fund.service.support.FundSubTypeClassifier
                    .classify(entry.fundName());
            boolean changed = false;
            if (fund.getFundSubType() == null) {
                fund.setFundSubType(result.fundSubType());
                changed = true;
            }
            if (isBlank(fund.getBenchmarkIndexCode()) && !isBlank(result.benchmarkIndexCode())) {
                fund.setBenchmarkIndexCode(result.benchmarkIndexCode());
                changed = true;
            }
            if (changed) {
                fundRepository.save(fund);
                updated++;
            }
        }
        log.info("字典 backfill 完成:待识别 {} 只,成功填回 {} 只", pending.size(), updated);
        return updated;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
