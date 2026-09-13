package com.fundpilot.backend.productcatalog.application.command.researchrefresh;

import com.fundpilot.backend.productcatalog.application.gateway.researchrefresh.PublicDataFirstCollectionEventGateway;
import com.fundpilot.backend.productcatalog.domain.product.FundProduct;
import com.fundpilot.backend.productcatalog.domain.product.FundProductRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 基金公开资料(费率/研究)首采命令:解析基金产品并投递异步首采事件。
 * 永不抛出异常——调用方(跟踪新基金)的事务不受影响;采集失败由夜间批量任务兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublicDataFirstCollectionCommandHandler {
    private final FundProductRepository products;
    private final PublicDataFirstCollectionEventGateway events;

    public void requestFirstCollection(long fundProductId) {
        Optional<FundProduct> product = products.findById(fundProductId);
        if (product.isEmpty()) {
            log.warn("公开资料首采请求找不到基金产品 {},忽略", fundProductId);
            return;
        }
        events.publishRefreshRequested(product.get().fundCode());
    }
}
