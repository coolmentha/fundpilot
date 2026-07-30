package com.fundpilot.backend.productcatalog.infrastructure.persistence.fee;

import com.fundpilot.backend.productcatalog.domain.fee.FundFeeSchedule;
import com.fundpilot.backend.productcatalog.domain.fee.FundFeeScheduleRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
class FundFeeScheduleRepositoryImpl implements FundFeeScheduleRepository {
    private final FundFeeScheduleJpaRepository repository;

    @Override public Optional<FundFeeSchedule> findByFundCode(String fundCode) {
        return repository.findByFundCode(fundCode).map(FundFeeSchedulePersistenceMapper::toDomain);
    }

    @Override public List<String> findAllFundCodes() {
        return repository.findAllFundCodes();
    }

    @Override public FundFeeSchedule save(FundFeeSchedule schedule) {
        FundFeeScheduleJpaEntity entity = schedule.id() == null
                ? FundFeeSchedulePersistenceMapper.toEntity(schedule)
                : repository.findById(schedule.id()).orElseThrow();
        FundFeeSchedulePersistenceMapper.copyMutable(schedule, entity);
        return FundFeeSchedulePersistenceMapper.toDomain(repository.save(entity));
    }
}
