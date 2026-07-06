package com.fundpilot.backend.dca.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.dca.service.DcaPlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 定投计划 Controller:CRUD + 状态机。
 * <p>6 个端点:list/create/update/activate/retire/active。逻辑下沉 Service,返回 View DTO。
 */
@RestController
@RequiredArgsConstructor
public class DcaPlanController {

    private final DcaPlanService dcaPlanService;

    @GetMapping("/api/funds/{fundId}/dca-plans")
    public ApiResponse<List<FundDcaPlanView>> listByFund(@PathVariable Long fundId) {
        return ApiResponse.ok(dcaPlanService.listByFundView(fundId));
    }

    @PostMapping("/api/funds/{fundId}/dca-plans")
    public ApiResponse<Map<String, Long>> create(@PathVariable Long fundId,
                                                 @RequestBody DcaPlanRequest request) {
        Long id = dcaPlanService.create(fundId, request);
        return ApiResponse.ok(Map.of("id", id));
    }

    @PutMapping("/api/dca-plans/{id}")
    public ApiResponse<Void> update(@PathVariable Long id, @RequestBody DcaPlanRequest request) {
        dcaPlanService.updateDraft(id, request);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/dca-plans/{id}/activate")
    public ApiResponse<Void> activate(@PathVariable Long id) {
        dcaPlanService.activate(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/dca-plans/{id}/retire")
    public ApiResponse<Void> retire(@PathVariable Long id) {
        dcaPlanService.retire(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/funds/{fundId}/dca-plans/active")
    public ApiResponse<FundDcaPlanView> active(@PathVariable Long fundId) {
        return ApiResponse.ok(dcaPlanService.findActiveView(fundId).orElse(null));
    }
}
