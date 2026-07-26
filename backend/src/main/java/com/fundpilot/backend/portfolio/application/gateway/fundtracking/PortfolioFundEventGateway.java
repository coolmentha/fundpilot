package com.fundpilot.backend.portfolio.application.gateway.fundtracking;

import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundTrackedEvent;
import com.fundpilot.backend.portfolio.application.event.portfoliofund.PortfolioFundVoidedEvent;

/**
 * 组合基金跟踪能力对外发布集成事件的出站契约。
 * <p>Handler 只负责生成 {@code application.event} 中的集成事件，投递实现放在
 * {@code infrastructure.messaging}，便于后续替换为持久化事件注册表。
 */
public interface PortfolioFundEventGateway {

    void publishTracked(PortfolioFundTrackedEvent event);

    void publishVoided(PortfolioFundVoidedEvent event);
}
