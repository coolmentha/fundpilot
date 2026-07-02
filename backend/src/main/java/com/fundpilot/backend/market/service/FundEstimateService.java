package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.client.EastmoneyFundGzClient;
import com.fundpilot.backend.market.client.EastmoneyJsParser;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 基金盘中估值拉取服务(issue #36):调 fundgz 接口取盘中估算涨跌幅,
 * 供三态今日涨跌「盘中态」使用(详见 PRD #34 / issue #38)。
 *
 * <p>估值是短时态数据(盘中每分钟变化,收盘后失效),按需实时拉取不落库。
 * 失败降级返 empty(估值拉不到不影响主流程,今日涨跌降级为落库净值算或 0)。
 */
@Service
@RequiredArgsConstructor
public class FundEstimateService {

    private static final Logger log = LoggerFactory.getLogger(FundEstimateService.class);

    private final EastmoneyFundGzClient eastmoneyFundGzClient;

    /**
     * @param fundCode 基金代码
     * @return 盘中估值快照(含估算涨跌幅);拉取失败或解析失败返 empty
     */
    public Optional<FundEstimateSnapshot> fetchEstimate(String fundCode) {
        log.info("拉取基金 {} 盘中估值", fundCode);
        if (fundCode == null || fundCode.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = eastmoneyFundGzClient.fetchGzRaw(fundCode);
            return Optional.ofNullable(EastmoneyJsParser.parseFundGz(raw));
        } catch (RuntimeException ex) {
            log.warn("拉取基金 {} 盘中估值失败,降级返 empty: {}", fundCode, ex.getMessage());
            return Optional.empty();
        }
    }
}
