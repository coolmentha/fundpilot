package com.fundpilot.backend.signal.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.entity.FundTransactionEntity;
import com.fundpilot.backend.fund.enums.FundStatus;
import com.fundpilot.backend.fund.enums.FundTransactionSource;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.fund.service.FundPositionService;
import com.fundpilot.backend.signal.controller.ConfirmOperationRequest;
import com.fundpilot.backend.signal.entity.SignalLogEntity;
import com.fundpilot.backend.signal.enums.SignalReason;
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
 * 信号操作确认服务(issue #14):用户回应 SignalLog 的统一入口。
 * <p>读 SignalLog 的 {@code signalType + reason} 分派到不同推进动作,
 * 写 {@link FundTransactionEntity}(nav=null、status=PENDING)+ 推进 {@link FundStatus}。
 *
 * <p>金字塔加仓退场后,只处理 SELL 信号确认(BUILD/ADD 信号不再产生,但存量 SignalLog 的 BUILD/ADD
 * 确认仍兼容——走简化路径:只写交易,不再推进 tierNAddedAt)。
 *
 * <h3>分派表</h3>
 * <table>
 *   <tr><th>SignalLog</th><th>推进动作</th></tr>
 *   <tr><td>BUILD</td><td>写 INCREASE 交易(amount=actualAmount);FundStatus→HOLDING;openedAt=now</td></tr>
 *   <tr><td>ADD tierN</td><td>写 INCREASE 交易(存量兼容,不再推进 tierNAddedAt)</td></tr>
 *   <tr><td>SELL TRAILING_STOP</td><td>写 DECREASE 交易(shares=actualShares);持仓归零→CLEARED</td></tr>
 *   <tr><td>SELL LOGIC_BROKEN</td><td>写 DECREASE 交易;FundStatus→CLEARED(一次清空)</td></tr>
 * </table>
 *
 * <h3>偏离说明</h3>
 * issue 标题写 {@code DisciplineStrategyService.confirmOperation},但 #12 已把 DisciplineStrategyService
 * 定位为纯函数引擎(无 DB 依赖、所有值由参数注入)。confirmOperation 有 DB 写,放进纯函数引擎会破坏其可测性。
 * 故单独抽 {@code SignalOperationService},与 {@link SignalGenerationService} 同层(编排层)。
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

    /**
     * 确认信号操作:根据 SignalLog 分派推进动作并写 FundTransaction。
     *
     * @param signalLogId SignalLog 主键
     * @param request     用户实际下单值(actualAmount/actualShares)
     * @return 写入的 FundTransactionEntity
     * @throws BusinessException signalLogId 找不到
     * @throws BusinessException       actualAmount/actualShares 缺失
     */
    @Transactional
    public FundTransactionEntity confirmOperation(Long signalLogId, ConfirmOperationRequest request) {
        SignalLogEntity signalLog = signalLogRepository.findById(signalLogId)
                .orElseThrow(() -> new BusinessException(ErrorCode.SIGNAL_LOG_NOT_FOUND, "SignalLog #" + signalLogId + " 不存在"));
        FundEntity fund = signalLog.getFundEntity();
        SignalType type = signalLog.getSignalType();
        SignalReason reason = signalLog.getReason();
        Instant now = Instant.now();

        FundTransactionEntity tx = switch (type) {
            case BUILD -> handleBuild(signalLog, fund, request, now);
            case ADD -> handleAdd(signalLog, fund, request, now);
            case SELL -> handleSell(fund, reason, request, now);
            case NONE -> throw new BusinessException(ErrorCode.INVALID_SIGNAL_TYPE,
                    "NONE 信号无需确认操作");
        };
        return fundTransactionRepository.save(tx);
    }

    /** BUILD:写 INCREASE(amount);FundStatus→HOLDING;openedAt=now(存量兼容,新策略不再产 BUILD)。 */
    private FundTransactionEntity handleBuild(SignalLogEntity signalLog, FundEntity fund,
                                              ConfirmOperationRequest request, Instant now) {
        BigDecimal amount = requireAmount(request);
        fund.setStatus(FundStatus.HOLDING);
        fund.setOpenedAt(now);
        fundRepository.save(fund);
        return newTransaction(fund, signalLog, FundTransactionSource.INCREASE, amount, null, now);
    }

    /** ADD:写 INCREASE(amount)(存量兼容,新策略不再产 ADD,不再推进 tierNAddedAt)。 */
    private FundTransactionEntity handleAdd(SignalLogEntity signalLog, FundEntity fund,
                                           ConfirmOperationRequest request, Instant now) {
        BigDecimal amount = requireAmount(request);
        return newTransaction(fund, signalLog, FundTransactionSource.INCREASE, amount, null, now);
    }

    /**
     * SELL:按 reason 分派。
     * <ul>
     *   <li>TRAILING_STOP:写 DECREASE 交易;持仓归零→CLEARED</li>
     *   <li>LOGIC_BROKEN:写 DECREASE 交易;CLEARED(一次清空)</li>
     * </ul>
     */
    private FundTransactionEntity handleSell(FundEntity fund, SignalReason reason,
                                             ConfirmOperationRequest request, Instant now) {
        BigDecimal shares = requireShares(request);
        FundTransactionEntity tx = newTransaction(fund, null, FundTransactionSource.DECREASE, null, shares, now);

        if (reason == SignalReason.TRAILING_STOP) {
            clearIfHoldingExhausted(fund, now);
        } else if (reason == SignalReason.LOGIC_BROKEN) {
            fund.setStatus(FundStatus.CLEARED);
            fundRepository.save(fund);
        } else {
            throw new BusinessException(ErrorCode.UNSUPPORTED_SELL_REASON, "不支持的 SELL reason: " + reason);
        }
        return tx;
    }

    /** 移动止盈后聚合判断:若持仓份额归零 → CLEARED。 */
    private void clearIfHoldingExhausted(FundEntity fund, Instant now) {
        BigDecimal holdingShares = fundPositionService.getHoldingShares(fund.getId());
        if (holdingShares.signum() <= 0) {
            fund.setStatus(FundStatus.CLEARED);
            fundRepository.save(fund);
            log.info("TRAILING_STOP 持仓归零 fund_id={} → CLEARED", fund.getId());
        }
    }

    private static BigDecimal requireAmount(ConfirmOperationRequest request) {
        if (request.actualAmount() == null) {
            throw new BusinessException(ErrorCode.MISSING_ACTUAL_AMOUNT, "BUILD/ADD 需提供 actualAmount");
        }
        return request.actualAmount();
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
        tx.setNav(null); // nav 等 NavConfirmJob 回填(#15)
        tx.setStatus(com.fundpilot.backend.fund.enums.FundTransactionStatus.PENDING);
        tx.setSignalLogEntity(signalLog);
        return tx;
    }
}