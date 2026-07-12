package com.fundpilot.backend.admin.controller;

import com.fundpilot.backend.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class SiteAuthController {

    @GetMapping("/verify")
    public ApiResponse<Boolean> verify() {
        return ApiResponse.ok(true);
    }
}
