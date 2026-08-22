package com.fundpilot.backend.discipline.adapter.web.advicegeneration;

import com.fundpilot.backend.discipline.application.command.advicegeneration.AdviceGenerationCommandHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理 - 纪律建议生成接口", description = "纪律建议生成相关操作")
@RestController
@RequestMapping("/api/admin/signals")
@RequiredArgsConstructor
public class AdviceGenerationAdminController {
    private final AdviceGenerationCommandHandler commands;
    private final Clock clock;

    @PostMapping("/generate")
    @Operation(summary = "生成每日纪律建议")
    public ApiResponse<Map<String, String>> generate() {
        commands.generateDaily(clock.instant());
        return ApiResponse.ok(Map.of("status", "generated"));
    }
}
