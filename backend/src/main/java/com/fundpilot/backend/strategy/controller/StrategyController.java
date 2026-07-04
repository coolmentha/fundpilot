package com.fundpilot.backend.strategy.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.strategy.service.StrategyConfigRequest;
import com.fundpilot.backend.strategy.service.StrategyConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 策略 Controller(issue #16):策略参数 CRUD + 状态机。
 * <p>6 个端点:list/create/update/activate/retire/active。
 * 逻辑下沉 Service,返回 View DTO,不直接暴露 Entity。回测/寻优已随金字塔加仓退场移除。
 */
@RestController
@RequiredArgsConstructor
public class StrategyController {

    private final StrategyConfigService strategyConfigService;

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
