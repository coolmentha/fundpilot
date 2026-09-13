package com.fundpilot.backend.productcatalog.application.gateway.researchrefresh;

/**
 * 基金公开资料(费率+研究)首采请求事件的出站契约。投递实现放在 infrastructure.messaging。
 */
public interface PublicDataFirstCollectionEventGateway {
    void publishRefreshRequested(String fundCode);
}
