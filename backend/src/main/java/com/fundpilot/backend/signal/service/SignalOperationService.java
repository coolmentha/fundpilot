package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.enums.FundTransactionStatus;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.FundPositionService;
import com.fundpilot.backend.market.service.RedemptionFeeService;
import com.fundpilot.backend.signal.controller.ConfirmOperationRequest;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalType;
import com.fundpilot.backend.signal.repository.SignalLogRepository;
import com.fundpilot.backend.strategy.repository.FundStrategyRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * 信号操作确认服务(ADR-0015 重写):定投移动止盈只有 NONE/SELL,只处理 SELL 的减仓确认。
 * <p>SELL → 写 DECREASE 交易(shares=SignalLog.suggestedMeasure 的份额或用户实际份额),
 * nav 等 NavConfirmJob 回填。NONE 无需确认。定投买入不经本服务(定时任务自动产 INVEST)。
 */
@Service
@RequiredArgsConstructor
public class SignalOperationService {

    private static final Logger log = LoggerFactory.getLogger(SignalOperationService.class);

    private final SignalLogRepository signalLogRepository;
    private final FundRepository fundRepository;
    private final FundStrategyRepository fundStrategyRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final FundPositionService fundPositionService;
    private final RedemptionFeeService redemptionFeeService;

    /**
     * 确认信号操作:SELL 写 DECREASE 交易;NONE 抛异常(无需确认)。
     *
     * @param signalLogId SignalLog 主键
     * @param request     用户实际下单值(actualShares)
     * @return 写入的 FundTransactionEntity
     */
    @Transactional
    public FundTransactionEntity confirmOperation(Long signalLogId, ConfirmOperationRequest request) {
        SignalLogEntity signalLog = signalLogRepository.findById(signalLogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNAL_LOG_NOT_FOUND, "SignalLog #" + signalLogId + " 不存在"));
        FundEntity fund = signalLog.getFundEntity();
        SignalType type = signalLog.getSignalType();
        Instant now = Instant.now();

        if (type != SignalType.SELL) {
            throw new BusinessException(ErrorCode.INVALID_SIGNAL_TYPE, "NONE 信号无需确认操作");
        }
        BigDecimal shares = requireShares(request);
        // #60 赎回费软提示:查适用赎回费率,写入 SignalLog.warnings 供前端展示(不阻止赎回;数据缺失静默)
        String feeWarning = buildRedemptionFeeWarning(fund, shares);
        if (feeWarning != null) {
            signalLog.setWarnings(mergeWarning(signalLog.getWarnings(), feeWarning));
            signalLogRepository.save(signalLog);
        }
        FundTransactionEntity tx = newTransaction(fund, signalLog, FundTransactionSource.DECREASE, null, shares, now);
        log.info("SELL 减仓确认 fund_id={} shares={} redemptionFeeWarning={}", fund.getId(), shares, feeWarning);
        return fundTransactionRepository.save(tx);
    }

    /** 赎回费软提示文案:按 openedAt 算持有天数查赎回费率,有费率返提示串;无/缺失返 null。 */
    private String buildRedemptionFeeWarning(FundEntity fund, BigDecimal shares) {
        if (fund.getOpenedAt() == null || fund.getFundCode() == null) {
            return null;
        }
        long holdingDays = java.time.temporal.ChronoUnit.DAYS.between(fund.getOpenedAt(), java.time.Instant.now());
        try {
            java.math.BigDecimal feeRate = redemptionFeeService.feeRate(fund.getFundCode(), (int) Math.max(0, holdingDays));
            if (feeRate != null && feeRate.signum() > 0) {
                return "赎回费提示:持有" + holdingDays + "日,适用费率"
                        + feeRate.multiply(java.math.BigDecimal.valueOf(100)).toPlainString()
                        + "%(仅供参考,不阻止赎回)";
            }
        } catch (RuntimeException ex) {
            log.debug("赎回费软提示查询失败 fund={} 降级不提示: {}", fund.getId(), ex.getMessage());
        }
        return null;
    }

    /** 合并已有 warnings 与新提示(去重逗号)。 */
    private static String mergeWarning(String existing, String add) {
        return existing == null || existing.isBlank() ? add : existing + "," + add;
    }

    private static BigDecimal requireShares(ConfirmOperationRequest request) {
        if (request.actualShares() == null) {
            throw new BusinessException(ErrorCode.MISSING_ACTUAL_SHARES, "SELL 需提供 actualShares");
        }
        return request.actualShares();
    }

    private static FundTransactionEntity newTransaction(FundEntity fund, SignalLogEntity signalLog,
                                                        FundTransactionSource source,
                                                        BigDecimal amount, BigDecimal shares, Instant now) {
        FundTransactionEntity tx = new FundTransactionEntity();
        tx.setFundEntity(fund);
        tx.setSource(source);
        tx.setAmount(amount);
        tx.setShares(shares);
        tx.setNav(null); // nav 等 NavConfirmJob 回填
        tx.setStatus(FundTransactionStatus.PENDING);
        tx.setSignalLogEntity(signalLog);
        return tx;
    }
}