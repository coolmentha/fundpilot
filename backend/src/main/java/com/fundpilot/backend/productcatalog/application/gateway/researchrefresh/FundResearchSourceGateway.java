package com.fundpilot.backend.productcatalog.application.gateway.researchrefresh;

import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Holdings;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Industry;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Profile;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Scale;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Source;
import java.time.Instant;

public interface FundResearchSourceGateway {
    SourceSnapshot<Profile> fetchProfile(String fundCode);
    SourceSnapshot<Scale> fetchScale(String fundCode);
    SourceSnapshot<Holdings> fetchHoldings(String fundCode);
    SourceSnapshot<Industry> fetchIndustry(String fundCode);

    record SourceSnapshot<T>(T data, Source source, Instant reportDate) {}
}
