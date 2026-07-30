package com.fundpilot.backend.productcatalog.infrastructure.persistence.product;

import com.fundpilot.backend.productcatalog.domain.product.FundProduct;
import com.fundpilot.backend.productcatalog.domain.product.FundProductRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FundProductRepositoryImpl implements FundProductRepository {
    private final FundProductJpaRepository repository;

    @Override public Optional<FundProduct> findById(long id) {
        return repository.findById(id).map(FundProductPersistenceMapper::toDomain);
    }
    @Override public List<FundProduct> findByIds(Set<Long> ids) {
        return ids.isEmpty() ? List.of() : repository.findAllById(ids).stream()
                .map(FundProductPersistenceMapper::toDomain).toList();
    }
    @Override public Optional<FundProduct> findByFundCode(String fundCode) {
        return repository.findByFundCode(fundCode).map(FundProductPersistenceMapper::toDomain);
    }
    @Override public List<FundProduct> findByFundCodes(Set<String> fundCodes) {
        return repository.findByFundCodeIn(fundCodes).stream()
                .map(FundProductPersistenceMapper::toDomain).toList();
    }
    @Override public List<FundProduct> search(String query, int limit) {
        return repository.search(query, PageRequest.of(0, limit)).stream()
                .map(FundProductPersistenceMapper::toDomain).toList();
    }
    @Override public FundProduct save(FundProduct product) {
        FundProductJpaEntity entity = product.id() == null
                ? FundProductPersistenceMapper.toEntity(product)
                : repository.findById(product.id()).orElseThrow();
        FundProductPersistenceMapper.copyMutable(product, entity);
        return FundProductPersistenceMapper.toDomain(repository.save(entity));
    }
    @Override public List<FundProduct> saveAll(List<FundProduct> products) {
        Set<Long> ids = products.stream().map(FundProduct::id)
                .filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, FundProductJpaEntity> existing = repository.findAllById(ids).stream()
                .collect(Collectors.toMap(FundProductJpaEntity::getId, Function.identity()));
        List<FundProductJpaEntity> entities = products.stream().map(product -> {
            FundProductJpaEntity entity = product.id() == null
                    ? FundProductPersistenceMapper.toEntity(product)
                    : existing.get(product.id());
            if (entity == null) throw new IllegalStateException("基金产品不存在: " + product.id());
            FundProductPersistenceMapper.copyMutable(product, entity);
            return entity;
        }).toList();
        return repository.saveAll(entities).stream().map(FundProductPersistenceMapper::toDomain).toList();
    }
}
