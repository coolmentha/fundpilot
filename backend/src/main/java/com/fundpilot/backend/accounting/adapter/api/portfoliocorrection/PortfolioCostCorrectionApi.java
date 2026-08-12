package com.fundpilot.backend.accounting.adapter.api.portfoliocorrection;

import com.fundpilot.backend.accounting.application.command.portfoliocorrection.PortfolioCorrectionCommandHandler;
import com.fundpilot.backend.accounting.application.command.portfoliocorrection.PortfolioCorrectionFailure;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/** Accounting 对当前持仓成本修正暴露的公开入站契约。 */
@Component
@RequiredArgsConstructor
public class PortfolioCostCorrectionApi {
    private final PortfolioCorrectionCommandHandler commands;

    public CostCorrectionResult correct(CorrectCostPerShare request) {
        try {
            var result = commands.correctCostPerShare(
                    request.ownerId(), request.portfolioFundId(), request.costPerShare());
            return new CostCorrectionResult(result.portfolioFundId(), result.costPerShare());
        } catch (PortfolioCorrectionFailure failure) {
            throw new Failure(Code.valueOf(failure.code().name()), failure.getMessage());
        }
    }

    public record CorrectCostPerShare(long ownerId, long portfolioFundId, BigDecimal costPerShare) {
    }

    public record CostCorrectionResult(long portfolioFundId, BigDecimal costPerShare) {
    }

    public static final class Failure extends RuntimeException {
        private final Code code;

        private Failure(Code code, String message) {
            super(message);
            this.code = code;
        }

        public Code code() {
            return code;
        }
    }

    public enum Code {
        COST_PER_SHARE_INVALID,
        PORTFOLIO_FUND_NOT_FOUND,
        PORTFOLIO_FUND_NOT_OPEN
    }
}
