package com.fundpilot.backend.platform.adapter.web.openapi;

import com.fundpilot.backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 无状态发现入口：调用方可先确认版本与资源范围，再携带 Bearer Token 访问。 */
@Tag(name = "Open API 发现接口", description = "Open API 公开元数据")
@RestController
@RequestMapping("/api/open")
@RequiredArgsConstructor
public class OpenApiDiscoveryController {

    @GetMapping
    @Operation(summary = "查询 Open API 版本与鉴权要求（无需 Token）")
    public ApiResponse<DiscoveryView> get() {
        return ApiResponse.ok(new DiscoveryView("v1", "Bearer", "/api/open/v1",
                List.of("market", "portfolio", "transactions", "plans", "alerts")));
    }

    public record DiscoveryView(String version, String authentication, String basePath,
                                List<String> resources) {
    }
}
