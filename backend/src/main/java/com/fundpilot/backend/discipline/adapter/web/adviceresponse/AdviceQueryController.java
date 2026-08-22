package com.fundpilot.backend.discipline.adapter.web.adviceresponse;

import com.fundpilot.backend.discipline.application.query.advicequery.AdviceQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.math.BigDecimal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Discipline 建议查询入口。日期参数按 UTC 日期标签解释，与 V41 回填口径一致。 */
@Tag(name = "纪律建议查询接口", description = "纪律建议查询相关操作")
@RestController
@RequestMapping("/api/discipline/advice")
@RequiredArgsConstructor
public class AdviceQueryController {
    private final AdviceQueryHandler queries;

    @GetMapping("/pending")
    @Operation(summary = "查询待回应建议")
    public ApiResponse<List<View>> pending(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return ApiResponse.ok(queries.pending(ownerId).stream().map(View::from).toList());
    }

    @GetMapping("/funds/{legacyFundId}/latest")
    @Operation(summary = "查询基金最新纪律建议")
    public ApiResponse<View> latest(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long legacyFundId) {
        var result = queries.latest(ownerId, legacyFundId);
        return ApiResponse.ok(result == null ? null : View.from(result));
    }

    @GetMapping("/portfolio-funds/{portfolioFundId}/latest")
    @Operation(summary = "查询组合基金最新纪律建议")
    public ApiResponse<View> latestPortfolioFund(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long portfolioFundId) {
        var result = queries.latestByPortfolioFund(ownerId, portfolioFundId);
        return ApiResponse.ok(result == null ? null : View.from(result));
    }

    @GetMapping("/funds/{legacyFundId}")
    @Operation(summary = "按日期区间查询基金纪律建议")
    public ApiResponse<List<View>> range(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long legacyFundId,
            @RequestParam String from, @RequestParam String to) {
        Instant start = LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = LocalDate.parse(to).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return ApiResponse.ok(queries.range(ownerId, legacyFundId, start, end).stream().map(View::from).toList());
    }

    @GetMapping("/portfolio-funds/{portfolioFundId}")
    @Operation(summary = "按日期区间查询组合基金纪律建议")
    public ApiResponse<List<View>> portfolioFundRange(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long portfolioFundId,
            @RequestParam String from, @RequestParam String to) {
        Instant start = LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = LocalDate.parse(to).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return ApiResponse.ok(queries.rangeByPortfolioFund(ownerId, portfolioFundId, start, end).stream().map(View::from).toList());
    }

    @Schema(description = "纪律建议视图")
    public record View(long id, Long fundId, long portfolioFundId, String action, String responseStatus, Instant signalDate,
                       Integer triggerTier, BigDecimal coefficient, Measure suggestedMeasure, String reason,
                       String warnings, Long relatedTransactionId, String relatedTransactionStatus) {
        static View from(AdviceQueryHandler.Result value) {
            Measure measure = value.suggestedValue() == null ? null
                    : new Measure(value.suggestedValue(), value.suggestedMeasureUnit());
            return new View(value.id(), value.legacyFundId(), value.portfolioFundId(), value.action(), value.responseStatus(), value.signalDate(),
                    value.triggerTier(), value.coefficient(), measure, value.reason(), value.warnings(),
                    value.relatedTransactionId(), value.relatedTransactionStatus());
        }
    }
    @Schema(description = "纪律建议度量值视图")
    public record Measure(@Schema(description = "建议数值", example = "0.05") BigDecimal value,
                          @Schema(description = "建议数值单位", example = "PERCENT") String measureUnit) {}
}

