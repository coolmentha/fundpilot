package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.entity.FundDictEntity;
import com.fundpilot.backend.fund.repository.FundDictRepository;
import com.fundpilot.backend.fund.service.support.FundTypeClassification;
import com.fundpilot.backend.fund.service.support.FundTypeClassifier;
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
 * 基金字典同步服务(ADR-0005):从东方财富 {@code fundcode_search.js} 拉全量字典,
 * 落 {@code fund_dict} 表当缓存,落库时同步跑 fundSubType + fundCategory 识别并缓存识别结果。
 * <p>触发时机:定时任务每日一次 + admin 手动同步 + 表为空时首次搜索自动触发(降级保证可用)。
 * 字典约 2 万条,同步是全量 upsert(按 fund_code 合并),单次耗时数秒。
 */
@Service
@RequiredArgsConstructor
public class FundDictSyncService {

    private static final Logger log = LoggerFactory.getLogger(FundDictSyncService.class);

    private final MarketDataSource marketDataSource;
    private final FundDictRepository fundDictRepository;

    /**
     * 拉取全量字典并 upsert 到 fund_dict 表(含识别结果缓存)。
     *
     * @return 实际 upsert 的行数
     */
    @Transactional
    public int syncAll() {
        List<FundDictEntry> dict = marketDataSource.fetchFundDict();
        if (dict == null || dict.isEmpty()) {
            log.warn("字典为空,跳过 sync");
            return 0;
        }
        // 现有字典按 code 索引,用于 upsert 合并(避免逐条 findByFundCode 的 N+1)
        Map<String, FundDictEntity> existing = fundDictRepository.findAll().stream()
                .collect(Collectors.toMap(FundDictEntity::getFundCode, Function.identity(), (a, b) -> a));

        int upserted = 0;
        for (FundDictEntry entry : dict) {
            FundTypeClassification result = FundTypeClassifier.classify(entry.fundName());
            FundDictEntity entity = existing.get(entry.fundCode());
            if (entity == null) {
                entity = new FundDictEntity();
                entity.setFundCode(entry.fundCode());
                entity.setFundName(entry.fundName());
                entity.setRawName(entry.rawName());
                entity.setFundSubType(result.fundSubType());
                entity.setFundCategory(result.fundCategory());
                entity.setBenchmarkIndexCode(result.benchmarkIndexCode());
            } else {
                // 已存在:更新名称/识别结果(基金可能更名,识别规则升级后也要重算)
                entity.setFundName(entry.fundName());
                entity.setRawName(entry.rawName());
                entity.setFundSubType(result.fundSubType());
                entity.setFundCategory(result.fundCategory());
                entity.setBenchmarkIndexCode(result.benchmarkIndexCode());
            }
            fundDictRepository.save(entity);
            upserted++;
        }
        log.info("字典同步完成:拉取 {} 条,upsert {} 条", dict.size(), upserted);
        return upserted;
    }
}
