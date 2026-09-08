package com.fundpilot.backend.productcatalog.infrastructure.persistence.research;

import com.fundpilot.backend.productcatalog.domain.research.FundResearch;
import com.fundpilot.backend.productcatalog.domain.research.FundResearchRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FundResearchRepositoryImpl implements FundResearchRepository {
    private final FundResearchJpaRepository repository;

    @Override
    public Optional<FundResearch> findByFundCode(String fundCode) {
        return repository.findByFundCode(fundCode).map(FundResearchPersistenceMapper::toDomain);
    }

    @Override
    public List<String> findTrackedFundCodes(int limit) {
        return repository.findTrackedFundCodes(PageRequest.of(0, limit));
    }

    @Override
    public FundResearch save(FundResearch research) {
        FundResearchJpaEntity entity = research.id() == null
                ? FundResearchPersistenceMapper.toEntity(research)
                : repository.findById(research.id()).orElseThrow();
        FundResearchPersistenceMapper.copyMutable(research, entity);
        return FundResearchPersistenceMapper.toDomain(repository.save(entity));
    }
}
