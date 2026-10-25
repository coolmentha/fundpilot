package com.fundpilot.backend.identityaccess.adapter.web.openapi;

import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActorQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 受 Bearer 保护的身份入口；仅返回当前 Token 的非敏感归属信息。 */
@Tag(name = "Open API 身份接口", description = "查看当前 Open API Token 归属")
@RestController
@RequestMapping("/api/open/v1")
@RequiredArgsConstructor
public class OpenApiIdentityController {

    private final CurrentActorQueryHandler actors;

    @GetMapping("/me")
    @Operation(summary = "查询当前 Open API Token 归属")
    public ApiResponse<MeView> me() {
        return ApiResponse.ok(new MeView(actors.current().userId()));
    }

    public record MeView(long userId) {
    }
}
