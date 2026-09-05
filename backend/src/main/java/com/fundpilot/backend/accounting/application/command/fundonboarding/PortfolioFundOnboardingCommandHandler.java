package com.fundpilot.backend.accounting.application.command.fundonboarding;

import com.fundpilot.backend.accounting.application.command.positiontracking.PositionCommandHandler;
import com.fundpilot.backend.accounting.application.event.transaction.TransactionConfirmed;
import com.fundpilot.backend.accounting.application.gateway.fundonboarding.FundGroupingGateway;
import com.fundpilot.backend.accounting.application.gateway.fundonboarding.InitialPositionNavGateway;
import com.fundpilot.backend.accounting.application.gateway.fundonboarding.OnboardedPortfolioFundGateway;
import com.fundpilot.backend.accounting.application.gateway.transactionledger.LedgerEventGateway;
import com.fundpilot.backend.accounting.domain.lot.Lot;
import com.fundpilot.backend.accounting.domain.lot.LotRepository;
import com.fundpilot.backend.accounting.domain.transaction.LedgerTransaction;
import com.fundpilot.backend.accounting.domain.transaction.ShareScale;
import com.fundpilot.backend.accounting.domain.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在一个本地事务内创建 PortfolioFund，并按需建立已确认的期初账目与持仓。 */
@Service
@RequiredArgsConstructor
public class PortfolioFundOnboardingCommandHandler {
    private static final int INITIAL_HOLDING_SHARES_PRECISION = 19;
    private static final int INITIAL_HOLDING_SHARES_NUMERIC_SCALE = 8;
    private static final int INITIAL_TRANSACTION_AMOUNT_SCALE = 8;
    private static final int INITIAL_TRANSACTION_AMOUNT_PRECISION = 19;
    private static final int COST_PER_SHARE_SCALE = 8;
    private static final int COST_PER_SHARE_PRECISION = 19;

    private final OnboardedPortfolioFundGateway portfolioFunds;
    private final FundGroupingGateway groups;
    private final InitialPositionNavGateway navs;
    private final TransactionRepository transactions;
    private final LotRepository lots;
    private final PositionCommandHandler positions;
    private final LedgerEventGateway events;
    private final Clock clock;

    @Transactional
    public OnboardingResult onboard(Long legacyFundId, long ownerId, long fundProductId,
                                    boolean positionWarningEnabled, BigDecimal positionWarningRatio,
                                    BigDecimal initialHoldingShares, BigDecimal costPerShare,
                                    Instant openedAt) {
        return onboard(legacyFundId, ownerId, fundProductId, positionWarningEnabled, positionWarningRatio,
                initialHoldingShares, costPerShare, openedAt, List.of());
    }

    @Transactional
    public OnboardingResult onboard(Long legacyFundId, long ownerId, long fundProductId,
                                    boolean positionWarningEnabled, BigDecimal positionWarningRatio,
                                    BigDecimal initialHoldingShares, BigDecimal costPerShare,
                                    Instant openedAt, List<String> groupNames) {
        Instant now = clock.instant();
        if (initialHoldingShares == null) {
            if (costPerShare != null || openedAt != null) {
                throw failure(PortfolioFundOnboardingFailure.Code.INITIAL_HOLDING_SHARES_INVALID,
                        "未提供初始持仓份额时不能提供成本单价或建仓时间");
            }
            OnboardedPortfolioFundGateway.OnboardedPortfolioFund portfolioFund = track(
                    legacyFundId, ownerId, fundProductId, positionWarningEnabled, positionWarningRatio);
            assignGroups(ownerId, portfolioFund.portfolioFundId(), groupNames);
            return new OnboardingResult(portfolioFund.portfolioFundId(), null);
        }

        BigDecimal shares = normalizeInitialHoldingShares(initialHoldingShares);
        if (shares == null || shares.signum() <= 0) {
            throw failure(PortfolioFundOnboardingFailure.Code.INITIAL_HOLDING_SHARES_INVALID,
                    "初始持仓份额必须大于 0 且不超过数据库可表示范围");
        }
        BigDecimal normalizedCost = normalizeCostPerShare(costPerShare);
        if (costPerShare != null && normalizedCost == null) {
            throw failure(PortfolioFundOnboardingFailure.Code.COST_PER_SHARE_INVALID,
                    "成本单价必须大于 0 且不超过数据库可表示范围");
        }
        if (openedAt != null && openedAt.isAfter(now)) {
            throw failure(PortfolioFundOnboardingFailure.Code.OPENED_AT_IN_FUTURE,
                    "建仓时间不能晚于当前时间");
        }
        OnboardedPortfolioFundGateway.OnboardedPortfolioFund portfolioFund = track(
                legacyFundId, ownerId, fundProductId, positionWarningEnabled, positionWarningRatio);
        InitialPositionNavGateway.PublishedNav nav = navs.latest(fundProductId)
                .filter(value -> value.unitNav() != null && value.unitNav().signum() > 0)
                .orElseThrow(() -> failure(PortfolioFundOnboardingFailure.Code.NAV_UNAVAILABLE,
                        "基金产品 " + fundProductId + " 无已公布净值，无法确认初始持仓"));
        BigDecimal effectiveCost = costPerShare != null
                ? normalizedCost : normalizeCostPerShare(nav.unitNav());
        if (effectiveCost == null) {
            throw failure(PortfolioFundOnboardingFailure.Code.COST_PER_SHARE_INVALID,
                    "成本单价必须大于 0 且不超过数据库可表示范围");
        }
        if (normalizeInitialTransactionAmount(shares.multiply(nav.unitNav())) == null) {
            throw failure(PortfolioFundOnboardingFailure.Code.INITIAL_HOLDING_SHARES_INVALID,
                    "初始持仓对应的核算金额不在数据库可表示范围");
        }
        Instant effectiveOpenedAt = openedAt != null ? openedAt : now;
        LedgerTransaction transaction = transactions.save(LedgerTransaction.recordExistingPosition(
                portfolioFund.portfolioFundId(), ownerId, shares, nav.unitNav(), effectiveOpenedAt,
                effectiveOpenedAt));
        lots.save(Lot.open(portfolioFund.portfolioFundId(), transaction.id(), effectiveOpenedAt,
                shares, effectiveCost));
        positions.applyExistingPosition(portfolioFund.portfolioFundId(), ownerId, effectiveCost,
                effectiveOpenedAt);
        assignGroups(ownerId, portfolioFund.portfolioFundId(), groupNames);
        events.publishConfirmed(new TransactionConfirmed(transaction.id(), transaction.portfolioFundId(),
                transaction.ownerId(), transaction.source().name(), transaction.amount(), transaction.shares(),
                transaction.nav(), transaction.fee(), transaction.tradeDate(), transaction.confirmTime(),
                transaction.signalLogId(), transaction.dcaPlanId(), transaction.disciplineAdviceId(),
                transaction.investmentPlanId(), transaction.id(), now));
        return new OnboardingResult(portfolioFund.portfolioFundId(), transaction.id());
    }

    private void assignGroups(long ownerId, long portfolioFundId, List<String> groupNames) {
        try {
            groups.assignByNames(ownerId, portfolioFundId, groupNames == null ? List.of() : groupNames);
        } catch (FundGroupingGateway.Failure failure) {
            throw failure(PortfolioFundOnboardingFailure.Code.valueOf(failure.code().name()),
                    failure.getMessage());
        }
    }

    private OnboardedPortfolioFundGateway.OnboardedPortfolioFund track(Long legacyFundId, long ownerId,
                                                                         long fundProductId,
                                                                         boolean positionWarningEnabled,
                                                                         BigDecimal positionWarningRatio) {
        try {
            return portfolioFunds.track(legacyFundId, ownerId, fundProductId, positionWarningEnabled,
                    positionWarningRatio);
        } catch (OnboardedPortfolioFundGateway.Rejected rejection) {
            throw failure(switch (rejection.reason()) {
                case PRODUCT_NOT_FOUND -> PortfolioFundOnboardingFailure.Code.PRODUCT_NOT_FOUND;
                case ALREADY_TRACKED -> PortfolioFundOnboardingFailure.Code.PORTFOLIO_FUND_ALREADY_TRACKED;
                case INVALID_POSITION_WARNING -> PortfolioFundOnboardingFailure.Code.POSITION_WARNING_INVALID;
            }, rejection.getMessage());
        }
    }

    private static PortfolioFundOnboardingFailure failure(PortfolioFundOnboardingFailure.Code code,
                                                           String message) {
        return new PortfolioFundOnboardingFailure(code, message);
    }

    private static BigDecimal normalizeCostPerShare(BigDecimal value) {
        return normalizePositiveNumeric(value, COST_PER_SHARE_SCALE, COST_PER_SHARE_PRECISION);
    }

    private static BigDecimal normalizeInitialHoldingShares(BigDecimal value) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = ShareScale.normalize(value);
        return normalized.signum() > 0
                && fitsNumeric(normalized, INITIAL_HOLDING_SHARES_NUMERIC_SCALE,
                INITIAL_HOLDING_SHARES_PRECISION)
                ? normalized : null;
    }

    private static BigDecimal normalizeInitialTransactionAmount(BigDecimal value) {
        return normalizePositiveNumeric(value, INITIAL_TRANSACTION_AMOUNT_SCALE,
                INITIAL_TRANSACTION_AMOUNT_PRECISION);
    }

    private static BigDecimal normalizePositiveNumeric(BigDecimal value, int scale, int precision) {
        if (value == null) {
            return null;
        }
        BigDecimal normalized = value.setScale(scale, RoundingMode.HALF_UP);
        return normalized.signum() > 0 && fitsNumeric(normalized, scale, precision)
                ? normalized : null;
    }

    private static boolean fitsNumeric(BigDecimal value, int scale, int precision) {
        return value.setScale(scale, RoundingMode.HALF_UP).precision() <= precision;
    }

    public record OnboardingResult(long portfolioFundId, Long initialTransactionId) {
    }
}
