package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.fund.controller.FundTransactionView;
import com.fundpilot.backend.fund.service.FundDictSearchService;
import com.fundpilot.backend.fund.service.FundService;
import com.fundpilot.backend.fund.service.FundTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 基金 Controller(issue #16):基金 CRUD + 字典搜索,只做 HTTP 路由,业务逻辑下沉 {@link FundService}。
 * 返回 {@link FundView} DTO,不直接暴露 Entity。
 */
@RestController
@RequestMapping("/api/funds")
@RequiredArgsConstructor
public class FundController {

    private final FundService fundService;
    private final FundDictSearchService fundDictSearchService;
    private final FundTransactionService fundTransactionService;

    @GetMapping
    public ApiResponse<List<FundView>> list() {
        return ApiResponse.ok(fundService.list());
    }

    /**
     * 基金字典搜索(ADR-0005):新建基金搜索框自动补全用。
     * 按 code 前缀或 name 包含匹配,返回候选列表(最多 20 条),携带选中后一次性回填的全部字段。
     */
    @GetMapping("/search")
    public ApiResponse<List<FundDictSearchView>> search(@RequestParam("q") String q) {
        return ApiResponse.ok(fundDictSearchService.search(q));
    }

    @PostMapping
    public ApiResponse<FundView> create(@RequestBody FundCreateRequest request) {
        return ApiResponse.ok(fundService.create(request));
    }

    @GetMapping("/{id}")
    public ApiResponse<FundView> get(@PathVariable Long id) {
        return ApiResponse.ok(fundService.get(id));
    }

    /** 查某基金交易流水,按创建时间倒序(issue #18 交易流水 Tab)。 */
    @GetMapping("/{id}/transactions")
    public ApiResponse<List<FundTransactionView>> transactions(@PathVariable Long id) {
        return ApiResponse.ok(fundTransactionService.listByFund(id));
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
