package com.fundpilot.backend.productcatalog.domain.fee;

import java.util.List;
import java.util.Optional;

public interface FundFeeScheduleRepository {
    Optional<FundFeeSchedule> findByFundCode(String fundCode);
    List<String> findAllFundCodes();
    FundFeeSchedule save(FundFeeSchedule schedule);
}
