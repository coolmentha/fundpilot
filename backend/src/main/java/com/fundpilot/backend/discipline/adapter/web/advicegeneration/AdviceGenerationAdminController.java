package com.fundpilot.backend.discipline.adapter.web.advicegeneration;

import com.fundpilot.backend.discipline.application.command.advicegeneration.AdviceGenerationCommandHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import java.time.Clock;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/signals")
@RequiredArgsConstructor
public class AdviceGenerationAdminController {
    private final AdviceGenerationCommandHandler commands;
    private final Clock clock;

    @PostMapping("/generate")
    public ApiResponse<Map<String, String>> generate() {
        commands.generateDaily(clock.instant());
        return ApiResponse.ok(Map.of("status", "generated"));
    }
}
