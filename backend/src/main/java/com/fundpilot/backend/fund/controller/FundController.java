package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.fund.service.FundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

/**
 * 基金 Controller(issue #16):基金 CRUD,只做 HTTP 路由,业务逻辑下沉 {@link FundService}。
 * 返回 {@link FundView} DTO,不直接暴露 Entity。
 */
@Tag(name = "基金接口", description = "基金相关操作")
@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
public class FundController {

    private final FundService fundService;

    @Operation(summary = "查询基金列表")
    @GetMapping
    public ApiResponse<List<FundView>> list() {
        return ApiResponse.ok(fundService.list());
    }

    @Operation(summary = "创建基金")
    @PostMapping
    public ApiResponse<FundView> create(@RequestBody FundCreateRequest request) {
        return ApiResponse.ok(fundService.create(request));
    }

    @Operation(summary = "查询单只基金")
    @GetMapping("/{id}")
    public ApiResponse<FundView> get(@PathVariable Long id) {
        return ApiResponse.ok(fundService.get(id));
    }

    @Operation(summary = "更新基金")
    @PutMapping("/{id}")
    public ApiResponse<FundView> update(@PathVariable Long id, @RequestBody FundCreateRequest request) {
        return ApiResponse.ok(fundService.update(id, request));
    }

}
