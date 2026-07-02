package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.client.RedemptionFeeParser;
import com.fundpilot.backend.market.client.RedemptionFeeSnapshot;
import com.fundpilot.backend.market.client.RedemptionFeeTier;
import com.fundpilot.backend.market.client.EastmoneyClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * 赎回费查询服务(issue #60):从东方财富详情页拉取并解析赎回费档位表,供手动赎回软提示。
 * <p>数据缺失(拉取失败/解析无果)时返 {@link RedemptionFeeSnapshot#missing},调用方降级为 warnings,不阻断赎回。
 * 复用 {@link EastmoneyClient} 的限流链路(东方财富数据源统一限流)。
 */
@Service
@RequiredArgsConstructor
public class RedemptionFeeService {

    private static final Logger log = LoggerFactory.getLogger(RedemptionFeeService.class);

    private final EastmoneyClient eastmoneyClient;

    /**
     * 查指定基金的赎回费档位。拉取/解析失败返缺失快照(不抛异常,降级 warnings)。
     *
     * @param fundCode 基金代码
     * @return 赎回费档位快照(缺失时 tiers 空、missing=true)
     */
    public RedemptionFeeSnapshot fetch(String fundCode) {
        try {
            String html = eastmoneyClient.fetchRaw(fundCode + ".html");
            List<RedemptionFeeTier> tiers = RedemptionFeeParser.parse(extractRedemptionSection(html));
            if (tiers.isEmpty()) {
                log.warn("赎回费档位解析无果 fund={} 降级 warnings", fundCode);
                return RedemptionFeeSnapshot.missing(fundCode);
            }
            return new RedemptionFeeSnapshot(fundCode, tiers, false);
        } catch (RuntimeException ex) {
            log.warn("赎回费档位拉取失败 fund={} 降级 warnings: {}", fundCode, ex.getMessage());
            return RedemptionFeeSnapshot.missing(fundCode);
        }
    }

    /**
     * 计算指定持有期的赎回费率:取 holdingDays ≤ 实际持有天的最深档;无匹配档返 0(免赎回费)。
     * 数据缺失返 null(调用方降级)。
     *
     * @param fundCode     基金代码
     * @param holdingDays  实际持有天数
     * @return 适用赎回费率;数据缺失返 null
     */
    public BigDecimal feeRate(String fundCode, int holdingDays) {
        RedemptionFeeSnapshot snapshot = fetch(fundCode);
        if (snapshot.missing()) {
            return null;
        }
        BigDecimal rate = BigDecimal.ZERO;
        for (RedemptionFeeTier tier : snapshot.tiers()) {
            if (holdingDays >= tier.holdingDays()) {
                rate = tier.feeRate();
            } else {
                break;
            }
        }
        return rate;
    }

    /** 从详情页 HTML 截取赎回费相关片段(避免整页解析干扰)。无命中返原文(由 parser 兜底)。 */
    private static String extractRedemptionSection(String html) {
        if (html == null) {
            return "";
        }
        int idx = html.indexOf("赎回费");
        if (idx < 0) {
            return html;
        }
        // 取赎回费关键词后 600 字符(档位表通常在其内)
        int end = Math.min(html.length(), idx + 600);
        return html.substring(idx, end);
    }
}