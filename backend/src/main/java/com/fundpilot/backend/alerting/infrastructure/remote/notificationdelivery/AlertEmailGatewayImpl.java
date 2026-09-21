package com.fundpilot.backend.alerting.infrastructure.remote.notificationdelivery;

import com.fundpilot.backend.alerting.application.gateway.notificationdelivery.AlertEmailGateway;
import com.fundpilot.backend.alerting.application.gateway.notificationdelivery.AlertEmailGateway.AlertEmailMessage.FundRow;
import com.fundpilot.backend.alerting.domain.alertrule.AlertRuleType;
import com.fundpilot.backend.alerting.infrastructure.configuration.AlertingProperties;
import com.fundpilot.backend.sharedkernel.BusinessDay;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * 通过 SMTP 投递提醒邮件。
 *
 * <p>邮件服务未配置（无 {@link JavaMailSender} 或发件人为空）时返回失败而非抛异常，使提醒只落库为
 * {@code FAILED}，不影响主体功能与后续交易日重试。
 */
@Component
@RequiredArgsConstructor
public class AlertEmailGatewayImpl implements AlertEmailGateway {

    private static final String MISSING_MAIL_SERVICE = "邮件服务未配置";
    private static final int FAILURE_REASON_MAX_LENGTH = 255;
    private static final String RISE_COLOR = "#d4380d";
    private static final String DROP_COLOR = "#389e0d";
    private static final DateTimeFormatter SENT_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(BusinessDay.ZONE);

    private final ObjectProvider<JavaMailSender> mailSenders;
    private final AlertingProperties properties;
    private final Clock clock;

    @Override
    public DeliveryResult send(AlertEmailMessage message) {
        JavaMailSender sender = mailSenders.getIfAvailable();
        if (sender == null || properties.mailFrom() == null || properties.mailFrom().isBlank()) {
            return DeliveryResult.failure(MISSING_MAIL_SERVICE);
        }
        try {
            MimeMessage mime = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.mailFrom());
            helper.setTo(message.recipient());
            helper.setSubject(subject(message));
            helper.setText(plainText(message), html(message));
            sender.send(mime);
            return DeliveryResult.success();
        } catch (MessagingException | RuntimeException exception) {
            return DeliveryResult.failure(reason(exception));
        }
    }

    private static String subject(AlertEmailMessage message) {
        String condition = phrase(message.ruleType(), percent(message.threshold()));
        if (message.funds().size() == 1) {
            FundRow fund = message.funds().getFirst();
            return "【FundPilot 提醒】" + fund.fundName() + "(" + fund.fundCode() + ") " + condition;
        }
        return "【FundPilot 提醒】" + message.funds().size() + " 只基金触发" + condition;
    }

    private String html(AlertEmailMessage message) {
        String accent = color(message.ruleType());
        String condition = phrase(message.ruleType(), percent(message.threshold()));
        StringBuilder builder = new StringBuilder(2048);
        builder.append("<div style=\"font-family:-apple-system,'PingFang SC','Microsoft YaHei',Arial,sans-serif;")
                .append("color:#262626;font-size:14px;line-height:1.6\">")
                .append("<h2 style=\"margin:0 0 4px;font-size:20px;color:").append(accent).append("\">")
                .append(escape(condition)).append("</h2>")
                .append("<p style=\"margin:0 0 16px;color:#8c8c8c\">规则阈值 ")
                .append(escape(percent(message.threshold())))
                .append("% · 命中 ").append(message.funds().size()).append(" 只基金</p>")
                .append("<table cellpadding=\"0\" cellspacing=\"0\" ")
                .append("style=\"border-collapse:collapse;width:100%;font-size:13px\"><thead><tr ")
                .append("style=\"background:#fafafa;text-align:left\">");
        for (String header : new String[] {"基金名称", "代码", "触发条件", "当前净值(估值)", "当日涨跌幅",
                "持仓盈亏", "持仓收益率", "操作"}) {
            builder.append(cell("th", header, "#595959"));
        }
        builder.append("</tr></thead><tbody>");
        for (FundRow fund : message.funds()) {
            builder.append("<tr>")
                    .append(cell("td", fund.fundName(), null))
                    .append(cell("td", fund.fundCode(), null))
                    .append(cell("td", shortPhrase(message.ruleType()) + " " + percent(fund.observedValue()),
                            accent))
                    .append(cell("td", nav(fund.valuationNav()), null))
                    .append(cell("td", signedPercent(fund.dailyChangePct()), changeColor(fund.dailyChangePct())))
                    .append(cell("td", signedMoney(fund.unrealizedPnl()),
                            changeColor(fund.unrealizedPnl())))
                    .append(cell("td", signedPercent(fund.holdingReturnRate()),
                            changeColor(fund.holdingReturnRate())))
                    .append("<td style=\"padding:8px;border:1px solid #f0f0f0\">")
                    .append(detailLink(fund.portfolioFundId())).append("</td>")
                    .append("</tr>");
        }
        builder.append("</tbody></table>")
                .append("<p style=\"margin:16px 0 0;color:#8c8c8c;font-size:12px\">发送时间：")
                .append(SENT_AT.format(clock.instant())).append("（北京时间）<br/>")
                .append("本邮件由 FundPilot 自动发送；同一规则每个交易日最多提醒一次。</p></div>");
        return builder.toString();
    }

    private String plainText(AlertEmailMessage message) {
        String condition = phrase(message.ruleType(), percent(message.threshold()));
        StringBuilder builder = new StringBuilder(512);
        builder.append(condition).append("（阈值 ").append(percent(message.threshold())).append("%）\n");
        for (FundRow fund : message.funds()) {
            builder.append("- ").append(fund.fundName()).append("(").append(fund.fundCode()).append(") ")
                    .append(shortPhrase(message.ruleType())).append(" ")
                    .append(percent(fund.observedValue())).append("%")
                    .append("，估值净值 ").append(nav(fund.valuationNav()))
                    .append("，当日涨跌 ").append(signedPercent(fund.dailyChangePct()))
                    .append("，持仓盈亏 ").append(signedMoney(fund.unrealizedPnl()))
                    .append("，持仓收益率 ").append(signedPercent(fund.holdingReturnRate()))
                    .append('\n');
            builder.append("  详情：").append(detailUrl(fund.portfolioFundId())).append('\n');
        }
        builder.append("\n发送时间：").append(SENT_AT.format(clock.instant())).append("（北京时间）\n")
                .append("本邮件由 FundPilot 自动发送。");
        return builder.toString();
    }

    private String detailLink(long portfolioFundId) {
        String url = detailUrl(portfolioFundId);
        return url.isEmpty() ? "-" : "<a href=\"" + url + "\" style=\"color:#1677ff\">查看详情</a>";
    }

    private String detailUrl(long portfolioFundId) {
        String base = properties.appBaseUrl() == null ? "" : properties.appBaseUrl().trim();
        return base.isEmpty() ? "" : base.replaceAll("/+$", "") + "/funds/" + portfolioFundId;
    }

    private static String cell(String tag, String value, String color) {
        StringBuilder builder = new StringBuilder("<").append(tag)
                .append(" style=\"padding:8px;border:1px solid #f0f0f0");
        if (color != null) {
            builder.append(";color:").append(color);
        }
        return builder.append("\">").append(escape(value)).append("</").append(tag).append(">").toString();
    }

    /** A 股习惯：涨用红、跌用绿。 */
    private static String color(AlertRuleType type) {
        return type == AlertRuleType.DROP ? DROP_COLOR : RISE_COLOR;
    }

    private static String changeColor(BigDecimal value) {
        if (value == null || value.signum() == 0) {
            return "#595959";
        }
        return value.signum() > 0 ? RISE_COLOR : DROP_COLOR;
    }

    private static String phrase(AlertRuleType type, String thresholdPercent) {
        return shortPhrase(type) + "已达 " + thresholdPercent + "%";
    }

    private static String shortPhrase(AlertRuleType type) {
        return switch (type) {
            case RISE -> "上涨";
            case DROP -> "下跌";
            case PROFIT -> "盈利";
        };
    }

    private static String percent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        return value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String signedPercent(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        BigDecimal scaled = value.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
        return (scaled.signum() > 0 ? "+" : "") + scaled.toPlainString() + "%";
    }

    private static String signedMoney(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
        return (scaled.signum() > 0 ? "+" : "") + scaled.toPlainString();
    }

    private static String nav(BigDecimal value) {
        return value == null ? "-" : value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "-";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String reason(Exception exception) {
        String message = exception.getMessage();
        String value = message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
        return value.length() <= FAILURE_REASON_MAX_LENGTH
                ? value
                : value.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
