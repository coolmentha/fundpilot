package com.fundpilot.backend.market.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.common.RequiresNewTransactionExecutor;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundNavHistoryEntity;
import com.fundpilot.backend.fund.enums.InvestmentTarget;
import com.fundpilot.backend.fund.repository.FundNavHistoryRepository;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.FundNavUpdatedEvent;
import com.fundpilot.backend.market.client.FundNavSnapshot;
import com.fundpilot.backend.market.client.MarketDataSource;
import com.fundpilot.backend.market.service.support.FundMarketDataCapability;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * 当晚净值确认服务(issue #39):20-23 点每 5 分钟轮询,增量落库已公布净值。
 *
 * <p>场外基金当日净值在收盘后约 20:00 才公布(14:50 定时任务拉到的是 T-1 昨日净值)。
 * 本服务不再用 fundgz 的 jzrq 作为发布门卫，直接拉净值历史并按 remote navDate > local latest
 * 增量写入。FOF/QDII 即使最新公布日期滞后于今天，也能按真实日期正常入库。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyNavConfirmService {

    private final FundRepository fundRepository;
    private final FundNavHistoryRepository fundNavHistoryRepository;
    private final MarketDataSource marketDataSource;
    private final ApplicationEventPublisher eventPublisher;
    private final RequiresNewTransactionExecutor requiresNewTransactionExecutor;
    private final Clock clock;

    /**
     * 遍历所有基金,增量确认已公布净值。供 {@code DailyNavConfirmJob} 每 5 分钟调用。
     */
    public void confirmTodayNav() {
        confirmNavForDate(ChinaTradingDate.toUtcDate(clock.instant()));
    }

    /** 确认指定交易日净值，供晚间当日轮询与次日上午跨夜补拉共用。 */
    public void confirmNavForDate(Instant targetDate) {
        Instant normalizedTargetDate = ChinaTradingDate.toUtcDate(targetDate);
        List<FundTarget> funds = fundRepository.findAll().stream()
                .map(fund -> new FundTarget(fund.getId(), fund.getFundCode(), fund.getFundName(),
                        fund.getInvestmentTarget()))
                .toList();
        int confirmed = 0;
        int skipped = 0;
        for (FundTarget fund : funds) {
            try {
                if (confirmOne(fund, normalizedTargetDate)) {
                    confirmed++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException ex) {
                log.warn("确认基金 {} 净值失败,跳过: {}", fund.id(), ex.getMessage());
                skipped++;
            }
        }
        log.info("交易日 {} 净值确认完成:新落库 {} 只,跳过 {} 只",
                normalizedTargetDate, confirmed, skipped);
    }

    /**
     * @return true=本次新落库了净值;false=无更新或当前产品不支持普通净值,跳过
     */
    private boolean confirmOne(FundTarget fund, Instant targetDate) {
        if (!FundMarketDataCapability.supportsStandardNav(fund.target(), fund.name())) {
            return false;
        }
        Instant localLatest = fundNavHistoryRepository.findFirstByFundEntity_IdOrderByNavDateDesc(fund.id())
                .map(FundNavHistoryEntity::getNavDate).orElse(Instant.MIN);
        if (!localLatest.isBefore(targetDate)) {
            return false;
        }
        List<FundNavSnapshot> navHistory = marketDataSource.fetchNavHistory(fund.code());
        if (navHistory == null || navHistory.isEmpty()) {
            return false;
        }
        List<FundNavSnapshot> candidates = navHistory.stream()
                .filter(snapshot -> snapshot.navDate() != null
                        && snapshot.navDate().isAfter(localLatest)
                        && !snapshot.navDate().isAfter(targetDate))
                .sorted(Comparator.comparing(FundNavSnapshot::navDate))
                .toList();
        if (candidates.isEmpty()) {
            return false;
        }
        return requiresNewTransactionExecutor.execute(() -> persistNewer(fund.id(), candidates));
    }

    private boolean persistNewer(Long fundId, List<FundNavSnapshot> candidates) {
        FundEntity fund = fundRepository.findById(fundId).orElse(null);
        if (fund == null) {
            return false;
        }
        Instant latest = fundNavHistoryRepository.findFirstByFundEntity_IdOrderByNavDateDesc(fundId)
                .map(FundNavHistoryEntity::getNavDate).orElse(Instant.MIN);
        List<FundNavHistoryEntity> toInsert = candidates.stream()
                .filter(snapshot -> snapshot.navDate().isAfter(latest))
                .map(s -> {
                    FundNavHistoryEntity entity = new FundNavHistoryEntity();
                    entity.setFundEntity(fund);
                    entity.setNavDate(s.navDate());
                    entity.setNav(s.nav());
                    entity.setAccumulatedNav(s.accumulatedNav());
                    entity.setFirstSeenAt(clock.instant());
                    return entity;
                })
                .toList();
        if (!toInsert.isEmpty()) {
            fundNavHistoryRepository.saveAll(toInsert);
            eventPublisher.publishEvent(new FundNavUpdatedEvent(fund.getId()));
            return true;
        }
        return false;
    }

    private record FundTarget(Long id, String code, String name, InvestmentTarget target) {
    }
}
