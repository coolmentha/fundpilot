package com.fundpilot.backend.discipline.application.command.adviceresponse;

import com.fundpilot.backend.discipline.application.gateway.adviceresponse.AdviceTransactionGateway;
import com.fundpilot.backend.discipline.domain.advice.Advice;
import com.fundpilot.backend.discipline.domain.advice.AdviceAction;
import com.fundpilot.backend.discipline.domain.advice.AdviceRepository;
import com.fundpilot.backend.discipline.domain.advice.AdviceResponseStatus;
import com.fundpilot.backend.discipline.domain.strategy.DisciplineStrategyRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户接受建议时仅请求创建 Accounting PENDING 流水，不代表代替用户执行外部交易。 */
@Service
@RequiredArgsConstructor
public class AdviceResponseCommandHandler {
    private final AdviceRepository adviceRepository;
    private final DisciplineStrategyRepository strategies;
    private final AdviceTransactionGateway transactions;
    private final Clock clock;

    @Transactional
    public ResponseResult accept(long ownerId, long adviceId, BigDecimal amount, BigDecimal shares,
                                 Instant tradeDate) {
        Advice advice = owned(ownerId, adviceId);
        if (advice.ignoredAt() != null) {
            throw failure(AdviceResponseFailure.Code.ADVICE_IGNORED, "建议已忽略: " + adviceId);
        }
        if (advice.responseStatus() == AdviceResponseStatus.RESPONDED) {
            throw failure(AdviceResponseFailure.Code.ALREADY_RESPONDED, "建议已完成回应: " + adviceId);
        }
        AdviceTransactionGateway.Source source = sourceOf(advice.action());
        if (source == AdviceTransactionGateway.Source.INCREASE && invalid(amount)) {
            throw failure(AdviceResponseFailure.Code.VALUE_REQUIRED, "建仓或加仓建议需填正数金额");
        }
        if (source == AdviceTransactionGateway.Source.DECREASE && invalid(shares)) {
            throw failure(AdviceResponseFailure.Code.VALUE_REQUIRED, "卖出建议需填正数份额");
        }
        if (source == AdviceTransactionGateway.Source.DECREASE && "LOGIC_BROKEN".equals(advice.reason())) {
            BigDecimal confirmedShares = transactions.confirmedHoldingShares(ownerId, advice.portfolioFundId());
            if (shares.compareTo(confirmedShares) != 0) {
                throw failure(AdviceResponseFailure.Code.VALUE_NOT_ALLOWED, "逻辑止损必须全仓卖出");
            }
        }
        try {
            var transaction = transactions.createPending(new AdviceTransactionGateway.CreatePending(ownerId,
                    advice.portfolioFundId(), source, amount, shares,
                    tradeDate != null ? tradeDate : clock.instant(), advice.id(), advice.reason()));
            return new ResponseResult(advice.id(), transaction.transactionId());
        } catch (AdviceTransactionGateway.Rejected exception) {
            throw failure(exception.alreadyResponded() ? AdviceResponseFailure.Code.ALREADY_RESPONDED
                    : AdviceResponseFailure.Code.ADVICE_NOT_ACTIONABLE, exception.getMessage());
        }
    }

    @Transactional
    public void ignore(long ownerId, long adviceId) {
        Advice advice = owned(ownerId, adviceId);
        try {
            advice.ignore(clock.instant());
        } catch (IllegalStateException exception) {
            throw failure(AdviceResponseFailure.Code.ADVICE_NOT_ACTIONABLE, exception.getMessage());
        }
        adviceRepository.save(advice);
        resetTriggeredStrategy(adviceId);
    }

    /** 忽略的是当前 TRIGGERED 止盈建议时,把策略复位回 ARMED,否则后续不再生成卖出建议。 */
    private void resetTriggeredStrategy(long adviceId) {
        strategies.findByTriggeredAdviceId(adviceId)
                .filter(strategy -> "TRIGGERED".equals(strategy.takeProfitPhase()))
                .ifPresent(strategy -> {
                    strategy.supersedeTriggered();
                    strategies.save(strategy);
                });
    }

    private Advice owned(long ownerId, long adviceId) {
        return adviceRepository.findByIdForUpdate(adviceId)
                .filter(advice -> advice.ownerId() == ownerId)
                .orElseThrow(() -> failure(AdviceResponseFailure.Code.ADVICE_NOT_FOUND,
                        "建议不存在: " + adviceId));
    }

    private static AdviceTransactionGateway.Source sourceOf(AdviceAction action) {
        return switch (action) {
            case BUILD, ADD -> AdviceTransactionGateway.Source.INCREASE;
            case SELL -> AdviceTransactionGateway.Source.DECREASE;
            case NONE -> throw failure(AdviceResponseFailure.Code.ADVICE_NOT_ACTIONABLE, "无建议不可回应");
        };
    }

    private static boolean invalid(BigDecimal value) {
        return value == null || value.signum() <= 0;
    }

    private static AdviceResponseFailure failure(AdviceResponseFailure.Code code, String message) {
        return new AdviceResponseFailure(code, message);
    }

    public record ResponseResult(long adviceId, long transactionId) {
    }
}
