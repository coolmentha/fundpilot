package com.fundpilot.backend.portfolio.adapter.web.fundgrouping;

import com.fundpilot.backend.platform.web.RequestActorAttributes;
import com.fundpilot.backend.portfolio.application.command.fundgrouping.FundGroupingCommandHandler;
import com.fundpilot.backend.portfolio.application.query.fundgrouping.FundGroupingQueryHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fund-groups")
@RequiredArgsConstructor
public class FundGroupingController {
    private final FundGroupingCommandHandler commands;
    private final FundGroupingQueryHandler queries;

    @GetMapping
    public Response<List<GroupView>> list(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return Response.ok(queries.list(ownerId).stream().map(GroupView::from).toList());
    }

    @PutMapping
    public Response<List<GroupView>> replace(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @RequestBody(required = false) ReplaceGroupsRequest request) {
        List<FundGroupingCommandHandler.GroupInput> inputs = request == null || request.groups() == null
                ? null
                : request.groups().stream()
                        .map(item -> new FundGroupingCommandHandler.GroupInput(item.id(), item.name()))
                        .toList();
        commands.replace(ownerId, inputs);
        return Response.ok(queries.list(ownerId).stream().map(GroupView::from).toList());
    }

    public record ReplaceGroupsRequest(List<GroupInput> groups) {
    }

    public record GroupInput(Long id, String name) {
    }

    public record GroupView(long id, String name, int sortOrder, long fundCount) {
        static GroupView from(FundGroupingQueryHandler.GroupResult result) {
            return new GroupView(result.id(), result.name(), result.sortOrder(),
                    result.portfolioFundCount());
        }
    }

    record Response<T>(boolean success, T data, String code, String message) {
        static <T> Response<T> ok(T data) {
            return new Response<>(true, data, null, null);
        }
    }
}
