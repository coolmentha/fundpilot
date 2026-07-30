package com.fundpilot.backend.accounting.adapter.web.portfoliocorrection;

import com.fundpilot.backend.accounting.application.command.portfoliocorrection.PortfolioCorrectionCommandHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/portfolio-funds")
@RequiredArgsConstructor
public class PortfolioCorrectionController {
    private final PortfolioCorrectionCommandHandler commands;

    @PostMapping("/{portfolioFundId}/void")
    public AccountingApiResponse<VoidPortfolioFundView> voidPortfolioFund(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long portfolioFundId,
            @RequestBody(required = false) VoidPortfolioFundRequest request) {
        var result = commands.voidPortfolioFund(ownerId, portfolioFundId,
                request == null ? null : request.reason(),
                request != null && request.confirmed());
        return AccountingApiResponse.ok(new VoidPortfolioFundView(
                result.portfolioFundId(), result.changed(), result.voidedAt(),
                result.voidedBy(), result.voidReason()));
    }

    public record VoidPortfolioFundRequest(String reason, boolean confirmed) {
    }

    public record VoidPortfolioFundView(long portfolioFundId, boolean changed,
                                        Instant voidedAt, Long voidedBy, String voidReason) {
    }
}
