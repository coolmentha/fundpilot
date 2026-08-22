package com.fundpilot.backend.discipline.adapter.web.adviceresponse;

import com.fundpilot.backend.discipline.application.command.adviceresponse.AdviceResponseCommandHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.math.BigDecimal;
import java.time.Instant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 用户建议回应入口；接受建议只记录待确认账目请求。 */
@Tag(name = "纪律建议回应接口", description = "纪律建议回应相关操作")
@RestController
@RequestMapping("/api/discipline/advice")
@RequiredArgsConstructor
public class AdviceResponseController {
    private final AdviceResponseCommandHandler commands;

    @PostMapping("/{adviceId}/accept")
    @Operation(summary = "采纳纪律建议")
    public ApiResponse<AcceptedAdviceView> accept(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long adviceId,
            @RequestBody AcceptAdviceRequest request) {
        var result = commands.accept(ownerId, adviceId, request.amount(), request.shares(), request.tradeDate());
        return ApiResponse.ok(new AcceptedAdviceView(result.adviceId(), result.transactionId()));
    }

    @PostMapping("/{adviceId}/ignore")
    @Operation(summary = "忽略纪律建议")
    public ApiResponse<Void> ignore(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
            @PathVariable long adviceId) {
        commands.ignore(ownerId, adviceId);
        return ApiResponse.ok(null);
    }

    @Schema(description = "采纳纪律建议请求")
    public record AcceptAdviceRequest(@Schema(description = "拟交易金额", example = "2000.00") BigDecimal amount,
                                      @Schema(description = "拟交易份额", example = "1000.00") BigDecimal shares,
                                      @Schema(description = "拟交易日期", example = "2026-08-21T08:00:00Z") Instant tradeDate) {
    }

    @Schema(description = "采纳建议结果视图")
    public record AcceptedAdviceView(@Schema(description = "纪律建议ID", example = "9001") long adviceId,
                                     @Schema(description = "生成的待确认账目交易ID", example = "8001") long transactionId) {
    }
}

