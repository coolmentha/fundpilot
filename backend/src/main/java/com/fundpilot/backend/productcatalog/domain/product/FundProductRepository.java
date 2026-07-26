package com.fundpilot.backend.productcatalog.domain.product;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FundProductRepository {
    Optional<FundProduct> findById(long id);
    Optional<FundProduct> findByFundCode(String fundCode);
    List<FundProduct> findByFundCodes(Set<String> fundCodes);
    List<FundProduct> search(String query, int limit);
    FundProduct save(FundProduct product);
    List<FundProduct> saveAll(List<FundProduct> products);
}
