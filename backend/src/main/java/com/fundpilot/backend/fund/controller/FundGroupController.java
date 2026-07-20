package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.fund.service.FundGroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/fund-groups")
@RequiredArgsConstructor
public class FundGroupController {
    private final FundGroupService fundGroupService;

    @GetMapping
    public ApiResponse<List<FundGroupView>> list() {
        return ApiResponse.ok(fundGroupService.list());
    }

    @PutMapping
    public ApiResponse<List<FundGroupView>> save(@RequestBody FundGroupSaveRequest request) {
        return ApiResponse.ok(fundGroupService.save(request));
    }
}
