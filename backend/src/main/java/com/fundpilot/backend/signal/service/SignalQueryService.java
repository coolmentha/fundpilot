package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.signal.controller.SignalLogView;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.repository.SignalLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 信号查询服务(issue #16):只读 SignalLog 表的查询逻辑下沉。
 * Controller 只做 HTTP 路由,返回 {@link SignalLogView} DTO,不直接暴露 Entity。
 */
@Service
@RequiredArgsConstructor
public class SignalQueryService {

    private final SignalLogRepository signalLogRepository;

    /** 今日信号:取基金当日(UTC 0 点起 24 小时区间)最后一条。 */
    public SignalLogView today(Long fundId) {
        Instant dayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant dayEnd = dayStart.plus(1, java.time.temporal.ChronoUnit.DAYS);
        List<SignalLogEntity> logs = signalLogRepository.findByFundEntity_IdAndSignalDateBetween(fundId, dayStart, dayEnd);
        return logs.isEmpty() ? null : SignalLogView.from(logs.get(logs.size() - 1));
    }

    /** 日期范围信号(from/to 为日期字符串,UTC 0 点起算,含 from 含 to)。 */
    public List<SignalLogView> range(Long fundId, String from, String to) {
        Instant start = LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = LocalDate.parse(to).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return signalLogRepository.findByFundEntity_IdAndSignalDateBetween(fundId, start, end)
                .stream().map(SignalLogView::from).toList();
    }

    /**
     * 跨基金未回应信号工作台:非 NONE 信号倒序前 100(NONE 无需确认,见 Repository 注释)。
     */
    public List<SignalLogView> pending() {
        return signalLogRepository.findTop100BySignalTypeNotOrderBySignalDateDesc(SignalType.NONE)
                .stream().map(SignalLogView::from).toList();
    }
}
