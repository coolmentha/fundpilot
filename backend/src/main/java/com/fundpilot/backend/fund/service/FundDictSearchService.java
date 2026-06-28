package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.controller.FundDictSearchView;
import com.fundpilot.backend.fund.entity.FundDictEntity;
import com.fundpilot.backend.fund.repository.FundDictRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 基金字典搜索服务(ADR-0005):搜索框自动补全查本地 {@code fund_dict} 表。
 * <p>表为空时首次搜索自动触发 {@link FundDictSyncService#syncAll()} 降级保证可用
 * (用户首次使用不必先跑 admin 同步);同步失败不阻断,返回空列表(前端提示稍后重试)。
 * 搜索结果限制前 20 条,防止全量返回。
 */
@Service
@RequiredArgsConstructor
public class FundDictSearchService {

    private static final Logger log = LoggerFactory.getLogger(FundDictSearchService.class);
    private static final int SEARCH_LIMIT = 20;

    private final FundDictRepository fundDictRepository;
    private final FundDictSyncService fundDictSyncService;

    /**
     * 按代码前缀或名称包含搜索,返回候选列表(最多 20 条)。
     * 表为空时自动触发首次同步(降级),同步失败返回空列表。
     */
    @Transactional
    public List<FundDictSearchView> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        // 表为空时首次自动同步(降级保证可用);同步失败不阻断搜索
        if (fundDictRepository.count() == 0) {
            try {
                log.info("fund_dict 表为空,首次搜索自动触发同步");
                fundDictSyncService.syncAll();
            } catch (RuntimeException ex) {
                log.warn("首次搜索自动同步失败,返回空列表: {}", ex.getMessage());
                return List.of();
            }
        }
        List<FundDictEntity> matches = fundDictRepository.search(q.trim());
        return matches.stream()
                .limit(SEARCH_LIMIT)
                .map(FundDictSearchService::toView)
                .toList();
    }

    private static FundDictSearchView toView(FundDictEntity e) {
        return new FundDictSearchView(
                e.getFundCode(), e.getFundName(),
                e.getFundSubType(), e.getFundCategory(), e.getBenchmarkIndexCode());
    }
}
