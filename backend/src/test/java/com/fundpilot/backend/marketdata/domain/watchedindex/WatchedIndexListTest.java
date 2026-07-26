package com.fundpilot.backend.marketdata.domain.watchedindex;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WatchedIndexListTest {

    @Test
    void normalizesWhitespaceAndPreservesFirstSelectionOrder() {
        WatchedIndexList list = new WatchedIndexList(7, List.of(" 1.000300 ", "", "1.000001", "1.000300"));

        assertThat(list.indexCodes()).containsExactly("1.000300", "1.000001");
    }

    @Test
    void rejectsInvalidOwnerAndExcessiveSelections() {
        assertThatThrownBy(() -> new WatchedIndexList(0, List.of("1.000001")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WatchedIndexList(7, java.util.stream.IntStream.range(0, 21)
                .mapToObj(index -> "1." + index).toList()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
