package com.fundpilot.backend.productcatalog.adapter.event.researchrefresh;

import com.fundpilot.backend.productcatalog.application.command.feerefresh.FundFeeCommandHandler;
import com.fundpilot.backend.productcatalog.application.command.researchrefresh.FundResearchCommandHandler;
import com.fundpilot.backend.productcatalog.application.event.researchrefresh.FundPublicDataRefreshRequestedEvent;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FundPublicDataRefreshListenerTest {

    @Test
    void refreshesFeesAndResearchForRequestedFund() {
        FundFeeCommandHandler feeCommands = mock(FundFeeCommandHandler.class);
        FundResearchCommandHandler researchCommands = mock(FundResearchCommandHandler.class);
        FundPublicDataRefreshListener listener = new FundPublicDataRefreshListener(feeCommands, researchCommands);

        listener.onRefreshRequested(new FundPublicDataRefreshRequestedEvent("110022"));

        verify(feeCommands).refresh("110022");
        verify(researchCommands).refresh("110022");
    }

    @Test
    void feeFailureDoesNotBlockResearchRefresh() {
        FundFeeCommandHandler feeCommands = mock(FundFeeCommandHandler.class);
        FundResearchCommandHandler researchCommands = mock(FundResearchCommandHandler.class);
        doThrow(new IllegalStateException("upstream unavailable")).when(feeCommands).refresh("110022");
        FundPublicDataRefreshListener listener = new FundPublicDataRefreshListener(feeCommands, researchCommands);

        listener.onRefreshRequested(new FundPublicDataRefreshRequestedEvent("110022"));

        verify(researchCommands).refresh("110022");
    }
}
