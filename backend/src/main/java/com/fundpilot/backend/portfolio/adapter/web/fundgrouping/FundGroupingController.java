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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "基金分组接口", description = "基金分组相关操作")
@RestController
@RequestMapping("/api/fund-groups")
@RequiredArgsConstructor
public class FundGroupingController {
    private final FundGroupingCommandHandler commands;
    private final FundGroupingQueryHandler queries;

    @Operation(summary = "查询基金分组列表")
    @GetMapping
    public Response<List<GroupView>> list(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return Response.ok(queries.list(ownerId).stream().map(GroupView::from).toList());
    }

    @Operation(summary = "替换基金分组")
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

    @Schema(description = "基金分组替换请求")
    public record ReplaceGroupsRequest(
            @Schema(description = "替换后的分组列表", example = "[{\"id\":1,\"name\":\"核心池\"}]") List<GroupInput> groups) {
    }

    @Schema(description = "基金分组输入")
    public record GroupInput(
            @Schema(description = "分组编号,为空表示新建分组", example = "1") Long id,
            @Schema(description = "分组名称", example = "核心池") String name) {
    }

    @Schema(description = "基金分组视图")
    public record GroupView(
            @Schema(description = "分组编号", example = "1") long id,
            @Schema(description = "分组名称", example = "核心池") String name,
            @Schema(description = "分组排序序号,数值越小越靠前", example = "0") int sortOrder,
            @Schema(description = "分组内基金数量", example = "5") long fundCount) {
        static GroupView from(FundGroupingQueryHandler.GroupResult result) {
            return new GroupView(result.id(), result.name(), result.sortOrder(),
                    result.portfolioFundCount());
        }
    }

    @Schema(description = "通用响应视图")
    record Response<T>(
            @Schema(description = "请求是否成功,true 表示成功,false 表示失败", example = "true") boolean success,
            @Schema(description = "业务数据") T data,
            @Schema(description = "业务错误码,成功时为空", example = "OK") String code,
            @Schema(description = "提示信息,成功时为空", example = "操作成功") String message) {
        static <T> Response<T> ok(T data) {
            return new Response<>(true, data, null, null);
        }
    }
}
