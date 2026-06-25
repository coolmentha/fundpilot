package com.fundpilot.backend.strategy.service;

import com.fundpilot.backend.strategy.entity.StrategyBacktestEntity;
import org.springframework.stereotype.Service;

/**
 * {@link StrategyBacktestService} 占位实现(issue #10 引入接口后,需有 bean 让 context 加载)。
 * <p>run 方法暂抛 {@link UnsupportedOperationException},#11 实现真实回测逻辑后替换。
 * #10 的测试用 {@code @MockitoBean} 替换本实现,不会触发真实调用。
 */
@Service
public class DefaultStrategyBacktestService implements StrategyBacktestService {

    @Override
    public StrategyBacktestEntity run(Long strategyId, BacktestWindow window) {
        throw new UnsupportedOperationException("回测引擎待 issue #11 实现");
    }
}
