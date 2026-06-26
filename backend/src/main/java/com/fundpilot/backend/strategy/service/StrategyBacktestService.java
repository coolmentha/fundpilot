package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.strategy.controller.StrategyBacktestView;
import com.fundpilot.backend.strategy.entity.StrategyBacktestEntity;

/**
 * 回测引擎接口(issue #11):在历史净值序列上模拟执行某版策略参数,
 * 输出策略收益 + 最大回撤,与三条基准线对比,判定 {@code passed}。
 * <p>#10 {@code StrategyConfigService.calibrate} 同步调用此接口落回测留痕。
 * 本接口由 #11 实现具体回测逻辑,#10 通过依赖注入使用(测试可 Mock)。
 */
public interface StrategyBacktestService {

    /**
     * @param strategyId 策略版本 id(对应 {@code FundStrategyEntity.id})
     * @param window     回测窗口;基金成立不满一年时实现内部自动降级起始日期
     * @return 回测结果实体(含三条基准收益/回撤 + passed),已落库
     */
    StrategyBacktestEntity run(Long strategyId, BacktestWindow window);

    /**
     * run 的 DTO 包装(供 Controller 用,返回 {@link StrategyBacktestView})。
     * 默认实现委托 {@link #run} 后映射;测试 Mock 可直接返回 View。
     */
    default StrategyBacktestView runView(Long strategyId, BacktestWindow window) {
        return StrategyBacktestView.from(run(strategyId, window));
    }
}
