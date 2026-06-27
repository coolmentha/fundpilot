package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundTransactionView;
import com.fundpilot.backend.fund.controller.ManualTransactionRequest;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 交易流水服务(issue #18 交易合并到基金详情):查某基金交易流水列表 + 手动录入交易。
 * <p>查询按 createdDate 倒序转 {@link FundTransactionView};手动录入绕过信号(signalLog=null),
 * 买入类(INCREASE/TRANSFER_IN/INVEST)写 amount、卖出类(DECREASE/TRANSFER_OUT)写 shares,
 * 另一侧由 NavConfirmJob 当晚净值确认后回填。
 */
@Service
@RequiredArgsConstructor
public class FundTransactionService {

    private final FundTransactionRepository fundTransactionRepository;
    private final FundRepository fundRepository;

    /** 查某基金全部交易流水,按创建时间倒序(最新在前)。 */
    public List<FundTransactionView> listByFund(Long fundId) {
        return fundTransactionRepository.findByFundEntity_IdOrderByCreatedDateDesc(fundId).stream()
                .map(FundTransactionView::from)
                .toList();
    }

    /**
     * 手动录入一笔交易(issue #18 手动交易)。绕过信号(signalLog=null),status=PENDING,
     * 由 NavConfirmJob 当晚净值确认后回填另一侧并转 CONFIRMED。手动卖出不卡 7 天硬约束。
     */
    @Transactional
    public FundTransactionView createManual(Long fundId, ManualTransactionRequest request) {
        FundEntity fund = fundRepository.findById(fundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + fundId + " 不存在"));
        BigDecimal amount;
        BigDecimal shares;
        switch (request.source()) {
            case INCREASE, TRANSFER_IN, INVEST -> {
                if (request.amount() == null) {
                    throw new BusinessException(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED,
                            request.source() + " 需填金额(amount)");
                }
                amount = request.amount();
                shares = null;
            }
            case DECREASE, TRANSFER_OUT -> {
                if (request.shares() == null) {
                    throw new BusinessException(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED,
                            request.source() + " 需填份额(shares)");
                }
                amount = null;
                shares = request.shares();
            }
            default -> throw new BusinessException(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED,
                    "不支持的手动交易来源: " + request.source());
        }
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(request.source());
        tx.setAmount(amount);
        tx.setShares(shares);
        tx.setNav(null);
        tx.setStatus(FundTransactionStatus.PENDING);
        tx.setSignalLogEntity(null);
        return FundTransactionView.from(fundTransactionRepository.save(tx));
    }
}
