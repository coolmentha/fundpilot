package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.signal.controller.SignalLogView;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.repository.SignalLogRepository;
import com.fundpilot.backend.fund.enums.TakeProfitPhase;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;

/**
 * 信号查询服务(issue #16):只读 SignalLog 表的查询逻辑下沉。
 * Controller 只做 HTTP 路由,返回 {@link SignalLogView} DTO,不直接暴露 Entity。
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignalQueryService {

    private final SignalLogRepository signalLogRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final SignalActionabilityService signalActionabilityService;
    private final com.fundpilot.backend.fund.service.support.TradingCalendarService tradingCalendarService;
    private final Clock clock;

    /** 今日信号:取北京时间自然日对应的 UTC 00:00 标签起 24 小时区间最后一条。 */
    public SignalLogView today(Long fundId) {
        Instant dayStart = ChinaTradingDate.toUtcDate(clock.instant());
        Instant dayEnd = dayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);
        List<SignalLogEntity> logs = signalLogRepository
                .findByFundEntity_IdAndSignalDateGreaterThanEqualAndSignalDateLessThan(fundId, dayStart, dayEnd);
        if (logs.isEmpty()) {
            return null;
        }
        SignalLogEntity log = logs.get(logs.size() - 1);
        return toViews(List.of(log)).get(0);
    }

    /** 日期范围信号(from/to 为日期字符串,UTC 0 点起算,含 from 含 to)。 */
    public List<SignalLogView> range(Long fundId, String from, String to) {
        Instant start = Instant.parse(from + "T00:00:00Z");
        Instant end = Instant.parse(to + "T00:00:00Z").plus(1, java.time.temporal.ChronoUnit.DAYS);
        return toViews(signalLogRepository
                .findByFundEntity_IdAndSignalDateGreaterThanEqualAndSignalDateLessThan(fundId, start, end));
    }

    /**
     * 跨基金未回应信号工作台:非 NONE 信号倒序前 100(NONE 无需确认,见 Repository 注释)。
     */
    public List<SignalLogView> pending() {
        Instant today = ChinaTradingDate.toUtcDate(clock.instant());
        Instant latestTradingDay = tradingCalendarService.latestTradingDayBefore(today)
                .orElse(today);
        LinkedHashMap<Long, SignalLogEntity> merged = new LinkedHashMap<>();
        signalLogRepository.findRecentPendingSignals(
                        SignalType.NONE, latestTradingDay, PageRequest.of(0, 100))
                .forEach(signal -> merged.put(signal.getId(), signal));
        signalLogRepository.findTriggeredPendingSignals(SignalType.NONE, TakeProfitPhase.TRIGGERED)
                .forEach(signal -> merged.put(signal.getId(), signal));
        return merged.values().stream()
                .filter(signalActionabilityService::isActionable)
                .sorted(Comparator.comparing(SignalLogEntity::getSignalDate).reversed())
                .limit(100)
                .map(signal -> SignalLogView.from(
                        signal, com.fundpilot.backend.signal.enums.SignalActionStatus.PENDING))
                .toList();
    }

    private List<SignalLogView> toViews(List<SignalLogEntity> logs) {
        if (logs.isEmpty()) {
            return List.of();
        }
        List<Long> ids = logs.stream().map(SignalLogEntity::getId).toList();
        Map<Long, FundTransactionEntity> transactions = fundTransactionRepository.findBySignalLogEntity_IdIn(ids)
                .stream().collect(Collectors.toMap(tx -> tx.getSignalLogEntity().getId(), Function.identity()));
        Set<Long> respondedIds = transactions.keySet();
        List<SignalLogView> views = new ArrayList<>(logs.size());
        for (SignalLogEntity log : logs) {
            FundTransactionEntity tx = transactions.get(log.getId());
            SignalLogView view = SignalLogView.from(log, signalActionabilityService.status(log, respondedIds));
            views.add(tx == null ? view : view.withTransaction(tx.getId(), tx.getStatus()));
        }
        return views;
    }
}
