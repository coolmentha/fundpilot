package com.fundpilot.backend.market.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.common.RequiresNewTransactionExecutor;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.FundNavUpdatedEvent;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.client.FundNavSnapshot;
import com.fundpilot.backend.market.client.MarketDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 当晚净值确认服务(issue #39):20-23 点每分钟轮询,确认当日净值落库。
 *
 * <p>场外基金当日净值在收盘后约 20:00 才公布(14:50 定时任务拉到的是 T-1 昨日净值)。
 * 本服务遍历所有未软删基金,查 fund_nav_history 最近一期 navDate ≠ 今天(未确认)→
 * 调 fundgz 判 jzrq 是否 = 今天(轻量判定已公布)→ 是则调 pingzhongdata 拿累计净值落库;
 * 已确认或未公布则跳过。
 *
 * <p>用 fundgz 判定(轻量)+ pingzhongdata 落库(累计净值口径)双接口,保证落库的是累计净值
 * 而非 fundgz 的单位净值 dwjz(fundgz 只给单位净值)。已确认跳过是天然停止条件(全部确认后空跑)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyNavConfirmService {

    private final FundRepository fundRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final FundEstimateService fundEstimateService;
    private final MarketDataSource marketDataSource;
    private final ApplicationEventPublisher eventPublisher;
    private final RequiresNewTransactionExecutor requiresNewTransactionExecutor;
    private final Clock clock;

    /**
     * 遍历所有基金,确认当日净值落库。供 {@code DailyNavConfirmJob} 每分钟调用。
     */
    public void confirmTodayNav() {
        confirmNavForDate(ChinaTradingDate.toUtcDate(clock.instant()));
    }

    /** 确认指定交易日净值，供晚间当日轮询与次日上午跨夜补拉共用。 */
    public void confirmNavForDate(Instant targetDate) {
        Instant normalizedTargetDate = ChinaTradingDate.toUtcDate(targetDate);
        List<Long> fundIds = fundRepository.findAll().stream().map(FundEntity::getId).toList();
        int confirmed = 0;
        int skipped = 0;
        for (Long fundId : fundIds) {
            try {
                if (requiresNewTransactionExecutor.execute(() -> confirmOne(fundId, normalizedTargetDate))) {
                    confirmed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException ex) {
                log.warn("确认基金 {} 当日净值失败,跳过: {}", fundId, ex.getMessage());
                skipped++;
            }
        }
        log.info("交易日 {} 净值确认完成:新落库 {} 只,跳过 {} 只",
                normalizedTargetDate, confirmed, skipped);
    }

    /**
     * @return true=本次新落库了当日净值;false=已确认或未公布,跳过
     */
    private boolean confirmOne(Long fundId, Instant targetDate) {
        FundEntity fund = fundRepository.findById(fundId).orElse(null);
        if (fund == null) {
            return false;
        }
        // 指定交易日净值已落库 → 跳过
        if (isNavConfirmed(fund.getId(), targetDate)) {
            return false;
        }
        // fundgz 判定:jzrq 是否 = 目标交易日(已公布)
        Optional<FundEstimateSnapshot> estimate = fundEstimateService.fetchEstimate(fund.getFundCode());
        if (estimate.isEmpty() || !isBaseNavDate(estimate.get(), targetDate)) {
            return false; // 未公布,跳过
        }
        // 已公布 → pingzhongdata 拿累计净值落库
        List<FundNavSnapshot> navHistory = marketDataSource.fetchNavHistory(fund.getFundCode());
        if (navHistory == null || navHistory.isEmpty()) {
            return false;
        }
        if (navHistory.stream().noneMatch(snapshot -> targetDate.equals(snapshot.navDate()))) {
            return false;
        }
        upsertNavHistory(fund, navHistory);
        return true;
    }

    /** 指定日期净值是否已确认。 */
    private boolean isNavConfirmed(Long fundId, Instant targetDate) {
        return !fundNavHistoryRepository
                .findByFundEntity_IdAndNavDateGreaterThanEqualAndNavDateLessThan(
                        fundId, targetDate, targetDate.plus(1, ChronoUnit.DAYS))
                .isEmpty();
    }

    /** fundgz 的 jzrq(基准净值日期)是否 = 目标交易日。 */
    private boolean isBaseNavDate(FundEstimateSnapshot estimate, Instant targetDate) {
        String baseNavDate = estimate.baseNavDate();
        if (baseNavDate == null || baseNavDate.isBlank()) {
            return false;
        }
        try {
            // jzrq 格式 "2026-06-28" 或 Instant.toString,统一转为 UTC 00:00 日期标签。
            Instant jzrq = Instant.parse(baseNavDate.substring(0, 10) + "T00:00:00Z");
            return jzrq.equals(targetDate);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** 增量落库净值历史(查已落库 navDate,只插不存在的,复用 MarketDataFetchService 逻辑)。 */
    private void upsertNavHistory(FundEntity fund, List<FundNavSnapshot> navHistory) {
        Set<Instant> existing = new HashSet<>(fundNavHistoryRepository.findNavDatesByFundEntity_Id(fund.getId()));
        List<FundNavHistoryEntity> toInsert = navHistory.stream()
                .filter(s -> !existing.contains(s.navDate()))
                .map(s -> {
                    FundNavHistoryEntity entity = new FundNavHistoryEntity();
                    entity.setFundEntity(fund);
                    entity.setNavDate(s.navDate());
                    entity.setNav(s.nav());
                    entity.setAccumulatedNav(s.accumulatedNav());
                    return entity;
                })
                .toList();
        if (!toInsert.isEmpty()) {
            fundNavHistoryRepository.saveAll(toInsert);
            eventPublisher.publishEvent(new FundNavUpdatedEvent(fund.getId()));
        }
    }
}
