package com.fundpilot.backend.signal.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.repository.SignalLogRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 信号查询 Controller(issue #16):只读 SignalLog 表,不触发计算。
 * <p>3 个端点:今日信号 / 日期范围信号 / 跨基金未回应信号工作台。
 */
@RestController
public class SignalController {

    private final SignalLogRepository signalLogRepository;

    public SignalController(SignalLogRepository signalLogRepository) {
        this.signalLogRepository = signalLogRepository;
    }

    @GetMapping("/api/funds/{fundId}/signals/today")
    public ApiResponse<SignalLogEntity> today(@PathVariable Long fundId) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd = today.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        List<SignalLogEntity> logs = signalLogRepository.findByFundEntity_IdAndSignalDateBetween(fundId, dayStart, dayEnd);
        return ApiResponse.ok(logs.isEmpty() ? null : logs.get(logs.size() - 1));
    }

    @GetMapping("/api/funds/{fundId}/signals")
    public ApiResponse<List<SignalLogEntity>> range(@PathVariable Long fundId,
                                                    @RequestParam("from") String from,
                                                    @RequestParam("to") String to) {
        Instant start = LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = LocalDate.parse(to).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return ApiResponse.ok(signalLogRepository.findByFundEntity_IdAndSignalDateBetween(fundId, start, end));
    }

    /** 跨基金未回应信号工作台:取所有基金最新信号(本期简化为全量倒序前 100)。 */
    @GetMapping("/api/signals/pending")
    public ApiResponse<List<SignalLogEntity>> pending() {
        return ApiResponse.ok(signalLogRepository.findTop100ByOrderBySignalDateDesc());
    }
}
