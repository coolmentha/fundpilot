package com.fundpilot.backend.insights.adapter.api.fundreturn;

import com.fundpilot.backend.insights.application.query.portfolioreturn.PortfolioReturnQueryHandler;
import java.math.BigDecimal;
import java.math.MathContext;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FundReturnApi {

    private final PortfolioReturnQueryHandler queries;

    /** 返回该用户当前在持(未清仓)的基金收益快照。 */
    public List<FundReturnSnapshot> currentFunds(long ownerId) {
        return queries.currentFunds(ownerId).stream().map(FundReturnSnapshot::from).toList();
    }

    public record FundReturnSnapshot(long portfolioFundId, String fundCode, String fundName,
                                     String positionStatus, boolean open,
                                     BigDecimal dailyChangePct, BigDecimal holdingAmount,
                                     BigDecimal unrealizedPnl, BigDecimal holdingReturnRate,
                                     BigDecimal valuationNav, Instant valuationDate,
                                     String estimateStatus) {
        static FundReturnSnapshot from(PortfolioReturnQueryHandler.FundReturnResult value) {
            return new FundReturnSnapshot(value.portfolioFundId(), value.fundCode(), value.fundName(),
                    value.positionStatus(), value.open(), value.dailyChangePct(), value.holdingAmount(),
                    value.unrealizedPnl(), holdingReturnRate(value.holdingAmount(), value.unrealizedPnl()),
                    value.valuationNav(), value.valuationDate(), value.estimateStatus());
        }

        /** 持仓收益率口径与前端 querySafety.js#holdingReturnRate 保持一致。 */
        static BigDecimal holdingReturnRate(BigDecimal holdingAmount, BigDecimal totalPnl) {
            if (holdingAmount == null || totalPnl == null) {
                return null;
            }
            BigDecimal cost = holdingAmount.subtract(totalPnl);
            return cost.signum() > 0 ? totalPnl.divide(cost, MathContext.DECIMAL64) : null;
        }
    }
}
