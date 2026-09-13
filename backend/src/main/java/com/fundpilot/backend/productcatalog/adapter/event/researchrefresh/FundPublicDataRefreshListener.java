package com.fundpilot.backend.productcatalog.adapter.event.researchrefresh;

import com.fundpilot.backend.productcatalog.application.command.feerefresh.FundFeeCommandHandler;
import com.fundpilot.backend.productcatalog.application.command.researchrefresh.FundResearchCommandHandler;
import com.fundpilot.backend.productcatalog.application.event.researchrefresh.FundPublicDataRefreshRequestedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * 公开资料首采消费端:新基金被跟踪后立即采一次费率与研究资料,
 * 事务提交后异步执行;任一采集失败只记日志,夜间批量任务会兜底。
 */
@Slf4j
@Component
@RequiredArgsConstructor
class FundPublicDataRefreshListener {
    private final FundFeeCommandHandler feeCommands;
    private final FundResearchCommandHandler researchCommands;

    @ApplicationModuleListener
    public void onRefreshRequested(FundPublicDataRefreshRequestedEvent event) {
        String fundCode = event.fundCode();
        try {
            feeCommands.refresh(fundCode);
        } catch (RuntimeException exception) {
            log.warn("基金 {} 首采费率失败: {}", fundCode, exception.getMessage());
        }
        try {
            researchCommands.refresh(fundCode);
        } catch (RuntimeException exception) {
            log.warn("基金 {} 首采研究资料失败: {}", fundCode, exception.getMessage());
        }
    }
}
