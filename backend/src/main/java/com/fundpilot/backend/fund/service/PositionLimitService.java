package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.fund.entity.FundEntity;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundTransactionRepository;
import com.fundpilot.backend.user.service.UserConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PositionLimitService {

    public static final BigDecimal HARD_MAX_POSITION_RATIO = new BigDecimal("0.30");
    private static final MathContext MATH = MathContext.DECIMAL64;

    private final FundRepository fundRepository;
    private final FundTransactionRepository fundTransactionRepository;
    private final UserConfigService userConfigService;

    /** 在基金行锁内校验买入后的事实市值，所有买入确认路径必须调用。 */
    public void validatePurchase(Long fundId, BigDecimal amount, BigDecimal unitNav) {
        FundEntity fund = fundRepository.findByIdForUpdate(fundId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FUND_NOT_FOUND,
                        "Fund #" + fundId + " 不存在"));
        BigDecimal ratio = normalizeRatio(fund.getMaxPositionRatio());
        BigDecimal totalCapital = userConfigService.requireTotalCapital();
        BigDecimal holdingShares = fundTransactionRepository.aggregateConfirmedShares(List.of(fundId)).stream()
                .findFirst().map(FundTransactionRepository.HoldingSharesProjection::getHoldingShares)
                .orElse(BigDecimal.ZERO);
        BigDecimal currentValue = BigDecimal.ZERO;
        if (holdingShares.signum() > 0) {
            if (unitNav == null || unitNav.signum() <= 0) {
                throw new BusinessException(ErrorCode.NAV_HISTORY_EMPTY, "缺少有效单位净值，无法校验仓位上限");
            }
            currentValue = holdingShares.multiply(unitNav, MATH);
        }
        BigDecimal projectedValue = currentValue.add(amount);
        BigDecimal limit = totalCapital.multiply(ratio, MATH);
        if (projectedValue.compareTo(limit) > 0) {
            throw new BusinessException(ErrorCode.POSITION_LIMIT_EXCEEDED,
                    "买入后仓位 " + projectedValue + " 超过单基金上限 " + limit);
        }
    }

    public static BigDecimal normalizeRatio(BigDecimal ratio) {
        BigDecimal value = ratio != null ? ratio : FundEntity.DEFAULT_MAX_POSITION_RATIO;
        if (value.signum() <= 0 || value.compareTo(HARD_MAX_POSITION_RATIO) > 0) {
            throw new BusinessException(ErrorCode.POSITION_LIMIT_INVALID,
                    "单基金仓位上限必须大于 0 且不超过 30%");
        }
        return value;
    }
}
