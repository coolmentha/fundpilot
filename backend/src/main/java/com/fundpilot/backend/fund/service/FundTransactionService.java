package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.controller.FundTransactionView;
import com.fundpilot.backend.fund.controller.ManualTransactionRequest;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 交易流水服务(issue #18 交易合并到基金详情):查某基金交易流水列表 + 手动录入交易。
 * <p>查询按业务交易时间倒序转 {@link FundTransactionView};手动录入绕过信号(signalLog=null),
 * 买入类(INCREASE/TRANSFER_IN/INVEST)写 amount、卖出类(DECREASE/TRANSFER_OUT)写 shares,
 * 另一侧在交易日净值落库后自动回填。
 */
@Service
@RequiredArgsConstructor
public class FundTransactionService {

    private final FundTransactionRepository fundTransactionRepository;
    private final FundRepository fundRepository;
    private final TransactionConfirmSupport transactionConfirmSupport;
    private final FundPositionService fundPositionService;

    /** 查某基金全部交易流水,按交易发生时间倒序(最新在前)。 */
    public List<FundTransactionView> listByFund(Long fundId) {
        return fundTransactionRepository.findByFundIdOrderByTradeDateDesc(fundId).stream()
                .map(FundTransactionView::from)
                .toList();
    }

    /** 查全部待处理交易，供跨基金操作确认工作台使用。 */
    public List<FundTransactionView> listPending() {
        return fundTransactionRepository.findByStatusOrderByTradeDateDesc(FundTransactionStatus.PENDING).stream()
                .map(FundTransactionView::from)
                .toList();
    }

    /**
     * 手动录入一笔交易(issue #18 手动交易)。绕过信号(signalLog=null),status=PENDING,
     * 交易日净值落库后回填另一侧并转 CONFIRMED。手动卖出不卡 7 天硬约束。
     *
     * <p>基金转换(task 07-08):{@code source=TRANSFER_OUT} 且 {@code targetFundId} 非空时,
     * 创建转出(A, TRANSFER_OUT, shares)+ 转入(B, TRANSFER_IN, amount/shares 均空,待确认时回填)两条交易,
     * 双向 set relatedFundTransactionEntity 互指。返回转出腿(触发腿)。
     * {@code targetFundId} 为空走原纯转出逻辑(单条记录)。
     */
    @Transactional
    public FundTransactionView createManual(Long fundId, ManualTransactionRequest request) {
        if (request.source() == null) {
            throw new BusinessException(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED, "交易来源(source)必填");
        }
        boolean isAdjust = request.source() == FundTransactionSource.ADJUST_IN
                || request.source() == FundTransactionSource.ADJUST_OUT;
        FundEntity fund = (isAdjust ? fundRepository.findByIdForUpdate(fundId) : fundRepository.findById(fundId))
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND, "Fund #" + fundId + " 不存在"));
        Instant now = Instant.now();
        Instant tradeDate = request.tradeDate() != null ? request.tradeDate() : now;
        if (tradeDate.isAfter(now)) {
            throw new BusinessException(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED, "交易发生时间不能晚于当前时间");
        }

        // 调整交易(task 07-09):录入即 CONFIRMED,不算净值/手续费,只改持仓份额。
        // amount/fee/feeRate/nav 均空,金额实时算(份额×当前净值)。
        if (isAdjust) {
            if (request.shares() == null || request.shares().signum() <= 0) {
                throw new BusinessException(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED,
                        request.source() + " 需填正数份额(shares)");
            }
            if (request.source() == FundTransactionSource.ADJUST_OUT) {
                BigDecimal holdingShares = fundPositionService.getHoldingShares(fundId);
                if (holdingShares == null || request.shares().compareTo(holdingShares) > 0) {
                    throw new BusinessException(ErrorCode.INSUFFICIENT_HOLDING_SHARES,
                            "调减份额 " + request.shares() + " 超过当前持仓 "
                                    + (holdingShares == null ? BigDecimal.ZERO : holdingShares));
                }
            }
            FundTransactionEntity tx = new FundTransactionEntity();
            tx.setFundEntity(fund);
            tx.setSource(request.source());
            tx.setShares(request.shares());
            tx.setAmount(null);
            tx.setNav(null);
            tx.setFee(null);
            tx.setFeeRate(null);
            tx.setStatus(FundTransactionStatus.CONFIRMED);
            tx.setTradeDate(tradeDate);
            tx.setConfirmTime(now);
            tx.setSignalLogEntity(null);
            FundTransactionEntity saved = fundTransactionRepository.save(tx);
            transactionConfirmSupport.onAdjustConfirmed(saved);
            fundPositionService.reconcileStatus(fundId);
            return FundTransactionView.from(saved);
        }

        BigDecimal amount;
        BigDecimal shares;
        switch (request.source()) {
            case INCREASE, TRANSFER_IN, INVEST -> {
                if (request.amount() == null || request.amount().signum() <= 0) {
                    throw new BusinessException(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED,
                            request.source() + " 需填正数金额(amount)");
                }
                amount = request.amount();
                shares = null;
            }
            case DECREASE, TRANSFER_OUT -> {
                if (request.shares() == null || request.shares().signum() <= 0) {
                    throw new BusinessException(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED,
                            request.source() + " 需填正数份额(shares)");
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
        tx.setTradeDate(tradeDate);
        tx.setSignalLogEntity(null);

        // 基金转换:TRANSFER_OUT + targetFundId -> 建转入腿并双向互指。返回转出腿。
        if (request.source() == FundTransactionSource.TRANSFER_OUT && request.targetFundId() != null) {
            if (request.targetFundId().equals(fundId)) {
                throw new BusinessException(ErrorCode.MANUAL_TRANSACTION_FIELD_REQUIRED,
                        "转入基金不能与转出基金相同");
            }
            FundEntity targetFund = fundRepository.findById(request.targetFundId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND,
                            "转入基金 #" + request.targetFundId() + " 不存在"));
            FundTransactionEntity txOut = fundTransactionRepository.save(tx);

            FundTransactionEntity txIn = new FundTransactionEntity();
            txIn.setFundEntity(targetFund);
            txIn.setSource(FundTransactionSource.TRANSFER_IN);
            txIn.setAmount(null);   // 待确认时由转出净金额回填
            txIn.setShares(null);   // 待确认时算
            txIn.setNav(null);
            txIn.setStatus(FundTransactionStatus.PENDING);
            txIn.setTradeDate(tradeDate);
            txIn.setSignalLogEntity(null);
            txIn.setRelatedFundTransactionEntity(txOut);
            txOut.setRelatedFundTransactionEntity(txIn);
            fundTransactionRepository.save(txIn);
            // txOut 已在上方 save,互指关系更新后需再 save 一次
            fundTransactionRepository.save(txOut);
            return FundTransactionView.from(txOut);
        }
        return FundTransactionView.from(fundTransactionRepository.save(tx));
    }
}
