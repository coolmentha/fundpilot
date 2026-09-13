package com.fundpilot.backend.portfolio.application.gateway.fundtracking;

/**
 * 基金公开资料(费率/研究)首采请求的出站契约。
 * 实现只投递异步请求,不阻塞跟踪主流程、不抛出外部采集异常。
 */
public interface PublicDataFirstCollectionGateway {
    void requestFirstCollection(long fundProductId);
}
