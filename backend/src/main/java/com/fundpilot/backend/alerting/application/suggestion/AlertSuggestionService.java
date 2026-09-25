package com.fundpilot.backend.alerting.application.suggestion;

import com.fundpilot.backend.alerting.application.condition.ConditionEvaluationService;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway.AlertFundFact;
import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertIndicatorGateway;
import com.fundpilot.backend.alerting.application.ruletext.AlertRuleText;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleKind;
import com.fundpilot.backend.alerting.domain.condition.AlertCondition;
import com.fundpilot.backend.alerting.domain.condition.ConditionEvaluator;
import com.fundpilot.backend.alerting.domain.condition.ConditionRelation;
import com.fundpilot.backend.alerting.domain.condition.IndicatorCode;
import com.fundpilot.backend.alerting.domain.suggestion.SuggestionState;
import com.fundpilot.backend.alerting.domain.suggestion.SuggestionStateRepository;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitParams;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitPhase;
import com.fundpilot.backend.alerting.domain.suggestion.TakeProfitPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 建议型规则的判定与状态机编排：逻辑破坏止损与回撤止盈。
 *
 * <p>结果只是「建议卖出多少份额」的纯通知，不生成交易（D2）；判定口径照搬旧 {@code AdvicePolicy}，
 * 因此同一份持仓事实在迁移前后一定得到同一个判定结论。
 *
 * <p>状态推进时机：周期峰值的累积必须逐日写回（否则回撤判定会失真），因此求值阶段就地落库；
 * 「已触发 / 冷静期」的推进发生在邮件发送成功之后，发送失败时次日会重新判定并重试。
 */
@Service
@RequiredArgsConstructor
public class AlertSuggestionService {

    /** 主动型基金没有基准指数，旧口径下量能条件对它不适用。 */
    private static final String ACTIVE_PRODUCT_TYPE = "ACTIVE";

    private static final int NAV_SCALE = 4;

    private final AlertFundFactsGateway facts;
    private final AlertIndicatorGateway indicators;
    private final ConditionEvaluationService conditions;
    private final SuggestionStateRepository states;

    /** 逐基金判定建议型规则；命中基金附带建议内容与已落库状态，其余基金的状态就地收尾。 */
    public Evaluation evaluate(AlertRule rule, List<AlertFundFact> funds, Instant tradingDate,
                               Instant endExclusive) {
        Map<Long, SuggestionState> statesByFund = states.findByRule(rule.id()).stream()
                .collect(Collectors.toMap(SuggestionState::portfolioFundId, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));
        Map<Long, AlertFundFact> fundsById = funds.stream()
                .collect(Collectors.toMap(AlertFundFact::portfolioFundId, Function.identity(),
                        (first, second) -> first, LinkedHashMap::new));

        List<Suggestion> hits = new ArrayList<>();
        for (AlertFundFact fund : funds) {
            if (!fund.held()) {
                continue;
            }
            SuggestionState state = statesByFund.computeIfAbsent(fund.portfolioFundId(),
                    fundId -> SuggestionState.create(rule.id(), rule.ownerId(), fundId));
            Suggestion suggestion = switch (rule.kind()) {
                case LOGIC_BROKEN -> logicBroken(rule, fund, tradingDate, endExclusive, state);
                case TRAILING_STOP -> trailingStop(rule, fund, tradingDate, state);
                case CONDITION -> null;
            };
            if (suggestion != null) {
                hits.add(suggestion);
            }
        }
        // 已不在持（或已不在本规则范围内）的基金回到起点，允许下次重新提醒
        statesByFund.values().stream()
                .filter(state -> !held(fundsById.get(state.portfolioFundId())))
                .forEach(this::reset);
        return new Evaluation(List.copyOf(hits));
    }

    /**
     * 邮件发送成功后推进状态：命中基金标记为已触发，回撤止盈随后立即进入冷静期。
     *
     * <p>通知链路已降级为纯通知，不再等待「卖出确认」，因此发送成功即进入冷静期。
     */
    public void markSent(AlertRule rule, Evaluation evaluation, Instant tradingDate) {
        for (Suggestion hit : evaluation.hits()) {
            hit.state().markTriggered();
            if (rule.kind() == AlertRuleKind.TRAILING_STOP) {
                hit.state().enterCooldown(tradingDate);
            }
            states.save(hit.state());
        }
    }

    /**
     * 逻辑破坏止损：条件全部满足，且非主动型基金还需「放量下跌」（照搬旧 {@code AdvicePolicy} 的口径）。
     *
     * <p>连续命中期间只提醒一次：标记为已触发后不再重复命中，直到条件恢复（不再满足）才回到起点。
     */
    private Suggestion logicBroken(AlertRule rule, AlertFundFact fund, Instant tradingDate, Instant endExclusive,
                                   SuggestionState state) {
        if (state.phase() == TakeProfitPhase.TRIGGERED) {
            return null;
        }
        List<ConditionEvaluationService.Row> rows = conditions.evaluate(fund, rule.conditions(), endExclusive);
        ConditionEvaluationService.Row volume = volumeDrop(fund, endExclusive);
        if (!ConditionEvaluationService.satisfied(rows) || (volume != null && !volume.satisfied())) {
            reset(state);
            return null;
        }
        if (!TakeProfitPolicy.positive(fund.holdingShares())) {
            return null;
        }
        long daysSinceLastBuy = fund.lastBuyTime() == null
                ? TakeProfitPolicy.MIN_HOLD_TRADING_DAYS
                : facts.tradingDaysBetween(fund.lastBuyTime(), tradingDate);
        String detail = ConditionEvaluationService.detail(rows) + (volume == null
                ? "；主动型基金不适用量能条件"
                : "，" + volume.detail());
        String action = "建议全部卖出 " + quantity(fund.holdingShares()) + " 份"
                + (daysSinceLastBuy < TakeProfitPolicy.MIN_HOLD_TRADING_DAYS
                ? "（加仓至今不足 " + TakeProfitPolicy.MIN_HOLD_TRADING_DAYS + " 个交易日，按纪律仍建议卖出）" : "");
        return new Suggestion(fund, detail, action, state);
    }

    /** 回撤止盈：收益率达标后记录周期峰值，回撤达标即按取 min 公式给出建议卖出份额。 */
    private Suggestion trailingStop(AlertRule rule, AlertFundFact fund, Instant tradingDate, SuggestionState state) {
        TakeProfitParams params = rule.takeProfit();
        BigDecimal holdingShares = fund.holdingShares();
        BigDecimal unitNav = fund.currentUnitNav();
        BigDecimal accumulatedNav = fund.currentAccumulatedNav();
        BigDecimal matureShares = fund.matureRedeemableShares();

        BigDecimal floatingProfit = null;
        BigDecimal overallReturn = null;
        if (TakeProfitPolicy.positive(fund.costPerShare()) && TakeProfitPolicy.positive(holdingShares)
                && TakeProfitPolicy.positive(unitNav)) {
            BigDecimal holdingCost = TakeProfitPolicy.holdingCost(fund.costPerShare(), holdingShares);
            floatingProfit = TakeProfitPolicy.floatingProfit(fund.costPerShare(), holdingShares, unitNav);
            overallReturn = TakeProfitPolicy.overallReturn(floatingProfit, holdingCost);
        }
        boolean cooldownFinished = state.cooldownStartedAt() == null
                || facts.tradingDaysBetween(state.cooldownStartedAt(), tradingDate) >= params.cooldownDays();
        boolean canTrigger = state.prepareTakeProfit(overallReturn, accumulatedNav, params.activation(),
                tradingDate, cooldownFinished);
        SuggestionState persisted = persist(state);

        if (!canTrigger || !TakeProfitPolicy.positive(matureShares)) {
            return null;
        }
        BigDecimal pullback = TakeProfitPolicy.pullback(state.cyclePeakNav(), accumulatedNav);
        if (pullback == null || pullback.compareTo(params.pullback()) < 0) {
            return null;
        }
        BigDecimal suggested = TakeProfitPolicy.suggestedShares(floatingProfit, holdingShares, unitNav, params,
                matureShares);
        if (!TakeProfitPolicy.positive(suggested)) {
            return null;
        }
        String detail = "盈利 " + AlertRuleText.percent(overallReturn) + " 后回撤 "
                + AlertRuleText.percent(pullback) + "（周期峰值累计净值 "
                + decimal(state.cyclePeakNav()) + "）";
        String action = "建议卖出 " + quantity(suggested) + " 份（约占持仓 "
                + AlertRuleText.percent(suggested.divide(holdingShares, 4, RoundingMode.HALF_UP)) + "）";
        return new Suggestion(fund, detail, action, persisted);
    }

    /**
     * 非主动型基金的量能条件：指数收阴且量比达 1.5；主动型基金没有基准指数，返回 null 表示不适用。
     */
    private ConditionEvaluationService.Row volumeDrop(AlertFundFact fund, Instant endExclusive) {
        if (ACTIVE_PRODUCT_TYPE.equals(fund.productType())) {
            return null;
        }
        AlertCondition condition = AlertCondition.of(IndicatorCode.VOLUME_DROP, ConditionRelation.ABOVE);
        List<BigDecimal> values = indicators.values(fund, condition, endExclusive);
        return new ConditionEvaluationService.Row(condition, values.isEmpty() ? null : values.getLast(),
                ConditionEvaluator.satisfied(condition, values));
    }

    /** 从未被推进过的新状态不建行；已有行则始终写回（哪怕回到起点，也要把旧行清干净）。 */
    private SuggestionState persist(SuggestionState state) {
        return state.id() == null && state.pristine() ? state : states.save(state);
    }

    /** 条件恢复或持仓清空时回到起点；本来就在起点的状态无需落库。 */
    private void reset(SuggestionState state) {
        if (state.pristine()) {
            return;
        }
        state.reset();
        persist(state);
    }

    private static boolean held(AlertFundFact fund) {
        return fund != null && fund.held();
    }

    /** 份额的中文展示：至多两位小数。 */
    private static String quantity(BigDecimal shares) {
        return shares.setScale(2, RoundingMode.HALF_DOWN).stripTrailingZeros().toPlainString();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "-" : value.setScale(NAV_SCALE, RoundingMode.HALF_UP).stripTrailingZeros()
                .toPlainString();
    }

    /** 一次判定出的全部命中基金。 */
    public record Evaluation(List<Suggestion> hits) {
    }

    /** 一只命中基金的建议内容：触发原因、建议操作与本次状态推进所依据的状态对象。 */
    public record Suggestion(AlertFundFact fund, String detail, String action, SuggestionState state) {
    }
}