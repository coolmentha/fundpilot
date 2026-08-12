package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import com.fundpilot.backend.marketdata.application.gateway.indicatorrefresh.PublishedIndexValuationSourceGateway;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class PublishedIndexValuationSourceGatewayAdapter implements PublishedIndexValuationSourceGateway {
    private static final String SOURCE = "CSINDEX_INDEX_CSI_DS_PE_PEG";
    private final CsindexValuationClient client;

    @Override
    public List<Valuation> fetch(String indexCode, String startDate, String endDate) {
        String code = indexCode;
        int dot = code.indexOf('.');
        if (dot >= 0) code = code.substring(0, dot);
        return CsindexJsParser.parseIndexValuation(client.fetchIndexCsiDsPe(code, startDate, endDate), indexCode,
                        SOURCE).stream()
                .map(value -> new Valuation(value.tradeDate(), value.peRatio())).toList();
    }
}
