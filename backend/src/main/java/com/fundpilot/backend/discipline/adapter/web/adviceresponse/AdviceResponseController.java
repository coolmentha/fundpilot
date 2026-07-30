package com.fundpilot.backend.discipline.adapter.web.adviceresponse;

import com.fundpilot.backend.discipline.application.command.adviceresponse.AdviceResponseCommandHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户建议回应入口；接受建议只记录待确认账目请求。 */
@RestController
@RequestMapping("/api/discipline/advice")
@RequiredArgsConstructor
public class AdviceResponseController {
    private final AdviceResponseCommandHandler commands;

    @PostMapping("/{adviceId}/accept")
    public DisciplineApiResponse<AcceptedAdviceView> accept(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long adviceId,
            @RequestBody AcceptAdviceRequest request) {
        var result = commands.accept(ownerId, adviceId, request.amount(), request.shares(), request.tradeDate());
        return DisciplineApiResponse.ok(new AcceptedAdviceView(result.adviceId(), result.transactionId()));
    }

    @PostMapping("/{adviceId}/ignore")
    public DisciplineApiResponse<Void> ignore(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long adviceId) {
        commands.ignore(ownerId, adviceId);
        return DisciplineApiResponse.ok(null);
    }

    public record AcceptAdviceRequest(BigDecimal amount, BigDecimal shares, Instant tradeDate) {
    }

    public record AcceptedAdviceView(long adviceId, long transactionId) {
    }
}
