package com.fundpilot.backend.strategy.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.strategy.service.BacktestWindow;
import com.fundpilot.backend.strategy.service.StrategyBacktestService;
import com.fundpilot.backend.strategy.service.StrategyConfigRequest;
import com.fundpilot.backend.strategy.service.StrategyConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 策略 Controller(issue #16):策略参数 CRUD + 状态机 + 回测。
 * <p>8 个端点:list/create/update/calibrate/backtest(复跑)/backtests(历史)/activate/retire/active。
 * 逻辑下沉 Service,返回 View DTO,不直接暴露 Entity。
 */
@RestController
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyConfigService strategyConfigService;
    private final StrategyBacktestService strategyBacktestService;

    @GetMapping("/api/funds/{fundId}/strategies")
    public ApiResponse<List<FundStrategyView>> listByFund(@PathVariable Long fundId) {
        return ApiResponse.ok(strategyConfigService.listByFundView(fundId));
    }

    @PostMapping("/api/funds/{fundId}/strategies")
    public ApiResponse<Map<String, Long>> create(@PathVariable Long fundId,
                                                 @RequestBody StrategyConfigRequest request) {
        Long id = strategyConfigService.createDraft(fundId, request);
        return ApiResponse.ok(Map.of("id", id));
    }

    @PutMapping("/api/strategies/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody StrategyConfigRequest request) {
        strategyConfigService.updateDraft(id, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/strategies/{id}/calibrate")
    public ApiResponse<Void> calibrate(@PathVariable Long id) {
        strategyConfigService.calibrate(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/strategies/{id}/backtest")
    public ApiResponse<StrategyBacktestView> backtest(@PathVariable Long id) {
        // 复跑回测:默认过去一年窗口(基金成立不满一年时 run 内部自动降级)
        Instant end = Instant.now().plus(1, java.time.temporal.ChronoUnit.DAYS);
        Instant start = end.minus(BacktestWindow.BACKTEST_WINDOW_DAYS, java.time.temporal.ChronoUnit.DAYS);
        return ApiResponse.ok(strategyBacktestService.runView(id, new BacktestWindow(start, end)));
    }

    @GetMapping("/api/strategies/{id}/backtests")
    public ApiResponse<List<StrategyBacktestView>> backtests(@PathVariable Long id) {
        return ApiResponse.ok(strategyConfigService.listBacktests(id));
    }

    @PostMapping("/api/strategies/{id}/activate")
    public ApiResponse<Void> activate(@PathVariable Long id) {
        strategyConfigService.activate(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/strategies/{id}/retire")
    public ApiResponse<Void> retire(@PathVariable Long id) {
        strategyConfigService.retire(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/funds/{fundId}/strategies/active")
    public ApiResponse<FundStrategyView> active(@PathVariable Long fundId) {
        return ApiResponse.ok(strategyConfigService.findActiveView(fundId).orElse(null));
    }
}
