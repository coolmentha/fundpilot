package com.fundpilot.backend.market.service;

import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.client.FundNavSnapshot;
import com.fundpilot.backend.market.client.MarketDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
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

    /**
     * 遍历所有基金,确认当日净值落库。供 {@code DailyNavConfirmJob} 每分钟调用。
     */
    @Transactional
    public void confirmTodayNav() {
        LocalDate today = ZonedDateTime.now(ZoneOffset.UTC).toLocalDate();
        List<FundEntity> funds = fundRepository.findAll();
        int confirmed = 0;
        int skipped = 0;
        for (FundEntity fund : funds) {
            try {
                if (confirmOne(fund, today)) {
                    confirmed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException ex) {
                log.warn("确认基金 {} 当日净值失败,跳过: {}", fund.getId(), ex.getMessage());
                skipped++;
            }
        }
        log.info("当日净值确认完成:新落库 {} 只,跳过 {} 只", confirmed, skipped);
    }

    /**
     * @return true=本次新落库了当日净值;false=已确认或未公布,跳过
     */
    private boolean confirmOne(FundEntity fund, LocalDate today) {
        // 已确认(最近 navDate = 今天)→ 跳过
        if (isTodayNavConfirmed(fund.getId(), today)) {
            return false;
        }
        // fundgz 判定:jzrq 是否 = 今天(已公布)
        Optional<FundEstimateSnapshot> estimate = fundEstimateService.fetchEstimate(fund.getFundCode());
        if (estimate.isEmpty() || !isJzrqToday(estimate.get(), today)) {
            return false; // 未公布,跳过
        }
        // 已公布 → pingzhongdata 拿累计净值落库
        List<FundNavSnapshot> navHistory = marketDataSource.fetchNavHistory(fund.getFundCode());
        if (navHistory == null || navHistory.isEmpty()) {
            return false;
        }
        upsertNavHistory(fund, navHistory);
        return true;
    }

    /** 最近一期 navDate 是否 = 今天(已确认)。 */
    private boolean isTodayNavConfirmed(Long fundId, LocalDate today) {
        List<FundNavHistoryEntity> latestTwo = fundNavHistoryRepository.findTop2ByFundEntity_IdOrderByNavDateDesc(fundId);
        if (latestTwo.isEmpty()) {
            return false;
        }
        LocalDate latestDate = latestTwo.get(0).getNavDate().atZone(ZoneOffset.UTC).toLocalDate();
        return latestDate.equals(today);
    }

    /** fundgz 的 jzrq(基准净值日期)是否 = 今天(说明当日净值已公布)。 */
    private boolean isJzrqToday(FundEstimateSnapshot estimate, LocalDate today) {
        String baseNavDate = estimate.baseNavDate();
        if (baseNavDate == null || baseNavDate.isBlank()) {
            return false;
        }
        try {
            // jzrq 格式 "2026-06-28" 或 Instant.toString,取前 10 位作 LocalDate
            LocalDate jzrq = LocalDate.parse(baseNavDate.substring(0, Math.min(10, baseNavDate.length())));
            return jzrq.equals(today);
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
        }
    }
}
