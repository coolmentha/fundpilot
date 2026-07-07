package com.fundpilot.backend.fund.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundpilot.backend.fund.client.EastmoneyFundFeeClient;
import com.fundpilot.backend.fund.client.FundFeeHtmlParser;
import com.fundpilot.backend.fund.client.FundFeeSnapshot;
import com.fundpilot.backend.fund.client.RedemptionTier;
import com.fundpilot.backend.fund.controller.FundFeeView;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundFeeEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.repository.FundFeeRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 基金费率服务:从天天基金 jjfl 页爬取费率,存 {@code fund_fee} 表,查询返 {@link FundFeeSnapshot}。
 * <p>费率慢变(基金合同修改才改),走 DB 缓存(非内存 volatile),每日 06:30 {@code FundFeeRefreshJob} 刷新。
 * 爬取失败/页面无费率 → 返 null 或 {@link FundFeeSnapshot#empty()},调用方降级为不扣费。
 */
@Service
@RequiredArgsConstructor
public class FundFeeService {

    private static final Logger log = LoggerFactory.getLogger(FundFeeService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final EastmoneyFundFeeClient fundFeeClient;
    private final FundFeeRepository fundFeeRepository;
    private final FundRepository fundRepository;

    /**
     * 爬取指定基金的费率并落库(upsert)。
     *
     * @param fundCode 基金代码(如 001071)
     * @return 落库后的快照;爬取失败返 null
     */
    public FundFeeSnapshot fetchAndSave(String fundCode) {
        try {
            String html = fundFeeClient.fetchFeeHtml(fundCode);
            FundFeeHtmlParser.PurchaseFeeRate purchase = FundFeeHtmlParser.parsePurchaseRate(html);
            List<RedemptionTier> ladder = FundFeeHtmlParser.parseRedemptionLadder(html);
            BigDecimal salesServiceFee = FundFeeHtmlParser.parseSalesServiceFee(html);

            // 三项都解析失败 → 页面结构异常或基金不存在,不落库
            if (purchase == null && ladder.isEmpty() && salesServiceFee == null) {
                log.warn("基金 {} 费率页解析全部为空,不落库", fundCode);
                return null;
            }

            BigDecimal originalRate = purchase != null ? purchase.originalRate() : null;
            BigDecimal discountRate = purchase != null ? purchase.discountRate() : null;
            String ladderJson = ladder.isEmpty() ? null : MAPPER.writeValueAsString(ladder);

            FundFeeEntity entity = fundFeeRepository.findByFundCode(fundCode)
                    .orElseGet(() -> {
                        FundFeeEntity e = new FundFeeEntity();
                        e.setFundCode(fundCode);
                        return e;
                    });
            entity.setPurchaseRate(originalRate);
            entity.setDiscountRate(discountRate);
            entity.setSalesServiceFee(salesServiceFee);
            entity.setRedemptionLadder(ladderJson);
            entity.setFetchedAt(Instant.now());
            fundFeeRepository.save(entity);
            log.info("基金 {} 费率落库完成: 申购优惠={} 赎回档数={} 销售服务费={}",
                    fundCode, discountRate, ladder.size(), salesServiceFee);
            return new FundFeeSnapshot(discountRate, ladder, salesServiceFee);
        } catch (RuntimeException e) {
            log.warn("基金 {} 费率爬取失败: {}", fundCode, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("基金 {} 费率爬取异常(JSON 序列化): {}", fundCode, e.getMessage());
            return null;
        }
    }

    /**
     * 查指定基金费率(从 DB 读,不触发爬取)。
     *
     * @param fundCode 基金代码
     * @return 费率快照;无记录返 {@link FundFeeSnapshot#empty()}
     */
    public FundFeeSnapshot getFee(String fundCode) {
        return fundFeeRepository.findByFundCode(fundCode)
                .map(this::toSnapshot)
                .orElse(FundFeeSnapshot.empty());
    }

    /**
     * 按 fundId 查费率(先查 fund.fundCode 再查 fund_fee)。
     */
    public FundFeeSnapshot getFeeByFundId(Long fundId) {
        return fundRepository.findById(fundId)
                .map(FundEntity::getFundCode)
                .map(this::getFee)
                .orElse(FundFeeSnapshot.empty());
    }

    /**
     * 查指定基金费率视图(含原费率,供前端展示)。
     * <p>缓存缺失时按基金代码即时爬取一次并落库,避免详情页长期显示"未爬取"。
     */
    public FundFeeView getFeeView(Long fundId) {
        return fundRepository.findById(fundId)
                .map(FundEntity::getFundCode)
                .map(this::getOrFetchFeeEntity)
                .map(this::toView)
                .orElse(null);
    }

    /**
     * 刷新所有持仓(HOLDING)基金的费率。由 {@code FundFeeRefreshJob} 定时调用。
     * 逐个爬取(受 RateLimiter 2 req/s 限流),单只失败不影响其他。
     */
    @Transactional(readOnly = true)
    public void refreshHoldingFunds() {
        List<FundEntity> funds = fundRepository.findByStatus(FundStatus.HOLDING);
        for (FundEntity fund : funds) {
            try {
                fetchAndSave(fund.getFundCode());
            } catch (RuntimeException e) {
                // 单只失败跳过,继续下一只
            }
        }
        log.info("持仓基金费率刷新完成,共 {} 只", funds.size());
    }

    private FundFeeSnapshot toSnapshot(FundFeeEntity entity) {
        List<RedemptionTier> ladder = parseLadderJson(entity.getRedemptionLadder());
        return new FundFeeSnapshot(entity.getDiscountRate(), ladder, entity.getSalesServiceFee());
    }

    private FundFeeEntity getOrFetchFeeEntity(String fundCode) {
        return fundFeeRepository.findByFundCode(fundCode)
                .orElseGet(() -> {
                    fetchAndSave(fundCode);
                    return fundFeeRepository.findByFundCode(fundCode).orElse(null);
                });
    }

    private FundFeeView toView(FundFeeEntity entity) {
        return new FundFeeView(
                entity.getPurchaseRate(),
                entity.getDiscountRate(),
                entity.getSalesServiceFee(),
                parseLadderJson(entity.getRedemptionLadder()));
    }

    private List<RedemptionTier> parseLadderJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.warn("赎回费率阶梯 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }
}
