package com.fundpilot.backend.signal.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.signal.service.SignalQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 信号查询 Controller(issue #16):只读 SignalLog 表,不触发计算。
 * <p>3 个端点:今日信号 / 日期范围信号 / 跨基金未回应信号工作台。
 * 逻辑下沉 {@link SignalQueryService},返回 {@link SignalLogView} DTO。
 */
@RestController
@RequiredArgsConstructor
public class SignalController {

    private final SignalQueryService signalQueryService;

    @GetMapping("/api/funds/{fundId}/signals/today")
    public ApiResponse<SignalLogView> today(@PathVariable Long fundId) {
        return ApiResponse.ok(signalQueryService.today(fundId));
    }

    @GetMapping("/api/funds/{fundId}/signals")
    public ApiResponse<List<SignalLogView>> range(@PathVariable Long fundId,
                                                  @RequestParam("from") String from,
                                                  @RequestParam("to") String to) {
        return ApiResponse.ok(signalQueryService.range(fundId, from, to));
    }

    /** 跨基金未回应信号工作台:取所有基金最新信号(本期简化为全量倒序前 100)。 */
    @GetMapping("/api/signals/pending")
    public ApiResponse<List<SignalLogView>> pending() {
        return ApiResponse.ok(signalQueryService.pending());
    }
}
