package com.fundpilot.backend.productcatalog.application.command.researchrefresh;

import com.fundpilot.backend.productcatalog.application.gateway.researchrefresh.FundResearchSourceGateway.SourceSnapshot;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Holdings;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Industry;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Profile;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Scale;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Source;
import com.fundpilot.backend.productcatalog.domain.research.FundResearchRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class FundResearchWriter {
    private final FundResearchRepository repository;

    @Transactional
    FundResearch writeProfile(String code, SourceSnapshot<Profile> source, Instant fetchedAt) {
        FundResearch research = load(code);
        research.updateProfile(source.data(), source.source(), source.reportDate(), fetchedAt);
        return repository.save(research);
    }

    @Transactional
    FundResearch writeScale(String code, SourceSnapshot<Scale> source, Instant fetchedAt) {
        FundResearch research = load(code);
        research.updateScale(source.data(), source.source(), source.reportDate(), fetchedAt);
        return repository.save(research);
    }

    @Transactional
    FundResearch writeHoldings(String code, Holdings data, SourceSnapshot<Holdings> source, Instant fetchedAt) {
        FundResearch research = load(code);
        research.updateHoldings(data, source.source(), source.reportDate(), fetchedAt);
        return repository.save(research);
    }

    @Transactional
    FundResearch writeIndustry(String code, SourceSnapshot<Industry> source, Instant fetchedAt) {
        FundResearch research = load(code);
        research.updateIndustry(source.data(), source.source(), source.reportDate(), fetchedAt);
        return repository.save(research);
    }

    @Transactional
    FundResearch failProfile(String code, Source source) {
        FundResearch research = load(code);
        research.failProfile(source);
        return repository.save(research);
    }

    @Transactional
    FundResearch failScale(String code, Source source) {
        FundResearch research = load(code);
        research.failScale(source);
        return repository.save(research);
    }

    @Transactional
    FundResearch failHoldings(String code, Source source) {
        FundResearch research = load(code);
        research.failHoldings(source);
        return repository.save(research);
    }

    @Transactional
    FundResearch failIndustry(String code, Source source) {
        FundResearch research = load(code);
        research.failIndustry(source);
        return repository.save(research);
    }

    @Transactional
    FundResearch markAttempt(String code, Instant attemptedAt) {
        FundResearch research = load(code);
        research.markAttempt(attemptedAt);
        return repository.save(research);
    }

    private FundResearch load(String code) {
        return repository.findByFundCode(code).orElseGet(() -> FundResearch.create(code));
    }
}
