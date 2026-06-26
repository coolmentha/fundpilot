package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundCreateRequest;
import com.fundpilot.backend.fund.controller.FundView;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 基金服务(issue #16):基金 CRUD 业务逻辑,Controller 只做 HTTP 路由,逻辑下沉到本层。
 * <p>新建后自动调 {@link FundDictBackfillService} 回填 fundSubType/benchmarkIndexCode(issue #8 字典识别)。
 * 返回 {@link FundView} DTO,不直接暴露 {@link FundEntity}。
 */
@Service
@RequiredArgsConstructor
public class FundService {

    private final FundRepository fundRepository;
    private final FundDictBackfillService fundDictBackfillService;
    private final FundArchiveService fundArchiveService;

    /** 查全部基金。 */
    public List<FundView> list() {
        return fundRepository.findAll().stream().map(FundView::from).toList();
    }

    /** 新建基金;新建后自动调字典回填(识别 fundSubType/benchmarkIndexCode,#8);幂等,已识别的会跳过。 */
    @Transactional
    public FundView create(FundCreateRequest request) {
        FundEntity fund = new FundEntity();
        fund.setFundCode(request.fundCode());
        fund.setFundName(request.fundName());
        fund.setFundCategory(request.fundCategory());
        fund.setPlannedTotalAmount(request.plannedTotalAmount());
        Long fundId = fundRepository.save(fund).getId();
        fundDictBackfillService.backfillAll();
        return fundRepository.findById(fundId)
                .map(FundView::from)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "新建基金后回查失败 #" + fundId));
    }

    /** 查单个基金;不存在抛 400(业务问题,非路由不存在)。 */
    public FundView get(Long id) {
        return FundView.from(requireFund(id));
    }

    /** 更新基金;仅合并请求中非 null 的字段。 */
    @Transactional
    public FundView update(Long id, FundCreateRequest request) {
        FundEntity fund = requireFund(id);
        if (request.fundName() != null) {
            fund.setFundName(request.fundName());
        }
        if (request.fundCategory() != null) {
            fund.setFundCategory(request.fundCategory());
        }
        if (request.plannedTotalAmount() != null) {
            fund.setPlannedTotalAmount(request.plannedTotalAmount());
        }
        return FundView.from(fundRepository.save(fund));
    }

    /** 归档基金(级联软删),委托 {@link FundArchiveService}。 */
    @Transactional
    public void archive(Long id) {
        fundArchiveService.archive(id);
    }

    private FundEntity requireFund(Long id) {
        return fundRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + id + " 不存在"));
    }
}
