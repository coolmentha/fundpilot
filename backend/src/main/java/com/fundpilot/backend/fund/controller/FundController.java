package com.fundpilot.backend.fund.controller;

import com.fundpilot.backend.common.ApiResponse;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.FundDictBackfillService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 基金 Controller(issue #16):基金 CRUD。FundEntity 无独立 Service 层,Controller 直连 FundRepository。
 * 新建基金时调 {@link FundDictBackfillService} 回填 fundSubType/benchmarkIndexCode(issue #8 字典识别)。
 */
@RestController
@RequestMapping("/api/funds")
public class FundController {

    private final FundRepository fundRepository;
    private final FundDictBackfillService fundDictBackfillService;

    public FundController(FundRepository fundRepository, FundDictBackfillService fundDictBackfillService) {
        this.fundRepository = fundRepository;
        this.fundDictBackfillService = fundDictBackfillService;
    }

    @GetMapping
    public ApiResponse<List<FundEntity>> list() {
        return ApiResponse.ok(fundRepository.findAll());
    }

    @PostMapping
    public ApiResponse<FundEntity> create(@RequestBody FundCreateRequest request) {
        FundEntity fund = new FundEntity();
        fund.setFundCode(request.fundCode());
        fund.setFundName(request.fundName());
        fund.setFundCategory(request.fundCategory());
        fund.setPlannedTotalAmount(request.plannedTotalAmount());
        fund = fundRepository.save(fund);
        // 新建后自动调字典回填(识别 fundSubType/benchmarkIndexCode,#8);幂等,已识别的会跳过
        fundDictBackfillService.backfillAll();
        return ApiResponse.ok(fundRepository.findById(fund.getId()).orElse(fund));
    }

    @GetMapping("/{id}")
    public ApiResponse<FundEntity> get(@PathVariable Long id) {
        return ApiResponse.ok(fundRepository.findById(id).orElse(null));
    }

    @PutMapping("/{id}")
    public ApiResponse<FundEntity> update(@PathVariable Long id, @RequestBody FundCreateRequest request) {
        FundEntity fund = fundRepository.findById(id).orElse(null);
        if (fund == null) {
            return ApiResponse.ok(null);
        }
        if (request.fundName() != null) {
            fund.setFundName(request.fundName());
        }
        if (request.fundCategory() != null) {
            fund.setFundCategory(request.fundCategory());
        }
        if (request.plannedTotalAmount() != null) {
            fund.setPlannedTotalAmount(request.plannedTotalAmount());
        }
        return ApiResponse.ok(fundRepository.save(fund));
    }
}
