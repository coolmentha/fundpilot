package com.fundpilot.backend.strategy.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.strategy.entity.FundStrategyEntity;
import com.fundpilot.backend.strategy.entity.StrategyBacktestEntity;
import com.fundpilot.backend.strategy.repository.StrategyBacktestRepository;
import com.fundpilot.backend.strategy.service.BacktestWindow;
import com.fundpilot.backend.strategy.service.StrategyBacktestService;
import com.fundpilot.backend.strategy.service.StrategyConfigRequest;
import com.fundpilot.backend.strategy.service.StrategyConfigService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * 策略 Controller(issue #16):策略参数 CRUD + 状态机 + 回测。
 * <p>8 个端点:list/create/update/calibrate/backtest(复跑)/backtests(历史)/activate/retire/active。
 */
@RestController
public class StrategyController {

    private final StrategyConfigService strategyConfigService;
    private final StrategyBacktestService strategyBacktestService;
    private final StrategyBacktestRepository strategyBacktestRepository;
    private final FundRepository fundRepository;

    public StrategyController(StrategyConfigService strategyConfigService,
                              StrategyBacktestService strategyBacktestService,
                              StrategyBacktestRepository strategyBacktestRepository,
                              FundRepository fundRepository) {
        this.strategyConfigService = strategyConfigService;
        this.strategyBacktestService = strategyBacktestService;
        this.strategyBacktestRepository = strategyBacktestRepository;
        this.fundRepository = fundRepository;
    }

    @GetMapping("/api/funds/{fundId}/strategies")
    public ApiResponse<List<FundStrategyEntity>> listByFund(@PathVariable Long fundId) {
        return ApiResponse.ok(strategyConfigService.listByFund(fundId));
    }

    @PostMapping("/api/funds/{fundId}/strategies")
    public ApiResponse<Map<String, Long>> create(@PathVariable Long fundId,
                                                 @RequestBody StrategyConfigRequest request) {
        // issue #16:StrategyConfigRequest 应含 fundCategory/plannedTotalAmount,随策略一并落地基金属性
        applyFundAttrsIfPresent(fundId, request);
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
    public ApiResponse<StrategyBacktestEntity> backtest(@PathVariable Long id) {
        // 复跑回测:默认过去一年窗口(基金成立不满一年时 run 内部自动降级)
        Instant end = LocalDate.now(ZoneOffset.UTC).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant start = end.minus(365, java.time.temporal.ChronoUnit.DAYS);
        StrategyBacktestEntity result = strategyBacktestService.run(id, new BacktestWindow(start, end));
        return ApiResponse.ok(result);
    }

    @GetMapping("/api/strategies/{id}/backtests")
    public ApiResponse<List<StrategyBacktestEntity>> backtests(@PathVariable Long id) {
        return ApiResponse.ok(strategyBacktestRepository.findByFundStrategyEntity_IdOrderByCreatedDateDesc(id));
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
    public ApiResponse<FundStrategyEntity> active(@PathVariable Long fundId) {
        return ApiResponse.ok(strategyConfigService.findActive(fundId).orElse(null));
    }

    /** issue #16:StrategyConfigRequest 含基金属性时一并落地(本期 record 未含,预留扩展钩子)。 */
    private void applyFundAttrsIfPresent(Long fundId, StrategyConfigRequest request) {
        // 当前 StrategyConfigRequest 仅含 10 个策略参数字段,基金属性由 FundController 独立管理
        // 若后续扩展 request 含 fundCategory/plannedTotalAmount,在此落地
    }
}
