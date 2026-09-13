package com.fundpilot.backend.productcatalog.adapter.api.publicdata;

import com.fundpilot.backend.productcatalog.application.gateway.researchrefresh.PublicDataFirstCollectionEventGateway;
import com.fundpilot.backend.productcatalog.domain.product.FundProduct;
import com.fundpilot.backend.productcatalog.domain.product.FundProductRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 基金公开资料(费率/研究)首采入口,供其他模块在跟踪新基金后调用。
 * 只投递异步事件,不做任何外部调用,永不抛出异常——调用方的事务不受影响;
 * 采集失败由夜间 02:30/04:00 批量任务兜底重试。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundPublicDataApi {
    private final FundProductRepository products;
    private final PublicDataFirstCollectionEventGateway events;

    /** 请求指定基金产品的公开资料异步首采;产品不存在只记日志。 */
    public void requestFirstCollection(long fundProductId) {
        Optional<FundProduct> product = products.findById(fundProductId);
        if (product.isEmpty()) {
            log.warn("公开资料首采请求找不到基金产品 {},忽略", fundProductId);
            return;
        }
        events.publishRefreshRequested(product.get().fundCode());
    }
}
