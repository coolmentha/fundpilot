package com.fundpilot.backend.alerting.application.query.ruleevaluation;

import com.fundpilot.backend.alerting.application.command.notificationdelivery.AlertNotificationDispatchCommandHandler;
import com.fundpilot.backend.alerting.application.command.rulemanagement.AlertRuleCommandHandler;
import com.fundpilot.backend.alerting.application.command.rulemanagement.AlertRuleDraft;
import com.fundpilot.backend.alerting.application.condition.ConditionEvaluationService;
import com.fundpilot.backend.alerting.application.gateway.notificationdelivery.AlertEmailGateway;
import com.fundpilot.backend.alerting.application.gateway.notificationdelivery.AlertRecipientGateway;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.application.grid.AlertGridService;
import com.fundpilot.backend.alerting.application.ruletext.AlertRuleText;
import com.fundpilot.backend.alerting.application.suggestion.AlertSuggestionService;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleKind;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleRepository;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleScope;
import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import com.fundpilot.backend.alerting.domain.condition.ConditionEvaluator;
import com.fundpilot.backend.alerting.domain.condition.ConditionRelation;
import com.fundpilot.backend.alerting.domain.condition.IndicatorCode;
import com.fundpilot.backend.alerting.domain.grid.GridDirection;
import com.fundpilot.backend.alerting.domain.grid.GridPolicy;
import com.fundpilot.backend.alerting.domain.notification.AlertNotificationRepository;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitParams;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitPolicy;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 提醒条件求值的唯一编排入口：每日批量评估所有启用规则，另提供保存前的单条试算。
 *
 * <p>不声明事务：每条规则的发送与落库由 {@link AlertNotificationDispatchCommandHandler} 独立事务完成，
 * 单条规则的异常或唯一索引冲突不会污染其它规则。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlertRuleEvaluationQueryHandler {

    private final AlertRuleRepository rules;
    private final AlertNotificationRepository notifications;
    private final AlertFundFactsGateway facts;
    private final ConditionEvaluationService conditions;
    private final AlertSuggestionService suggestions;
    private final AlertGridService grids;
    private final AlertRecipientGateway recipients;
    private final AlertNotificationDispatchCommandHandler dispatches;
    private final Clock clock;

    /**
     * 评估全部启用规则中**除网格以外**的种类，返回计数供任务日志与测试断言。
     *
     * <p>网格被排除在外：它必须在收盘前（14:50–15:00）用盘中估值判定，不能与其余规则的 14:30
     * 时点混在一起，否则会提前发出当天可能失效的越线信号。网格由 {@link #evaluateGrid()} 驱动。
     */
    public EvaluationResult evaluate() {
        return evaluate(kind -> kind != AlertRuleKind.GRID);
    }

    /** 只评估网格规则；由 14:50–14:59 的窗口任务调用。 */
    public EvaluationResult evaluateGrid() {
        return evaluate(kind -> kind == AlertRuleKind.GRID);
    }

    /** 按种类过滤评估启用规则并按用户发送提醒。 */
    private EvaluationResult evaluate(Predicate<AlertRuleKind> kindFilter) {
        Instant now = clock.instant();
        if (!facts.isTradingDay(now)) {
            return new EvaluationResult(0, 0, 0, 0);
        }
        Instant tradingDate = BusinessDay.toDateLabel(now);
        Instant endExclusive = BusinessDay.endExclusive(tradingDate);
        Map<Long, List<AlertRule>> rulesByOwner = rules.findAllEnabled().stream()
                .filter(rule -> kindFilter.test(rule.kind()))
                .collect(Collectors.groupingBy(AlertRule::ownerId, LinkedHashMap::new, Collectors.toList()));
        if (rulesByOwner.isEmpty()) {
            return new EvaluationResult(0, 0, 0, 0);
        }

        int evaluated = 0;
        int matched = 0;
        int sent = 0;
        int failed = 0;
        for (Map.Entry<Long, List<AlertRule>> entry : rulesByOwner.entrySet()) {
            long ownerId = entry.getKey();
            List<AlertRule> ownerRules = entry.getValue();
            Map<Long, AlertFundFactsGateway.AlertFundFact> fundsById = facts.currentFunds(ownerId).stream()
                    .collect(Collectors.toMap(AlertFundFactsGateway.AlertFundFact::portfolioFundId,
                            Function.identity(), (first, second) -> first, LinkedHashMap::new));
            Set<Coverage> covered = ownerRules.stream()
                    .filter(rule -> rule.portfolioFundId() != null)
                    .map(rule -> new Coverage(rule.portfolioFundId(), rule.signature()))
                    .collect(Collectors.toSet());
            String recipient = recipients.emailOf(ownerId).orElse(null);

            for (AlertRule rule : ownerRules) {
                evaluated++;
                try {
                    Outcome result = switch (rule.kind()) {
                        case GRID -> evaluateGridRule(rule, fundsById, covered, tradingDate, ownerId, recipient);
                        case TRAILING_STOP, LOGIC_BROKEN -> evaluateSuggestion(rule, fundsById, covered, tradingDate,
                                endExclusive, ownerId, recipient);
                        case CONDITION -> evaluateConditions(rule, fundsById, covered, tradingDate, endExclusive,
                                ownerId, recipient);
                    };
                    matched += result.matched() ? 1 : 0;
                    sent += result.sent() ? 1 : 0;
                    failed += result.failed() ? 1 : 0;
                } catch (DataIntegrityViolationException exception) {
                    log.info("提醒规则 {} 当日已由其它实例发送，跳过", rule.id());
                } catch (RuntimeException exception) {
                    failed++;
                    log.warn("提醒规则 {} 评估失败", rule.id(), exception);
                }
            }
        }
        EvaluationResult result = new EvaluationResult(evaluated, matched, sent, failed);
        if (evaluated > 0) {
            log.info("价格提醒评估完成: {}", result);
        }
        return result;
    }

    /** 通用条件规则的判定与发送；条件全部满足即为命中。 */
    private Outcome evaluateConditions(AlertRule rule, Map<Long, AlertFundFactsGateway.AlertFundFact> fundsById,
                                       Set<Coverage> covered, Instant tradingDate, Instant endExclusive,
                                       long ownerId, String recipient) {
        List<FundHit> hits = hits(rule, fundsById, covered, endExclusive);
        if (hits.isEmpty() || alreadySent(rule, tradingDate)) {
            return Outcome.none();
        }
        var result = dispatches.dispatch(ownerId, rule, tradingDate, recipient,
                hits.stream().map(AlertRuleEvaluationQueryHandler::row).toList());
        return Outcome.of(result.sent());
    }

    /**
     * 建议型规则的判定与发送：命中即给出建议卖出份额，发送成功后才推进状态机。
     *
     * <p>状态已在求值阶段落库（周期峰值必须逐日累积），因此「当日已提醒」时直接跳过、不重复发送。
     */
    private Outcome evaluateSuggestion(AlertRule rule, Map<Long, AlertFundFactsGateway.AlertFundFact> fundsById,
                                       Set<Coverage> covered, Instant tradingDate, Instant endExclusive, long ownerId,
                                       String recipient) {
        var evaluation = suggestions.evaluate(rule, targets(rule, fundsById, covered), tradingDate, endExclusive);
        if (evaluation.hits().isEmpty() || alreadySent(rule, tradingDate)) {
            return Outcome.none();
        }
        var result = dispatches.dispatch(ownerId, rule, tradingDate, recipient,
                evaluation.hits().stream().map(AlertRuleEvaluationQueryHandler::row).toList());
        if (result.sent()) {
            suggestions.markSent(rule, evaluation, tradingDate);
        }
        return Outcome.of(result.sent());
    }

    private boolean alreadySent(AlertRule rule, Instant tradingDate) {
        return notifications.countSentByRuleAndTradingDate(rule.id(), tradingDate) >= 1;
    }

    /**
     * 网格规则的判定与发送：命中即按方向给出买卖建议，不推进任何状态。
     *
     * <p>无法判定的基金（无基准流水、估值不可用）同时记日志与邮件说明，避免「静默不提醒」被误读为
     * 「没越线」。同一交易日重复进入本方法时由 {@code alreadySent} 拦下，因此窗口内每分钟重跑是安全的。
     */
    private Outcome evaluateGridRule(AlertRule rule, Map<Long, AlertFundFactsGateway.AlertFundFact> fundsById,
                                     Set<Coverage> covered, Instant tradingDate, long ownerId, String recipient) {
        if (alreadySent(rule, tradingDate)) {
            return Outcome.none();
        }
        var targets = targets(rule, fundsById, covered);
        var evaluation = grids.evaluate(rule, targets);
        for (AlertGridService.Skip skip : evaluation.skips()) {
            log.info("网格规则 {} 跳过基金 {}：{}", rule.id(), skip.fund().fundCode(), skip.reason());
        }
        if (evaluation.hits().isEmpty()) {
            return Outcome.none();
        }
        var result = dispatches.dispatch(ownerId, rule, tradingDate, recipient,
                evaluation.hits().stream().map(AlertRuleEvaluationQueryHandler::row).toList(),
                evaluation.skips().stream().map(AlertRuleEvaluationQueryHandler::skipped).toList());
        return Outcome.of(result.sent());
    }

    /** 被跳过基金的邮件行：让「为什么这只没提醒」在邮件里可见，而不是只留在日志。 */
    private static AlertEmailGateway.AlertEmailMessage.SkippedFund skipped(AlertGridService.Skip skip) {
        return new AlertEmailGateway.AlertEmailMessage.SkippedFund(skip.fund().fundCode(), skip.fund().fundName(),
                skip.reason());
    }

    /** 该规则的目标基金中命中全部条件的部分；单基金规则只匹配自身，全局规则剔除已被单基金规则覆盖的基金。 */
    private List<FundHit> hits(AlertRule rule, Map<Long, AlertFundFactsGateway.AlertFundFact> fundsById,
                               Set<Coverage> covered, Instant endExclusive) {
        List<FundHit> hits = new ArrayList<>();
        for (AlertFundFactsGateway.AlertFundFact fund : targets(rule, fundsById, covered)) {
            List<ConditionEvaluationService.Row> rows = conditions.evaluate(fund, rule.conditions(), endExclusive);
            if (ConditionEvaluationService.satisfied(rows)) {
                hits.add(new FundHit(fund, ConditionEvaluationService.detail(rows)));
            }
        }
        return hits;
    }

    /**
     * 保存前试算：用同一套校验与求值口径算出草稿规则的当前取值。
     *
     * <p>只读、不落库、不发邮件，供前端在保存前提示「当前是否满足、差在哪条条件、是否缺数据」。
     * 回撤止盈的完整判定依赖逐日记录的状态机，试算只回答「当前收益率是否已达止盈启动门槛」；
     * 网格没有状态，试算与运行期同一口径（含「估值必须可用」这一条）。
     */
    public PreviewResult preview(long ownerId, AlertRuleCommandHandler.RuleInput input) {
        Map<Long, AlertFundFactsGateway.AlertFundFact> fundsById = facts.currentFunds(ownerId).stream()
                .collect(Collectors.toMap(AlertFundFactsGateway.AlertFundFact::portfolioFundId,
                        Function.identity(), (first, second) -> first, LinkedHashMap::new));
        AlertRuleDraft draft = AlertRuleDraft.validate(input, fundsById.keySet());
        Instant tradingDate = BusinessDay.toDateLabel(clock.instant());
        Instant endExclusive = BusinessDay.endExclusive(tradingDate);
        List<FundPreview> funds = draftTargets(draft, fundsById).stream()
                .map(fund -> preview(fund, draft, endExclusive))
                .toList();
        return new PreviewResult(tradingDate, funds.stream().anyMatch(FundPreview::hit), funds);
    }

    /** 一条草稿规则的目标基金：全局规则覆盖全部关注基金，单基金规则只含自身。 */
    private static List<AlertFundFactsGateway.AlertFundFact> draftTargets(
            AlertRuleDraft draft, Map<Long, AlertFundFactsGateway.AlertFundFact> fundsById) {
        if (draft.scope() != AlertRuleScope.GLOBAL) {
            AlertFundFactsGateway.AlertFundFact fund = fundsById.get(draft.portfolioFundId());
            return fund == null ? List.of() : List.of(fund);
        }
        return List.copyOf(fundsById.values());
    }

    private FundPreview preview(AlertFundFactsGateway.AlertFundFact fund, AlertRuleDraft draft,
                                Instant endExclusive) {
        if (draft.kind() == AlertRuleKind.GRID) {
            return gridPreview(fund, draft);
        }
        List<ConditionEvaluationService.Row> rows = draft.kind().needsConditions()
                ? conditions.evaluate(fund, draft.conditions(), endExclusive)
                : List.of(takeProfitActivation(fund, draft.takeProfit()));
        return new FundPreview(fund.portfolioFundId(), fund.fundCode(), fund.fundName(),
                ConditionEvaluationService.satisfied(rows),
                rows.stream().map(row -> new ConditionPreview(row.summary(), row.latest(), row.satisfied()))
                        .toList());
    }

    /**
     * 网格试算：与运行期同源，都走 {@link GridPolicy}，并同样要求当日估值可用。
     *
     * <p>逐行给出基准线（含成交日）与当前偏离，便于前端在保存前回答「为什么没命中」：缺基准流水、
     * 估值不可用、还是确实在带内。估值不可用时判定为未命中，与运行期一致。
     */
    private static FundPreview gridPreview(AlertFundFactsGateway.AlertFundFact fund, AlertRuleDraft draft) {
        BigDecimal baseline = fund.baselineNav();
        BigDecimal valuation = fund.valuationNav();
        boolean hit = fund.estimateAvailable() && GridPolicy.evaluate(baseline, valuation, draft.grid()) != null;

        String baselineText = baseline == null
                ? "基准净值（该基金没有可用的历史成交净值）"
                : "基准净值 " + plain(baseline) + "（" + AlertRuleText.dateLabel(fund.baselineNavDate()) + " 成交）";
        String bandText = baseline == null
                ? "网格上下线路（需要基准净值）"
                : "上线路 " + plain(GridPolicy.upperLine(baseline, draft.grid()))
                + " / 下线路 " + plain(GridPolicy.lowerLine(baseline, draft.grid()));
        String deviationText = fund.estimateAvailable()
                ? "当日盘中估值相对基准线的偏离"
                : "当日盘中估值相对基准线的偏离（估值不可用：" + fund.estimateStatus() + "）";

        List<ConditionPreview> rows = List.of(
                new ConditionPreview(baselineText, baseline, baseline != null),
                new ConditionPreview(bandText, null, hit),
                new ConditionPreview(deviationText, GridPolicy.deviation(baseline, valuation), hit));
        return new FundPreview(fund.portfolioFundId(), fund.fundCode(), fund.fundName(), hit, rows);
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    /** 回撤止盈试算的那一行：当前总体收益率相对止盈启动门槛的位置。 */
    private static ConditionEvaluationService.Row takeProfitActivation(
            AlertFundFactsGateway.AlertFundFact fund, TakeProfitParams params) {
        BigDecimal overallReturn = null;
        if (TakeProfitPolicy.positive(fund.costPerShare()) && TakeProfitPolicy.positive(fund.holdingShares())
                && TakeProfitPolicy.positive(fund.currentUnitNav())) {
            BigDecimal holdingCost = TakeProfitPolicy.holdingCost(fund.costPerShare(), fund.holdingShares());
            overallReturn = TakeProfitPolicy.overallReturn(
                    TakeProfitPolicy.floatingProfit(fund.costPerShare(), fund.holdingShares(),
                            fund.currentUnitNav()), holdingCost);
        }
        AlertCondition activation = AlertCondition.of(IndicatorCode.HOLDING_RETURN, ConditionRelation.ABOVE,
                params.activation());
        return new ConditionEvaluationService.Row(activation, overallReturn,
                ConditionEvaluator.satisfied(activation, overallReturn == null ? List.of() : List.of(overallReturn)));
    }

    private static List<AlertFundFactsGateway.AlertFundFact> targets(
            AlertRule rule, Map<Long, AlertFundFactsGateway.AlertFundFact> fundsById, Set<Coverage> covered) {
        if (!rule.global()) {
            AlertFundFactsGateway.AlertFundFact fund = fundsById.get(rule.portfolioFundId());
            return fund == null ? List.of() : List.of(fund);
        }
        return fundsById.values().stream()
                .filter(fund -> !covered.contains(new Coverage(fund.portfolioFundId(), rule.signature())))
                .toList();
    }

    private static AlertEmailGateway.AlertEmailMessage.FundRow row(FundHit hit) {
        AlertFundFactsGateway.AlertFundFact fund = hit.fund();
        return new AlertEmailGateway.AlertEmailMessage.FundRow(fund.portfolioFundId(), fund.fundCode(),
                fund.fundName(), hit.detail(), fund.dailyChangePct(), fund.valuationNav(), fund.holdingAmount(),
                fund.unrealizedPnl(), fund.holdingReturnRate(), null);
    }

    /** 建议型规则的邮件行：建议卖出份额只写正文，不生成交易。 */
    private static AlertEmailGateway.AlertEmailMessage.FundRow row(AlertSuggestionService.Suggestion suggestion) {
        AlertFundFactsGateway.AlertFundFact fund = suggestion.fund();
        return new AlertEmailGateway.AlertEmailMessage.FundRow(fund.portfolioFundId(), fund.fundCode(),
                fund.fundName(), suggestion.detail(), fund.dailyChangePct(), fund.valuationNav(),
                fund.holdingAmount(), fund.unrealizedPnl(), fund.holdingReturnRate(), suggestion.action());
    }

    /** 网格规则的邮件行：方向建议（卖出/买入）写在建议列，不含具体份额。 */
    private static AlertEmailGateway.AlertEmailMessage.FundRow row(AlertGridService.Hit hit) {
        AlertFundFactsGateway.AlertFundFact fund = hit.fund();
        return new AlertEmailGateway.AlertEmailMessage.FundRow(fund.portfolioFundId(), fund.fundCode(),
                fund.fundName(), hit.detail(), fund.dailyChangePct(), fund.valuationNav(),
                fund.holdingAmount(), fund.unrealizedPnl(), fund.holdingReturnRate(), hit.action());
    }

    /** 一只命中基金及其条件的现值说明。 */
    private record FundHit(AlertFundFactsGateway.AlertFundFact fund, String detail) {
    }

    /** 已被单基金规则覆盖的「基金 + 口径签名」组合；同一规则种类下口径相同即算覆盖。 */
    private record Coverage(long portfolioFundId, String signature) {
    }

    /** 单条规则的评估去向：是否命中、是否发送成功。 */
    private record Outcome(boolean matched, boolean sent, boolean failed) {

        static Outcome none() {
            return new Outcome(false, false, false);
        }

        static Outcome of(boolean sent) {
            return new Outcome(true, sent, !sent);
        }
    }

    public record EvaluationResult(int evaluatedRules, int matchedRules, int sentRules, int failedRules) {
    }

    /** 试算结果：口径日、是否至少一只基金命中，以及逐只基金的逐条条件现值。 */
    public record PreviewResult(Instant tradingDate, boolean hit, List<FundPreview> funds) {
    }

    /** 单只基金的试算结果：命中说明与逐条条件的当前取值（无数据时为空）。 */
    public record FundPreview(long portfolioFundId, String fundCode, String fundName, boolean hit,
                              List<ConditionPreview> conditions) {
    }

    /** 单条条件在试算时的口径、当前取值与是否满足。 */
    public record ConditionPreview(String text, BigDecimal currentValue, boolean satisfied) {
    }
}