package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.fund.service.FundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 基金 Controller(issue #16):基金 CRUD,只做 HTTP 路由,业务逻辑下沉 {@link FundService}。
 * 返回 {@link FundView} DTO,不直接暴露 Entity。
 */
@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
public class FundController {

    private final FundService fundService;

    @GetMapping
    public ApiResponse<List<FundView>> list() {
        return ApiResponse.ok(fundService.list());
    }

    @PostMapping
    public ApiResponse<FundView> create(@RequestBody FundCreateRequest request) {
        return ApiResponse.ok(fundService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<FundView> get(@PathVariable Long id) {
        return ApiResponse.ok(fundService.get(id));
    }

    @PutMapping("/{id}")
    public ApiResponse<FundView> update(@PathVariable Long id, @RequestBody FundCreateRequest request) {
        return ApiResponse.ok(fundService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> archive(@PathVariable Long id) {
        fundService.archive(id);
        return ApiResponse.ok(null);
    }
}
