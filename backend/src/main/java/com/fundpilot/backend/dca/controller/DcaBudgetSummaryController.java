package com.fundpilot.backend.dca.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.dca.service.DcaBudgetSummaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 提供全局本月定投预算总览，预算只影响展示。 */
@RestController
@RequiredArgsConstructor
public class DcaBudgetSummaryController {

    private final DcaBudgetSummaryService dcaBudgetSummaryService;

    @GetMapping("/api/dca/budget-summary")
    public ApiResponse<DcaBudgetSummaryView> currentMonth() {
        return ApiResponse.ok(dcaBudgetSummaryService.currentMonth());
    }
}
