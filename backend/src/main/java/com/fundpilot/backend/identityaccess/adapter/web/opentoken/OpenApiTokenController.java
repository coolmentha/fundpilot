package com.fundpilot.backend.identityaccess.adapter.web.opentoken;

import com.fundpilot.backend.identityaccess.application.command.opentoken.OpenApiTokenCommandHandler;
import com.fundpilot.backend.identityaccess.application.query.opentoken.OpenApiTokenQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 当前登录用户管理自己的 Open API Token；不接受 ownerId 参数。 */
@Tag(name = "Open API Token 管理接口", description = "创建、刷新、撤销当前用户的只读 API Token")
@RestController
@RequestMapping("/api/open-api/token")
@RequiredArgsConstructor
public class OpenApiTokenController {

    private final OpenApiTokenCommandHandler commands;
    private final OpenApiTokenQueryHandler queries;

    @GetMapping
    @Operation(summary = "查询当前 Token 状态")
    public ApiResponse<TokenView> get(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return ApiResponse.ok(queries.findStatus(ownerId)
                .map(token -> new TokenView(true, token.rotatedAt(), token.lastUsedAt(), null))
                .orElseGet(() -> new TokenView(false, null, null, null)));
    }

    @PostMapping
    @Operation(summary = "创建或刷新 Token", description = "已有 Token 时执行轮换，旧 Token 立即失效；明文只在此响应返回一次")
    public ApiResponse<TokenView> issueOrRotate(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        var issued = commands.issueOrRotate(ownerId);
        return ApiResponse.ok(new TokenView(true, issued.rotatedAt(), null, issued.token()));
    }

    @DeleteMapping
    @Operation(summary = "撤销当前 Token")
    public ApiResponse<Boolean> revoke(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        commands.revoke(ownerId);
        return ApiResponse.ok(true);
    }

    @Schema(description = "Open API Token 状态；明文仅创建/刷新响应中存在")
    public record TokenView(
            @Schema(description = "是否存在有效 Token", example = "true") boolean active,
            @Schema(description = "最近创建或刷新时间") Instant rotatedAt,
            @Schema(description = "最近一次用于 Open API 调用的时间") Instant lastUsedAt,
            @Schema(description = "新 Token 明文，仅创建/刷新时返回") String token) {
    }
}
