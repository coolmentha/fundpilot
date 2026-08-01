package com.fundpilot.backend.discipline.adapter.web.adviceresponse;

import com.fundpilot.backend.discipline.application.query.advicequery.AdviceQueryHandler;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Discipline 建议查询入口。日期参数按 UTC 日期标签解释，与 V41 回填口径一致。 */
@RestController
@RequestMapping("/api/discipline/advice")
@RequiredArgsConstructor
public class AdviceQueryController {
    private final AdviceQueryHandler queries;

    @GetMapping("/pending")
    public DisciplineApiResponse<List<View>> pending(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return DisciplineApiResponse.ok(queries.pending(ownerId).stream().map(View::from).toList());
    }

    @GetMapping("/funds/{legacyFundId}/latest")
    public DisciplineApiResponse<View> latest(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long legacyFundId) {
        var result = queries.latest(ownerId, legacyFundId);
        return DisciplineApiResponse.ok(result == null ? null : View.from(result));
    }

    @GetMapping("/portfolio-funds/{portfolioFundId}/latest")
    public DisciplineApiResponse<View> latestPortfolioFund(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long portfolioFundId) {
        var result = queries.latestByPortfolioFund(ownerId, portfolioFundId);
        return DisciplineApiResponse.ok(result == null ? null : View.from(result));
    }

    @GetMapping("/funds/{legacyFundId}")
    public DisciplineApiResponse<List<View>> range(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long legacyFundId,
            @RequestParam String from, @RequestParam String to) {
        Instant start = LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = LocalDate.parse(to).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return DisciplineApiResponse.ok(queries.range(ownerId, legacyFundId, start, end).stream().map(View::from).toList());
    }

    @GetMapping("/portfolio-funds/{portfolioFundId}")
    public DisciplineApiResponse<List<View>> portfolioFundRange(
            @RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId, @PathVariable long portfolioFundId,
            @RequestParam String from, @RequestParam String to) {
        Instant start = LocalDate.parse(from).atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant end = LocalDate.parse(to).plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        return DisciplineApiResponse.ok(queries.rangeByPortfolioFund(ownerId, portfolioFundId, start, end).stream().map(View::from).toList());
    }

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
    public record Measure(BigDecimal value, String measureUnit) {}
}
