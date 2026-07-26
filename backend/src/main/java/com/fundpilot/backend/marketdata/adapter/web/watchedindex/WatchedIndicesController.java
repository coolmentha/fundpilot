package com.fundpilot.backend.marketdata.adapter.web.watchedindex;

import com.fundpilot.backend.marketdata.application.command.watchedindex.WatchedIndexCommandHandler;
import com.fundpilot.backend.marketdata.application.query.watchedindex.WatchedIndexQueryHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-data/watched-indices")
@RequiredArgsConstructor
public class WatchedIndicesController {
    private final WatchedIndexCommandHandler commands;
    private final WatchedIndexQueryHandler queries;

    @GetMapping
    public MarketDataApiResponse<View> get(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return MarketDataApiResponse.ok(new View(queries.findByOwner(ownerId)));
    }

    @PutMapping
    public MarketDataApiResponse<View> replace(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                                @RequestBody ReplaceRequest request) {
        return MarketDataApiResponse.ok(new View(commands.replace(ownerId, request.indexCodes()).indexCodes()));
    }

    public record ReplaceRequest(List<String> indexCodes) {}
    public record View(List<String> indexCodes) {}
}
