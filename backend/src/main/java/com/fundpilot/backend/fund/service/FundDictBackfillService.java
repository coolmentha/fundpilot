package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.support.FundSubTypeResult;
import com.fundpilot.backend.market.client.EastmoneyClient;
import com.fundpilot.backend.market.client.FundDictEntry;
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
 * 已有 fund 表 {@code fund_sub_type IS NULL} 的行,填回 fundSubType + benchmarkIndexCode。
 * <p>调用时机:{@code MarketDataFetchJob} 第一次拉到字典后可触发,或运维手动调。
 * 字典无匹配 fundCode 的行跳过,不报错(可能基金已下架)。
 */
@Service
public class FundDictBackfillService {

    private static final Logger log = LoggerFactory.getLogger(FundDictBackfillService.class);

    private final EastmoneyClient eastmoneyClient;
    private final FundRepository fundRepository;

    public FundDictBackfillService(EastmoneyClient eastmoneyClient, FundRepository fundRepository) {
        this.eastmoneyClient = eastmoneyClient;
        this.fundRepository = fundRepository;
    }

    /**
     * 拉取全量字典,对 {@code fund_sub_type IS NULL} 的 fund 行按字典名识别并填回。
     *
     * @return 实际填回的行数
     */
    @Transactional
    public int backfillAll() {
        List<FundDictEntry> dict = eastmoneyClient.fetchFundDict();
        if (dict == null || dict.isEmpty()) {
            log.warn("字典为空,跳过 backfill");
            return 0;
        }
        Map<String, FundDictEntry> dictByCode = dict.stream()
                .collect(Collectors.toMap(FundDictEntry::fundCode, Function.identity(), (a, b) -> a));

        List<FundEntity> pending = fundRepository.findByFundSubTypeIsNull();
        int updated = 0;
        for (FundEntity fund : pending) {
            FundDictEntry entry = dictByCode.get(fund.getFundCode());
            if (entry == null) {
                log.debug("fund_code={} 在字典中无匹配,跳过", fund.getFundCode());
                continue;
            }
            FundSubTypeResult result = com.fundpilot.backend.fund.service.support.FundSubTypeClassifier
                    .classify(entry.fundName());
            fund.setFundSubType(result.fundSubType());
            fund.setBenchmarkIndexCode(result.benchmarkIndexCode());
            fundRepository.save(fund);
            updated++;
        }
        log.info("字典 backfill 完成:待识别 {} 只,成功填回 {} 只", pending.size(), updated);
        return updated;
    }
}
