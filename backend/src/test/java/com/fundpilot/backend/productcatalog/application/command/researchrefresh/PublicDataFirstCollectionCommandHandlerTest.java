package com.fundpilot.backend.productcatalog.application.command.researchrefresh;

import com.fundpilot.backend.productcatalog.application.gateway.researchrefresh.PublicDataFirstCollectionEventGateway;
import com.fundpilot.backend.productcatalog.domain.product.FundProduct;
import com.fundpilot.backend.productcatalog.domain.product.FundProductRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicDataFirstCollectionCommandHandlerTest {
    private final FundProductRepository products = mock(FundProductRepository.class);
    private final List<String> published = new ArrayList<>();
    private final PublicDataFirstCollectionCommandHandler handler =
            new PublicDataFirstCollectionCommandHandler(products, published::add);

    @Test
    void publishesRefreshRequestWithResolvedFundCode() {
        when(products.findById(5L)).thenReturn(Optional.of(FundProduct.create(
                "110022", "易方达消费行业股票", null, null, null, null, null)));

        handler.requestFirstCollection(5L);

        assertThat(published).containsExactly("110022");
    }

    @Test
    void missingProductIsIgnoredSilently() {
        when(products.findById(5L)).thenReturn(Optional.empty());

        assertThatCode(() -> handler.requestFirstCollection(5L)).doesNotThrowAnyException();
        assertThat(published).isEmpty();
    }
}
