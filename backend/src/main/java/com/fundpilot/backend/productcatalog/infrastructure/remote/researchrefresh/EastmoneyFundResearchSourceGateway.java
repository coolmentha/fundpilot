package com.fundpilot.backend.productcatalog.infrastructure.remote.researchrefresh;

import com.fundpilot.backend.productcatalog.application.gateway.researchrefresh.FundResearchSourceGateway;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Holdings;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Industry;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Profile;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Scale;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Source;
import java.time.Clock;
import java.time.Year;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class EastmoneyFundResearchSourceGateway implements FundResearchSourceGateway {
    private static final String BASE = "https://fundf10.eastmoney.com/";
    private final EastmoneyFundResearchClient client;
    private final EastmoneyFundResearchMobileClient mobileClient;
    private final EastmoneyFundResearchIndustryClient industryClient;
    private final Clock clock;

    @Override
    public SourceSnapshot<Profile> fetchProfile(String fundCode) {
        var parsed = FundResearchHtmlParser.parseProfile(client.fetchProfile(fundCode));
        String position = mobileClient.fetchPosition(fundCode);
        if (position == null || position.isBlank()) return null;
        parsed = FundResearchHtmlParser.addTargetEtf(parsed, position);
        return snapshot(parsed,
                BASE + "jbgk_" + fundCode + ".html");
    }

    @Override
    public SourceSnapshot<Scale> fetchScale(String fundCode) {
        return snapshot(FundResearchHtmlParser.parseScale(client.fetchScale(fundCode)),
                BASE + "FundArchivesDatas.aspx?type=gmbd&mode=0&code=" + fundCode);
    }

    @Override
    public SourceSnapshot<Holdings> fetchHoldings(String fundCode) {
        int year = Year.now(clock).getValue();
        var parsed = FundResearchHtmlParser.parseHoldings(client.fetchHoldings(fundCode, year));
        if (parsed == null) {
            year--;
            parsed = FundResearchHtmlParser.parseHoldings(client.fetchHoldings(fundCode, year));
        }
        String allocation = mobileClient.fetchAllocation(fundCode);
        if (allocation == null || allocation.isBlank()) return null;
        parsed = FundResearchHtmlParser.addAllocation(parsed, allocation);
        return snapshot(parsed,
                BASE + "FundArchivesDatas.aspx?type=jjcc&topline=10&code=" + fundCode + "&year=" + year);
    }

    @Override
    public SourceSnapshot<Industry> fetchIndustry(String fundCode) {
        int year = Year.now(clock).getValue();
        var parsed = FundResearchHtmlParser.parseIndustry(industryClient.fetchIndustry(fundCode, year));
        if (parsed == null) {
            year--;
            parsed = FundResearchHtmlParser.parseIndustry(industryClient.fetchIndustry(fundCode, year));
        }
        return snapshot(parsed, "https://api.fund.eastmoney.com/f10/HYPZ/?fundCode="
                + fundCode + "&year=" + year);
    }

    private static <T> SourceSnapshot<T> snapshot(FundResearchHtmlParser.Parsed<T> parsed, String url) {
        if (parsed == null) return null;
        return new SourceSnapshot<>(parsed.data(), new Source("东方财富", url), parsed.reportDate());
    }
}
