package com.fundpilot.backend.productcatalog.application.command.researchrefresh;

import com.fundpilot.backend.productcatalog.application.gateway.researchrefresh.FundResearchSourceGateway;
import com.fundpilot.backend.productcatalog.application.gateway.researchrefresh.FundResearchSourceGateway.SourceSnapshot;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.FetchStatus;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Holding;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.HoldingKind;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Holdings;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Industry;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.LookThroughQuality;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Profile;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Reference;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Scale;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.ShareClass;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.Source;
import com.fundpilot.backend.productcatalog.domain.research.FundResearch.WeightedName;
import com.fundpilot.backend.productcatalog.domain.research.FundResearchRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FundResearchCommandHandlerTest {
    private static final String FUND = "001071";
    private static final String OTHER_FUND = "001072";
    private static final String TARGET_ETF = "510500";
    private static final Instant NOW = Instant.parse("2026-07-26T08:00:00Z");
    private static final Instant REPORT_DATE = Instant.parse("2026-06-30T00:00:00Z");
    private static final Instant OLD_REPORT_DATE = Instant.parse("2026-03-31T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Source SOURCE = new Source("东方财富", "https://fundf10.eastmoney.com");

    @Test
    void connectionFundUsesActualEtfWeightAndKeepsBothUnknownParts() {
        var parent = new Holdings(List.of(
                targetHolding(TARGET_ETF, "0.90"),
                new Holding(HoldingKind.CASH, null, "现金", new BigDecimal("0.05")),
                new Holding(HoldingKind.UNKNOWN, null, "未披露", new BigDecimal("0.05"))),
                null, null, null, new BigDecimal("0.95"), false, null, LookThroughQuality.DIRECT);
        var target = new Holdings(List.of(
                stockHolding("600000", "0.50"),
                new Holding(HoldingKind.UNKNOWN, null, "未披露", new BigDecimal("0.50"))),
                List.of(new WeightedName("金融", new BigDecimal("0.50"))),
                null, null, new BigDecimal("0.50"), false, null, LookThroughQuality.DIRECT);

        Holdings result = FundResearchCommandHandler.lookThrough(parent, TARGET_ETF, target, OLD_REPORT_DATE,
                LookThroughQuality.COMPLETE);

        assertThat(result.lookThrough()).isTrue();
        assertThat(result.lookThroughQuality()).isEqualTo(LookThroughQuality.COMPLETE);
        assertThat(result.targetEtfReportDate()).isEqualTo(OLD_REPORT_DATE);
        assertThat(result.stockHoldings()).extracting(Holding::weight)
                .containsExactly(new BigDecimal("0.05"), new BigDecimal("0.5000"),
                        new BigDecimal("0.4500"));
        assertThat(result.disclosedCoverage()).isEqualByComparingTo("0.50");
        assertThat(result.industryHoldings().getFirst().weight()).isEqualByComparingTo("0.4500");
    }

    @Test
    void allSourceFailuresKeepOldSnapshotsAndRecordTheAttempt() {
        FundResearchRepository repository = mock(FundResearchRepository.class);
        FundResearchSourceGateway source = mock(FundResearchSourceGateway.class);
        FundResearch existing = completeResearch(FUND, null, OLD_REPORT_DATE);
        Profile oldProfile = existing.profile().data();
        Scale oldScale = existing.scale().data();
        Holdings oldHoldings = existing.holdings().data();
        Industry oldIndustry = existing.industry().data();
        when(repository.findByFundCode(FUND)).thenReturn(Optional.of(existing));
        when(repository.save(any(FundResearch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(source.fetchProfile(FUND)).thenThrow(new IllegalStateException("profile unavailable"));
        when(source.fetchScale(FUND)).thenThrow(new IllegalStateException("scale unavailable"));
        when(source.fetchHoldings(FUND)).thenThrow(new IllegalStateException("holdings unavailable"));
        when(source.fetchIndustry(FUND)).thenThrow(new IllegalStateException("industry unavailable"));
        var handler = new FundResearchCommandHandler(repository, source,
                new FundResearchWriter(repository), CLOCK);

        handler.refresh(FUND);

        assertThat(existing.profile().data()).isSameAs(oldProfile);
        assertThat(existing.scale().data()).isSameAs(oldScale);
        assertThat(existing.holdings().data()).isSameAs(oldHoldings);
        assertThat(existing.industry().data()).isSameAs(oldIndustry);
        assertThat(List.of(existing.profile().status(), existing.scale().status(),
                existing.holdings().status(), existing.industry().status()))
                .containsOnly(FetchStatus.FAILED);
        assertThat(existing.lastAttemptAt()).isEqualTo(NOW);
        verify(repository, times(5)).save(existing);
    }

    @Test
    void successfulSectionsReplaceDataWhileFailedSectionsKeepOldValues() {
        FundResearchRepository repository = mock(FundResearchRepository.class);
        FundResearchSourceGateway source = mock(FundResearchSourceGateway.class);
        FundResearch existing = completeResearch(FUND, null, OLD_REPORT_DATE);
        Scale oldScale = existing.scale().data();
        Industry oldIndustry = existing.industry().data();
        Profile newProfile = profile(null);
        Holdings newHoldings = directHoldings("600036");
        when(repository.findByFundCode(FUND)).thenReturn(Optional.of(existing));
        when(repository.save(any(FundResearch.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(source.fetchProfile(FUND)).thenReturn(snapshot(newProfile, REPORT_DATE));
        when(source.fetchScale(FUND)).thenThrow(new IllegalStateException("scale unavailable"));
        when(source.fetchHoldings(FUND)).thenReturn(snapshot(newHoldings, REPORT_DATE));
        when(source.fetchIndustry(FUND)).thenThrow(new IllegalStateException("industry unavailable"));
        var handler = new FundResearchCommandHandler(repository, source,
                new FundResearchWriter(repository), CLOCK);

        handler.refresh(FUND);

        assertThat(existing.profile().data()).isSameAs(newProfile);
        assertThat(existing.profile().status()).isEqualTo(FetchStatus.SUCCESS);
        assertThat(existing.holdings().data()).isSameAs(newHoldings);
        assertThat(existing.holdings().status()).isEqualTo(FetchStatus.SUCCESS);
        assertThat(existing.scale().data()).isSameAs(oldScale);
        assertThat(existing.scale().status()).isEqualTo(FetchStatus.FAILED);
        assertThat(existing.industry().data()).isSameAs(oldIndustry);
        assertThat(existing.industry().status()).isEqualTo(FetchStatus.FAILED);
    }

    @Test
    void singleRefreshPropagatesDatabaseFailure() {
        FundResearchRepository repository = mock(FundResearchRepository.class);
        FundResearchSourceGateway source = mock(FundResearchSourceGateway.class);
        FundResearchWriter writer = mock(FundResearchWriter.class);
        SourceSnapshot<Profile> fetched = snapshot(profile(null), REPORT_DATE);
        when(source.fetchProfile(FUND)).thenReturn(fetched);
        when(writer.writeProfile(FUND, fetched, NOW))
                .thenThrow(new IllegalStateException("database unavailable"));
        var handler = new FundResearchCommandHandler(repository, source, writer, CLOCK);

        assertThatThrownBy(() -> handler.refresh(FUND))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        verify(writer, never()).markAttempt(FUND, NOW);
    }

    @Test
    void batchRecordsFailedAttemptAndContinuesWithNextFund() {
        FundResearchRepository repository = mock(FundResearchRepository.class);
        FundResearchSourceGateway source = mock(FundResearchSourceGateway.class);
        FundResearchWriter writer = mock(FundResearchWriter.class);
        when(repository.findTrackedFundCodes(100)).thenReturn(List.of(FUND, OTHER_FUND));
        when(source.fetchProfile(anyString())).thenReturn(snapshot(profile(null), REPORT_DATE));
        when(source.fetchScale(anyString())).thenReturn(snapshot(scale("2"), REPORT_DATE));
        when(source.fetchHoldings(anyString())).thenReturn(snapshot(directHoldings("600000"), REPORT_DATE));
        when(source.fetchIndustry(anyString())).thenReturn(snapshot(industry("金融"), REPORT_DATE));
        when(writer.writeProfile(anyString(), any(), eq(NOW)))
                .thenAnswer(invocation -> researchWithProfile(invocation.getArgument(0), null));
        when(writer.writeProfile(eq(FUND), any(), eq(NOW)))
                .thenThrow(new IllegalStateException("database unavailable"));
        var handler = new FundResearchCommandHandler(repository, source, writer, CLOCK);

        assertThat(handler.refreshTrackedBatch()).isEqualTo(2);

        verify(source).fetchProfile(OTHER_FUND);
        verify(writer).markAttempt(FUND, NOW);
        verify(writer).markAttempt(OTHER_FUND, NOW);
    }

    @Test
    void batchRefreshesSharedTargetOnceWithoutRecursingAndMarksLaterParentStale() {
        FundResearchRepository repository = mock(FundResearchRepository.class);
        FundResearchSourceGateway source = mock(FundResearchSourceGateway.class);
        FundResearchWriter writer = mock(FundResearchWriter.class);
        FundResearch target = FundResearch.create(TARGET_ETF);
        target.updateHoldings(directHoldings("600000"), SOURCE, OLD_REPORT_DATE, NOW);
        when(repository.findTrackedFundCodes(100)).thenReturn(List.of(FUND, OTHER_FUND));
        when(repository.findByFundCode(TARGET_ETF)).thenReturn(Optional.of(target));
        when(source.fetchProfile(FUND)).thenReturn(snapshot(profile(TARGET_ETF), REPORT_DATE));
        when(source.fetchProfile(OTHER_FUND)).thenReturn(snapshot(profile(TARGET_ETF), REPORT_DATE));
        when(source.fetchScale(anyString())).thenReturn(snapshot(scale("2"), REPORT_DATE));
        when(source.fetchHoldings(FUND)).thenReturn(snapshot(linkHoldings(TARGET_ETF), OLD_REPORT_DATE));
        when(source.fetchHoldings(OTHER_FUND)).thenReturn(snapshot(linkHoldings(TARGET_ETF), REPORT_DATE));
        when(source.fetchHoldings(TARGET_ETF)).thenReturn(snapshot(target.holdings().data(), OLD_REPORT_DATE));
        when(source.fetchIndustry(anyString())).thenReturn(snapshot(industry("金融"), REPORT_DATE));
        when(writer.writeProfile(eq(FUND), any(), eq(NOW))).thenReturn(researchWithProfile(FUND, TARGET_ETF));
        when(writer.writeProfile(eq(OTHER_FUND), any(), eq(NOW)))
                .thenReturn(researchWithProfile(OTHER_FUND, TARGET_ETF));
        var handler = new FundResearchCommandHandler(repository, source, writer, CLOCK);

        assertThat(handler.refreshTrackedBatch()).isEqualTo(2);

        verify(source, times(1)).fetchHoldings(TARGET_ETF);
        verify(source, never()).fetchProfile(TARGET_ETF);
        verify(source, never()).fetchScale(TARGET_ETF);
        verify(source, never()).fetchIndustry(TARGET_ETF);
        ArgumentCaptor<Holdings> first = ArgumentCaptor.captor();
        verify(writer).writeHoldings(eq(FUND), first.capture(), any(), eq(NOW));
        assertThat(first.getValue().lookThroughQuality()).isEqualTo(LookThroughQuality.COMPLETE);
        ArgumentCaptor<Holdings> second = ArgumentCaptor.captor();
        verify(writer).writeHoldings(eq(OTHER_FUND), second.capture(), any(), eq(NOW));
        assertThat(second.getValue().lookThroughQuality()).isEqualTo(LookThroughQuality.TARGET_STALE);
    }

    @Test
    void trackedEtfBeforeConnectionFundFetchesSharedHoldingsOnce() {
        assertTrackedTargetOverlap(List.of(TARGET_ETF, FUND), false);
    }

    @Test
    void connectionFundBeforeTrackedEtfFetchesSharedHoldingsOnce() {
        assertTrackedTargetOverlap(List.of(FUND, TARGET_ETF), false);
    }

    @Test
    void failedTrackedEtfHoldingsAreNotRetriedThroughConnectionFund() {
        assertTrackedTargetOverlap(List.of(TARGET_ETF, FUND), true);
    }

    @Test
    void failedTargetKeepsItsOldSnapshotAndMarksParentQualityFailed() {
        FundResearchRepository repository = mock(FundResearchRepository.class);
        FundResearchSourceGateway source = mock(FundResearchSourceGateway.class);
        Map<String, FundResearch> stored = new LinkedHashMap<>();
        stored.put(FUND, completeResearch(FUND, TARGET_ETF, OLD_REPORT_DATE));
        stored.put(TARGET_ETF, completeResearch(TARGET_ETF, null, OLD_REPORT_DATE));
        when(repository.findByFundCode(anyString()))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get(invocation.getArgument(0))));
        when(repository.save(any(FundResearch.class))).thenAnswer(invocation -> {
            FundResearch saved = invocation.getArgument(0);
            stored.put(saved.fundCode(), saved);
            return saved;
        });
        when(source.fetchProfile(FUND)).thenReturn(snapshot(profile(TARGET_ETF), REPORT_DATE));
        when(source.fetchScale(FUND)).thenReturn(snapshot(scale("3"), REPORT_DATE));
        when(source.fetchHoldings(FUND)).thenReturn(snapshot(linkHoldings(TARGET_ETF), REPORT_DATE));
        when(source.fetchHoldings(TARGET_ETF)).thenThrow(new IllegalStateException("target unavailable"));
        when(source.fetchIndustry(FUND)).thenReturn(snapshot(industry("科技"), REPORT_DATE));
        var handler = new FundResearchCommandHandler(repository, source,
                new FundResearchWriter(repository), CLOCK);

        handler.refresh(FUND);

        assertThat(stored.get(TARGET_ETF).holdings().status()).isEqualTo(FetchStatus.FAILED);
        assertThat(stored.get(TARGET_ETF).holdings().data()).isNotNull();
        assertThat(stored.get(FUND).holdings().data().lookThroughQuality())
                .isEqualTo(LookThroughQuality.TARGET_FAILED);
        assertThat(stored.get(FUND).holdings().data().stockHoldings())
                .extracting(Holding::code).contains("600000");
    }

    @Test
    void missingConcreteTargetWeightDoesNotFetchOrLookThroughTarget() {
        FundResearchRepository repository = mock(FundResearchRepository.class);
        FundResearchSourceGateway source = mock(FundResearchSourceGateway.class);
        FundResearchWriter writer = mock(FundResearchWriter.class);
        when(source.fetchProfile(FUND)).thenReturn(snapshot(profile(TARGET_ETF), REPORT_DATE));
        when(source.fetchScale(FUND)).thenReturn(snapshot(scale("2"), REPORT_DATE));
        when(source.fetchHoldings(FUND)).thenReturn(snapshot(directHoldings("600036"), REPORT_DATE));
        when(source.fetchIndustry(FUND)).thenReturn(snapshot(industry("金融"), REPORT_DATE));
        when(writer.writeProfile(eq(FUND), any(), eq(NOW))).thenReturn(researchWithProfile(FUND, TARGET_ETF));
        var handler = new FundResearchCommandHandler(repository, source, writer, CLOCK);

        handler.refresh(FUND);

        verify(source, never()).fetchHoldings(TARGET_ETF);
        ArgumentCaptor<Holdings> written = ArgumentCaptor.captor();
        verify(writer).writeHoldings(eq(FUND), written.capture(), any(), eq(NOW));
        assertThat(written.getValue().lookThrough()).isFalse();
        assertThat(written.getValue().lookThroughQuality())
                .isEqualTo(LookThroughQuality.TARGET_WEIGHT_UNKNOWN);
    }

    private static void assertTrackedTargetOverlap(List<String> order, boolean targetFails) {
        FundResearchRepository repository = mock(FundResearchRepository.class);
        FundResearchSourceGateway source = mock(FundResearchSourceGateway.class);
        FundResearchWriter writer = mock(FundResearchWriter.class);
        FundResearch target = completeResearch(TARGET_ETF, null, REPORT_DATE);
        when(repository.findTrackedFundCodes(100)).thenReturn(order);
        when(repository.findByFundCode(TARGET_ETF))
                .thenReturn(targetFails ? Optional.empty() : Optional.of(target));
        when(source.fetchProfile(FUND)).thenReturn(snapshot(profile(TARGET_ETF), REPORT_DATE));
        when(source.fetchProfile(TARGET_ETF)).thenReturn(snapshot(profile(null), REPORT_DATE));
        when(source.fetchScale(anyString())).thenReturn(snapshot(scale("2"), REPORT_DATE));
        when(source.fetchHoldings(FUND)).thenReturn(snapshot(linkHoldings(TARGET_ETF), REPORT_DATE));
        if (targetFails) {
            when(source.fetchHoldings(TARGET_ETF)).thenThrow(new IllegalStateException("target unavailable"));
        } else {
            when(source.fetchHoldings(TARGET_ETF)).thenReturn(snapshot(target.holdings().data(), REPORT_DATE));
        }
        when(source.fetchIndustry(anyString())).thenReturn(snapshot(industry("金融"), REPORT_DATE));
        when(writer.writeProfile(eq(FUND), any(), eq(NOW))).thenReturn(researchWithProfile(FUND, TARGET_ETF));
        when(writer.writeProfile(eq(TARGET_ETF), any(), eq(NOW)))
                .thenReturn(researchWithProfile(TARGET_ETF, null));
        var handler = new FundResearchCommandHandler(repository, source, writer, CLOCK);

        assertThat(handler.refreshTrackedBatch()).isEqualTo(2);

        verify(source, times(1)).fetchHoldings(TARGET_ETF);
        verify(source).fetchProfile(TARGET_ETF);
        verify(source).fetchScale(TARGET_ETF);
        verify(source).fetchIndustry(TARGET_ETF);
        verify(writer).writeHoldings(eq(FUND), any(), any(), eq(NOW));
        verify(writer).markAttempt(FUND, NOW);
        verify(writer).markAttempt(TARGET_ETF, NOW);
        if (targetFails) {
            verify(writer).failHoldings(TARGET_ETF, SOURCE);
        } else {
            verify(writer).writeHoldings(eq(TARGET_ETF), any(), any(), eq(NOW));
        }
    }

    private static FundResearch completeResearch(String code, String targetEtf, Instant reportDate) {
        FundResearch research = researchWithProfile(code, targetEtf);
        research.updateScale(scale("1"), SOURCE, reportDate, NOW.minusSeconds(3600));
        research.updateHoldings(directHoldings("600000"), SOURCE, reportDate, NOW.minusSeconds(3600));
        research.updateIndustry(industry("旧行业"), SOURCE, reportDate, NOW.minusSeconds(3600));
        return research;
    }

    private static FundResearch researchWithProfile(String code, String targetEtf) {
        FundResearch research = FundResearch.create(code);
        research.updateProfile(profile(targetEtf), SOURCE, OLD_REPORT_DATE, NOW.minusSeconds(3600));
        return research;
    }

    private static Profile profile(String targetEtf) {
        return new Profile("指数型", ShareClass.A, new Reference("000905.SH", "中证500"),
                targetEtf == null ? null : new Reference(targetEtf, "目标ETF"),
                Instant.parse("2020-01-01T00:00:00Z"));
    }

    private static Scale scale(String value) {
        return new Scale(null, null, new FundResearch.DatedAmount(new BigDecimal(value),
                FundResearch.AmountUnit.CNY_100_MILLION, REPORT_DATE));
    }

    private static Holdings directHoldings(String stockCode) {
        return new Holdings(List.of(stockHolding(stockCode, "0.80")), null, null, null,
                new BigDecimal("0.80"), false, null, LookThroughQuality.DIRECT);
    }

    private static Holdings linkHoldings(String targetCode) {
        return new Holdings(List.of(targetHolding(targetCode, "0.90")), null, null, null,
                new BigDecimal("0.90"), false, null, LookThroughQuality.DIRECT);
    }

    private static Industry industry(String name) {
        return new Industry(List.of(new WeightedName(name, BigDecimal.ONE)));
    }

    private static Holding targetHolding(String code, String weight) {
        return new Holding(HoldingKind.TARGET_ETF, code, "目标ETF", new BigDecimal(weight));
    }

    private static Holding stockHolding(String code, String weight) {
        return new Holding(HoldingKind.STOCK, code, "股票", new BigDecimal(weight));
    }

    private static <T> SourceSnapshot<T> snapshot(T data, Instant reportDate) {
        return new SourceSnapshot<>(data, SOURCE, reportDate);
    }
}
