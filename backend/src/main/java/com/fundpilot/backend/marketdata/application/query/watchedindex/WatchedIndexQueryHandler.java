package com.fundpilot.backend.marketdata.application.query.watchedindex;

import com.fundpilot.backend.marketdata.domain.watchedindex.WatchedIndexRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WatchedIndexQueryHandler {
    public static final List<String> DEFAULT_INDICES = List.of("1.000001", "1.000300", "0.399006");

    private final WatchedIndexRepository watchlists;

    @Transactional(readOnly = true)
    public List<String> findByOwner(long ownerId) {
        List<String> codes = watchlists.findByOwnerId(ownerId).indexCodes();
        return codes.isEmpty() ? DEFAULT_INDICES : codes;
    }

    @Transactional(readOnly = true)
    public List<String> findAllDistinctCodes() {
        List<String> codes = watchlists.findAllDistinctCodes();
        return codes.isEmpty() ? DEFAULT_INDICES : codes;
    }
}
