package com.fundpilot.backend.investmentplan.adapter.web.planmanagement;

import com.fundpilot.backend.investmentplan.application.command.planmanagement.InvestmentPlanCommandHandler;
import com.fundpilot.backend.investmentplan.application.query.planexecution.InvestmentPlanQueryHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/investment-plans")
@RequiredArgsConstructor
public class InvestmentPlanController {
    private final InvestmentPlanCommandHandler commands;
    private final InvestmentPlanQueryHandler queries;

    @GetMapping
    public Response<List<PlanView>> list(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return Response.ok(queries.list(ownerId).stream().map(PlanView::from).toList());
    }

    @GetMapping("/funds/{legacyFundId}")
    public Response<List<PlanView>> listByFund(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                               @PathVariable long legacyFundId) {
        return Response.ok(queries.listByLegacyFund(ownerId, legacyFundId).stream().map(PlanView::from).toList());
    }

    @GetMapping("/portfolio-funds/{portfolioFundId}")
    public Response<List<PlanView>> listByPortfolioFund(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                                        @PathVariable long portfolioFundId) {
        return Response.ok(queries.listByPortfolioFund(ownerId, portfolioFundId).stream().map(PlanView::from).toList());
    }

    @GetMapping("/funds/{legacyFundId}/active")
    public Response<PlanView> active(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                     @PathVariable long legacyFundId) {
        var plan = queries.activeByLegacyFund(ownerId, legacyFundId);
        return Response.ok(plan == null ? null : PlanView.from(plan));
    }

    @GetMapping("/portfolio-funds/{portfolioFundId}/active")
    public Response<PlanView> activePortfolioFund(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                                   @PathVariable long portfolioFundId) {
        var plan = queries.activeByPortfolioFund(ownerId, portfolioFundId);
        return Response.ok(plan == null ? null : PlanView.from(plan));
    }

    @PostMapping("/funds/{legacyFundId}")
    public Response<PlanView> create(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                     @PathVariable long legacyFundId, @RequestBody Request request) {
        return Response.ok(PlanView.from(commands.create(ownerId, legacyFundId, request.toInput())));
    }

    @PostMapping("/portfolio-funds/{portfolioFundId}")
    public Response<PlanView> createPortfolioFund(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                                   @PathVariable long portfolioFundId, @RequestBody Request request) {
        return Response.ok(PlanView.from(commands.createForPortfolioFund(ownerId, portfolioFundId, request.toInput())));
    }

    @PutMapping("/{planId}")
    public Response<PlanView> update(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                     @PathVariable long planId, @RequestBody Request request) {
        return Response.ok(PlanView.from(commands.update(ownerId, planId, request.toInput())));
    }

    @PostMapping("/{planId}/{action:activate|retire|pause|resume}")
    public Response<PlanView> action(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                     @PathVariable long planId, @PathVariable String action) {
        var plan = switch (action) {
            case "activate" -> commands.activate(ownerId, planId);
            case "retire" -> commands.retire(ownerId, planId);
            case "pause" -> commands.setEnabled(ownerId, planId, false);
            case "resume" -> commands.setEnabled(ownerId, planId, true);
            default -> throw new IllegalStateException("不支持的计划操作");
        };
        return Response.ok(PlanView.from(plan));
    }

    @DeleteMapping("/{planId}")
    public Response<Void> delete(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                 @PathVariable long planId) {
        commands.delete(ownerId, planId);
        return Response.ok(null);
    }

    public record Request(boolean enabled, BigDecimal amount, String frequency,
                          Integer dayOfWeek, Integer dayOfMonth) {
        InvestmentPlanCommandHandler.PlanInput toInput() {
            return new InvestmentPlanCommandHandler.PlanInput(enabled, amount, frequency, dayOfWeek, dayOfMonth);
        }
    }
    public record PlanView(Long id, long portfolioFundId, boolean enabled, BigDecimal amount,
                           String frequency, Integer dayOfWeek, Integer dayOfMonth,
                           String status, Instant createdDate, int remainingOccurrences,
                           BigDecimal remainingAmount, List<Instant> remainingExecutionDates) {
        static PlanView from(InvestmentPlanCommandHandler.PlanResult result) {
            var dates = result.remainingExecutionDates();
            return new PlanView(result.id(), result.portfolioFundId(), result.enabled(), result.amount(),
                    result.frequency(), result.dayOfWeek(), result.dayOfMonth(), result.status(),
                    result.createdDate(), dates.size(), result.amount().multiply(BigDecimal.valueOf(dates.size())), dates);
        }
    }
    record Response<T>(boolean success, T data, String code, String message) {
        static <T> Response<T> ok(T data) { return new Response<>(true, data, null, null); }
    }
}
