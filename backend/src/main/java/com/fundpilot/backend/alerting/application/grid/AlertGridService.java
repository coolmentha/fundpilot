package com.fundpilot.backend.alerting.application.grid;

import com.fundpilot.backend.alerting.application.gateway.ruleevaluation.AlertFundFactsGateway.AlertFundFact;
import com.fundpilot.backend.alerting.application.ruletext.AlertRuleText;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRule;
import com.fundpilot.backend.alerting.domain.grid.GridDirection;
import com.fundpilot.backend.alerting.domain.grid.GridParams;
import com.fundpilot.backend.alerting.domain.grid.GridPolicy;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 网格交易提醒的判定编排：把每只目标基金的基准线与当日盘中估值交给 {@link GridPolicy}。
 *
 * <p>与两条建议型规则不同，网格**不持有任何运行期状态**：基准线每次都从「最近一条有成交净值的已确认
 * 流水」重新算出，因此触发后不需要推进、也没有冷静期。同一规则同一交易日的重复发送由通知表的
 * 唯一索引与 {@code alreadySent} 抑制，而不是靠状态机。
 */
@Service
@RequiredArgsConstructor
public class AlertGridService {

    /**
     * 逐基金判定网格命中。
     *
     * <p>被跳过的基金连同原因一起返回，供调用方写日志与邮件提示——「估值不可用」与「无基准流水」是
     * 两种不同的数据缺口，不能静默丢弃。
     */
    public Evaluation evaluate(AlertRule rule, List<AlertFundFact> funds) {
        GridParams params = rule.grid();
        List<Hit> hits = new ArrayList<>();
        List<Skip> skips = new ArrayList<>();
        for (AlertFundFact fund : funds) {
            if (fund.baselineNav() == null) {
                skips.add(new Skip(fund, "无历史成交净值，无法确定基准线"));
                continue;
            }
            if (!fund.estimateAvailable()) {
                skips.add(new Skip(fund, "当日盘中估值不可用（" + estimateLabel(fund.estimateStatus()) + "）"));
                continue;
            }
            GridDirection direction = GridPolicy.evaluate(fund.baselineNav(), fund.valuationNav(), params);
            if (direction == null) {
                continue;
            }
            hits.add(new Hit(fund, detail(fund, params), direction.label()));
        }
        return new Evaluation(List.copyOf(hits), List.copyOf(skips));
    }

    /** 命中说明：基准线及其成交日、上下线路、当前估值与偏离比例。 */
    private static String detail(AlertFundFact fund, GridParams params) {
        BigDecimal baseline = fund.baselineNav();
        BigDecimal valuation = fund.valuationNav();
        return "基准净值 " + plain(baseline)
                + "（" + AlertRuleText.dateLabel(fund.baselineNavDate()) + " 成交）"
                + "，上线路 " + plain(GridPolicy.upperLine(baseline, params))
                + " / 下线路 " + plain(GridPolicy.lowerLine(baseline, params))
                + "，当前估值 " + plain(valuation)
                + "，偏离 " + AlertRuleText.signedPercent(GridPolicy.deviation(baseline, valuation));
    }

    /** 估值状态的中文说明；状态缺失时按未知处理。 */
    private static String estimateLabel(String status) {
        if (status == null || status.isBlank()) {
            return "状态未知";
        }
        return switch (status) {
            case "STALE" -> "估值已过期";
            case "UNAVAILABLE" -> "该基金不提供盘中估值";
            case "TIMEOUT" -> "估值拉取超时";
            case "PARSE_ERROR" -> "估值解析失败";
            case "NOT_ATTEMPTED" -> "尚未尝试拉取估值";
            default -> status;
        };
    }

    private static String plain(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    /** 一次判定出的命中与被跳过基金；跳过的基金带原因，供日志与邮件提示。 */
    public record Evaluation(List<Hit> hits, List<Skip> skips) {
    }

    /** 一只命中的基金：判定依据说明与方向建议。 */
    public record Hit(AlertFundFact fund, String detail, String action) {
    }

    /** 一只无法参与判定的基金及其原因。 */
    public record Skip(AlertFundFact fund, String reason) {
    }
}
