package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.common.ChinaTradingDate;
import com.fundpilot.backend.fund.enums.TakeProfitPhase;
import com.fundpilot.backend.fund.service.support.TradingCalendarService;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalActionStatus;
import com.fundpilot.backend.signal.enums.SignalReason;
import com.fundpilot.backend.signal.enums.SignalType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

/** 统一判断信号是否仍可操作，供查询、确认和忽略路径复用。 */
@Service
@RequiredArgsConstructor
public class SignalActionabilityService {

    private final TradingCalendarService tradingCalendarService;
    private final Clock clock;

    public SignalActionStatus status(SignalLogEntity signal, Set<Long> respondedIds) {
        if (signal.getSignalType() == SignalType.NONE) {
            return SignalActionStatus.INFORMATIONAL;
        }
        if (respondedIds.contains(signal.getId())) {
            return SignalActionStatus.RESPONDED;
        }
        if (signal.getIgnoredDate() != null) {
            return SignalActionStatus.IGNORED;
        }
        return isActionable(signal) ? SignalActionStatus.PENDING : SignalActionStatus.EXPIRED;
    }

    public boolean isActionable(SignalLogEntity signal) {
        if (isCurrentTriggeredTakeProfit(signal)) {
            return true;
        }
        Instant today = ChinaTradingDate.toUtcDate(clock.instant());
        return tradingCalendarService.latestTradingDayBefore(today)
                .map(latest -> !signal.getSignalDate().isBefore(latest))
                .orElse(false);
    }

    private static boolean isCurrentTriggeredTakeProfit(SignalLogEntity signal) {
        if (signal.getReason() != SignalReason.TRAILING_STOP || signal.getFundStrategyEntity() == null) {
            return false;
        }
        var strategy = signal.getFundStrategyEntity();
        return strategy.getTakeProfitPhase() == TakeProfitPhase.TRIGGERED
                && strategy.getTriggeredSignalId() != null
                && strategy.getTriggeredSignalId().equals(signal.getId());
    }
}
