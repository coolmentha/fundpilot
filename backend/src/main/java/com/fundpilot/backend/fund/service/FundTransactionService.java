package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.controller.FundTransactionView;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 交易流水查询服务(issue #18 交易合并到基金详情):查某基金交易流水列表,
 * 按 createdDate 倒序,转 {@link FundTransactionView}。不新建表,复用 FundTransactionEntity。
 * <p>后续手动录入(issue B2)也由本服务承接。
 */
@Service
@RequiredArgsConstructor
public class FundTransactionService {

    private final FundTransactionRepository fundTransactionRepository;

    /** 查某基金全部交易流水,按创建时间倒序(最新在前)。 */
    public List<FundTransactionView> listByFund(Long fundId) {
        return fundTransactionRepository.findByFundEntity_IdOrderByCreatedDateDesc(fundId).stream()
                .map(FundTransactionView::from)
                .toList();
    }
}
