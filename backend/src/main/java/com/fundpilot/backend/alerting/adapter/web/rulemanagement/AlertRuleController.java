package com.fundpilot.backend.alerting.adapter.web.rulemanagement;

import com.fundpilot.backend.alerting.application.command.rulemanagement.AlertRuleCommandHandler;
import com.fundpilot.backend.alerting.application.query.indicatormetadata.IndicatorMetadataQueryHandler;
import com.fundpilot.backend.alerting.application.query.rulemanagement.AlertRuleQueryHandler;
import com.fundpilot.backend.alerting.application.query.ruleevaluation.AlertRuleEvaluationQueryHandler;
import com.fundpilot.backend.platform.web.ApiResponse;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import com.fundpilot.backend.platform.web.error.BusinessException;
import com.fundpilot.backend.platform.web.error.ErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

@Tag(name = "价格提醒规则接口", description = "提醒规则的增删改查与启停")
@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertRuleCommandHandler commands;
    private final AlertRuleQueryHandler queries;
    private final AlertRuleEvaluationQueryHandler evaluation;
    private final IndicatorMetadataQueryHandler indicators;

    @GetMapping
    @Operation(summary = "查询提醒规则列表")
    public ApiResponse<List<AlertRuleView>> list(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId) {
        return ApiResponse.ok(queries.findByOwner(ownerId).stream().map(AlertRuleView::from).toList());
    }

    @GetMapping("/indicators")
    @Operation(summary = "查询提醒指标元数据", description = "条件构建器的枚举来源：指标、可选关系、参数定义与取值范围")
    public ApiResponse<List<IndicatorView>> indicatorMetadata() {
        return ApiResponse.ok(indicators.all().stream().map(IndicatorView::from).toList());
    }

    @PostMapping("/preview")
    @Operation(summary = "保存前试算提醒规则", description = "用当前行情与持仓数据试算草稿规则：是否命中、命中哪条条件、是否缺数据")
    public ApiResponse<PreviewView> preview(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                            @RequestBody Request request) {
        return ApiResponse.ok(PreviewView.from(evaluation.preview(ownerId, request.toInput())));
    }

    @PostMapping
    @Operation(summary = "新建提醒规则")
    public ApiResponse<AlertRuleView> create(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                             @RequestBody Request request) {
        return ApiResponse.ok(AlertRuleView.from(commands.create(ownerId, request.toInput())));
    }

    @PutMapping("/{ruleId}")
    @Operation(summary = "编辑提醒规则")
    public ApiResponse<AlertRuleView> update(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                             @PathVariable long ruleId, @RequestBody Request request) {
        return ApiResponse.ok(AlertRuleView.from(commands.update(ownerId, ruleId, request.toInput())));
    }

    @PostMapping("/{ruleId}/{action:enable|disable}")
    @Operation(summary = "启用或禁用提醒规则")
    public ApiResponse<AlertRuleView> changeEnabled(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                                    @PathVariable long ruleId, @PathVariable String action) {
        boolean enabled = switch (action) {
            case "enable" -> true;
            case "disable" -> false;
            default -> throw new BusinessException(ErrorCode.ALERT_RULE_SCOPE_INVALID, "不支持的规则操作: " + action);
        };
        return ApiResponse.ok(AlertRuleView.from(commands.changeEnabled(ownerId, ruleId, enabled)));
    }

    @DeleteMapping("/{ruleId}")
    @Operation(summary = "删除提醒规则")
    public ApiResponse<Void> delete(@RequestAttribute(RequestActorAttributes.USER_ID) Long ownerId,
                                    @PathVariable long ruleId) {
        commands.delete(ownerId, ruleId);
        return ApiResponse.ok(null);
    }

    @Schema(description = "提醒规则创建/更新请求")
    public record Request(
            @Schema(description = "作用范围，枚举（GLOBAL 全部关注基金 / FUND 指定基金）", example = "GLOBAL") String scope,
            @Schema(description = "指定基金 ID，scope=FUND 时必填、GLOBAL 时须为空", example = "12") Long portfolioFundId,
            @Schema(description = "规则种类，枚举（CONDITION 条件提醒 / LOGIC_BROKEN 逻辑破坏止损 / TRAILING_STOP 回撤止盈），"
                    + "缺省为 CONDITION", example = "CONDITION") String kind,
            @Schema(description = "条件组合方式，当前仅支持 ALL（全部满足）", example = "ALL") String match,
            @Schema(description = "条件数组，至少一条；kind 需要条件时必填") List<ConditionRequest> conditions,
            @Schema(description = "回撤止盈的六个参数；仅 kind=TRAILING_STOP 时必填") TakeProfitRequest takeProfit,
            @Schema(description = "是否启用，缺省为 true", example = "true") Boolean enabled) {

        AlertRuleCommandHandler.RuleInput toInput() {
            return new AlertRuleCommandHandler.RuleInput(scope, portfolioFundId, kind, match,
                    conditions == null ? null : conditions.stream().map(ConditionRequest::toInput).toList(),
                    takeProfit == null ? null : takeProfit.toInput(), enabled);
        }
    }

    @Schema(description = "回撤止盈参数；比例一律用小数表示（0.15 即 15%）")
    public record TakeProfitRequest(
            @Schema(description = "止盈启动收益率，(0, 1)", example = "0.15") BigDecimal activation,
            @Schema(description = "高点回撤比例，(0, 1)", example = "0.06") BigDecimal pullback,
            @Schema(description = "浮盈收割比例，(0, 1]", example = "0.5") BigDecimal harvest,
            @Schema(description = "最低保留仓位，[0, 1)", example = "0.5") BigDecimal minimumHolding,
            @Schema(description = "单次最大卖出比例，(0, 1]", example = "0.2") BigDecimal maxSingleSell,
            @Schema(description = "冷静期交易日，0~250", example = "10") Integer cooldownDays) {

        AlertRuleCommandHandler.RuleInput.TakeProfitInput toInput() {
            return new AlertRuleCommandHandler.RuleInput.TakeProfitInput(activation, pullback, harvest,
                    minimumHolding, maxSingleSell, cooldownDays);
        }
    }

    @Schema(description = "单条提醒条件")
    public record ConditionRequest(
            @Schema(description = "指标码，取值由 /api/alert-rules/indicators 提供", example = "PRICE_VS_MA")
            String indicator,
            @Schema(description = "指标参数，缺省取指标默认值", example = "{\"window\":250}")
            Map<String, Integer> params,
            @Schema(description = "关系，枚举（ABOVE / BELOW / CROSS_ABOVE / CROSS_BELOW / INCREASING / DECREASING）",
                    example = "BELOW") String relation,
            @Schema(description = "阈值，小数表示（0.05 即 5%）；仅与阈值比较的关系需要，缺省取指标默认值",
                    example = "0.05") BigDecimal value) {

        AlertRuleCommandHandler.RuleInput.ConditionInput toInput() {
            return new AlertRuleCommandHandler.RuleInput.ConditionInput(indicator, params, relation, value);
        }
    }

    @Schema(description = "提醒规则视图")
    public record AlertRuleView(
            @Schema(description = "规则 ID", example = "1") long id,
            @Schema(description = "作用范围", example = "FUND") String scope,
            @Schema(description = "指定基金 ID，全局规则为空", example = "12") Long portfolioFundId,
            @Schema(description = "基金代码，全局规则为空", example = "161725") String fundCode,
            @Schema(description = "基金名称，全局规则为空", example = "招商中证白酒指数") String fundName,
            @Schema(description = "规则种类", example = "CONDITION") String kind,
            @Schema(description = "规则种类中文名", example = "条件提醒") String kindLabel,
            @Schema(description = "条件组合方式；回撤止盈为空", example = "ALL") String match,
            @Schema(description = "判定的中文摘要", example = "净值与均线偏离率 下穿均线 且 周线 MACD 柱高 柱较上周缩小")
            String conditionSummary,
            @Schema(description = "条件数组，供编辑回显；回撤止盈为空数组") List<ConditionView> conditions,
            @Schema(description = "回撤止盈参数，供编辑回显；其它种类为空") TakeProfitView takeProfit,
            @Schema(description = "是否启用", example = "true") boolean enabled,
            @Schema(description = "今日是否已提醒", example = "false") boolean todaySent,
            @Schema(description = "上次触发时间") Instant lastTriggeredAt) {

        static AlertRuleView from(AlertRuleQueryHandler.RuleViewResult result) {
            return new AlertRuleView(result.id(), result.scope(), result.portfolioFundId(), result.fundCode(),
                    result.fundName(), result.kind(), result.kindLabel(), result.match(), result.conditionSummary(),
                    result.conditions().stream().map(ConditionView::from).toList(),
                    TakeProfitView.from(result.takeProfit()), result.enabled(),
                    result.todaySent(), result.lastTriggeredAt());
        }
    }

    @Schema(description = "单条条件视图")
    public record ConditionView(
            @Schema(description = "指标码", example = "PRICE_VS_MA") String indicator,
            @Schema(description = "指标参数", example = "{\"window\":250}") Map<String, Integer> params,
            @Schema(description = "关系", example = "BELOW") String relation,
            @Schema(description = "阈值，小数表示；放大/缩小类关系为空", example = "0.05") BigDecimal value) {

        static ConditionView from(AlertRuleQueryHandler.ConditionView result) {
            return new ConditionView(result.indicator(), result.params(), result.relation(), result.value());
        }
    }

    @Schema(description = "回撤止盈参数视图")
    public record TakeProfitView(
            @Schema(description = "止盈启动收益率", example = "0.15") BigDecimal activation,
            @Schema(description = "高点回撤比例", example = "0.06") BigDecimal pullback,
            @Schema(description = "浮盈收割比例", example = "0.5") BigDecimal harvest,
            @Schema(description = "最低保留仓位", example = "0.5") BigDecimal minimumHolding,
            @Schema(description = "单次最大卖出比例", example = "0.2") BigDecimal maxSingleSell,
            @Schema(description = "冷静期交易日", example = "10") int cooldownDays) {

        static TakeProfitView from(AlertRuleQueryHandler.TakeProfitView result) {
            return result == null ? null : new TakeProfitView(result.activation(), result.pullback(),
                    result.harvest(), result.minimumHolding(), result.maxSingleSell(), result.cooldownDays());
        }
    }

    @Schema(description = "提醒指标元数据")
    public record IndicatorView(
            @Schema(description = "指标码", example = "PRICE_VS_MA") String code,
            @Schema(description = "指标中文名", example = "净值与均线偏离率") String label,
            @Schema(description = "白话解释", example = "净值相对均线的偏离百分比，负值表示跌破均线") String description,
            @Schema(description = "数据来源", example = "MARKET_DATA") String source,
            @Schema(description = "指标取值下限，用于校验与输入提示") BigDecimal minimum,
            @Schema(description = "指标取值上限") BigDecimal maximum,
            @Schema(description = "指标参数定义") List<IndicatorParameterView> parameters,
            @Schema(description = "可选关系") List<IndicatorRelationView> relations) {

        static IndicatorView from(IndicatorMetadataQueryHandler.IndicatorMetadata result) {
            return new IndicatorView(result.code(), result.label(), result.description(), result.source(),
                    result.minimum(), result.maximum(),
                    result.parameters().stream().map(IndicatorParameterView::from).toList(),
                    result.relations().stream().map(IndicatorRelationView::from).toList());
        }
    }

    @Schema(description = "指标参数定义")
    public record IndicatorParameterView(
            @Schema(description = "参数名", example = "window") String name,
            @Schema(description = "参数中文名", example = "均线天数") String label,
            @Schema(description = "默认值", example = "250") int defaultValue,
            @Schema(description = "最小值", example = "5") int minimum,
            @Schema(description = "最大值", example = "250") int maximum) {

        static IndicatorParameterView from(IndicatorMetadataQueryHandler.ParameterMetadata result) {
            return new IndicatorParameterView(result.name(), result.label(), result.defaultValue(),
                    result.minimum(), result.maximum());
        }
    }

    @Schema(description = "指标可选关系")
    public record IndicatorRelationView(
            @Schema(description = "关系枚举值", example = "CROSS_BELOW") String relation,
            @Schema(description = "关系中文别名", example = "下穿") String label,
            @Schema(description = "是否与阈值比较；为 false 时前端不渲染阈值输入框") boolean thresholded,
            @Schema(description = "默认阈值；thresholded 为 true 且此处为空时用户必须填写", example = "0")
            BigDecimal defaultThreshold) {

        static IndicatorRelationView from(IndicatorMetadataQueryHandler.RelationMetadata result) {
            return new IndicatorRelationView(result.relation(), result.label(), result.thresholded(),
                    result.defaultThreshold());
        }
    }

    @Schema(description = "提醒规则试算结果")
    public record PreviewView(
            @Schema(description = "试算使用的交易日") Instant tradingDate,
            @Schema(description = "是否存在命中的基金；单基金范围即该基金是否命中") boolean hit,
            @Schema(description = "参与试算的基金及逐条条件取值") List<PreviewFundView> funds) {

        static PreviewView from(AlertRuleEvaluationQueryHandler.PreviewResult result) {
            return new PreviewView(result.tradingDate(), result.hit(),
                    result.funds().stream().map(PreviewFundView::from).toList());
        }
    }

    @Schema(description = "试算的单个基金")
    public record PreviewFundView(
            @Schema(description = "组合基金 ID", example = "12") long portfolioFundId,
            @Schema(description = "基金代码", example = "161725") String fundCode,
            @Schema(description = "基金名称", example = "招商中证白酒指数") String fundName,
            @Schema(description = "该基金是否命中全部条件") boolean hit,
            @Schema(description = "逐条条件的取值与满足情况") List<PreviewConditionView> conditions) {

        static PreviewFundView from(AlertRuleEvaluationQueryHandler.FundPreview result) {
            return new PreviewFundView(result.portfolioFundId(), result.fundCode(), result.fundName(), result.hit(),
                    result.conditions().stream().map(PreviewConditionView::from).toList());
        }
    }

    @Schema(description = "试算的单条条件")
    public record PreviewConditionView(
            @Schema(description = "条件中文说明", example = "净值与均线偏离率 下穿均线 0") String text,
            @Schema(description = "当前取值；数据不足时为空") BigDecimal currentValue,
            @Schema(description = "该条条件是否满足") boolean satisfied) {

        static PreviewConditionView from(AlertRuleEvaluationQueryHandler.ConditionPreview result) {
            return new PreviewConditionView(result.text(), result.currentValue(), result.satisfied());
        }
    }
}
