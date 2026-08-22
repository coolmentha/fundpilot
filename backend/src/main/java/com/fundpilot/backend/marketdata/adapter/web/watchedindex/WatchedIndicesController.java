package com.fundpilot.backend.marketdata.adapter.web.watchedindex;

import com.fundpilot.backend.marketdata.application.command.watchedindex.WatchedIndexCommandHandler;
import com.fundpilot.backend.marketdata.application.query.watchedindex.WatchedIndexQueryHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "关注指数接口", description = "关注指数相关操作")
@RestController
@RequestMapping("/api/market-data/watched-indices")
@RequiredArgsConstructor
public class WatchedIndicesController {
    private final WatchedIndexCommandHandler commands;
    private final WatchedIndexQueryHandler queries;

    @GetMapping
    @Operation(summary = "查询关注指数列表")
    public MarketDataApiResponse<View> get(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return MarketDataApiResponse.ok(new View(queries.findByOwner(ownerId)));
    }

    @PutMapping
    @Operation(summary = "全量替换关注指数")
    public MarketDataApiResponse<View> replace(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                                @RequestBody ReplaceRequest request) {
        return MarketDataApiResponse.ok(new View(commands.replace(ownerId, request.indexCodes()).indexCodes()));
    }

    @Schema(description = "关注指数替换请求")
    public record ReplaceRequest(
            @Schema(description = "指数代码列表，将全量覆盖原有关注列表", example = "[\"1.000300\", \"0.399006\"]") List<String> indexCodes) {}
    @Schema(description = "关注指数列表结果")
    public record View(
            @Schema(description = "指数代码列表", example = "[\"1.000300\", \"0.399006\"]") List<String> indexCodes) {}
}
