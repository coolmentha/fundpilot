package com.fundpilot.backend.portfolio.application.gateway.fundtracking;

import java.util.List;
import java.util.Set;

/** 组合基金视图读取产品目录所需的最小查询契约。 */
public interface FundProductGateway {
    List<Product> findByIds(Set<Long> ids);

    record Product(long id, String fundCode, String fundName, String productType,
                   String investmentTarget, String benchmarkIndexCode) {
    }
}
