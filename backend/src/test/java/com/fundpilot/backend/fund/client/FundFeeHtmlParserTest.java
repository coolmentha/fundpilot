package com.fundpilot.backend.fund.client;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link FundFeeHtmlParser} 单测:用 001071(A类) + 005919(C类)真实 jjfl HTML 片段验证解析。
 */
class FundFeeHtmlParserTest {

    // ===== 001071 华安媒体互联网混合A(A类)HTML 片段 =====
    // 申购费率表:有 strike 原费率 + | 优惠费率
    private static final String HTML_001071 = """
            <html><body>
            <div class="box"><div class="boxitem w790">
            <h4 class="t"><label class="left">运作费用</label><label class="right"></label></h4>
            <table class="w650 comm"><tbody>
            <tr><td>管理费率</td><td>1.20%（每年）</td></tr>
            <tr><td>托管费率</td><td>0.20%（每年）</td></tr>
            <tr><td>销售服务费率</td><td class="w135">0.00%（每年）</td></tr>
            </tbody></table>
            </div></div>
            <div class="box"><div class="boxitem w790">
            <h4 class="t"><label class="left">认购费率</label><label class="right"></label></h4>
            <table class="w650 comm jjfl"><tbody><tr><td>小于100万元</td><td>1.20%</td></tr></tbody></table>
            </div></div>
            <div class="box"><div class="boxitem w790">
            <h4 class="t"><label class="left">申购费率</label><label class="right"></label></h4><div class="space0"></div>
            <table class="w650 comm jjfl"><thead><tr><th>适用金额</th><th>原费率 | 天天基金优惠费率</th></tr></thead>
            <tbody>
            <tr><td>小于100万元</td><td><strike class='gray'>1.50%</strike>&nbsp;&nbsp;|&nbsp;&nbsp;0.15%</td></tr>
            <tr><td>大于等于100万元，小于300万元</td><td><strike class='gray'>1.20%</strike>&nbsp;&nbsp;|&nbsp;&nbsp;0.12%</td></tr>
            </tbody></table>
            </div></div>
            <div class="box"><div class="boxitem w790">
            <h4 class="t"><label class="left">赎回费率<a name="shfl"></a></label><label class="right"></label></h4><div class="space0"></div>
            <table class="w650 comm jjfl"><thead><tr><th>适用期限</th><th>赎回费率</th></tr></thead>
            <tbody>
            <tr><td>小于7天</td><td>1.50%</td></tr>
            <tr><td>大于等于7天，小于30天</td><td>0.75%</td></tr>
            <tr><td>大于等于30天，小于365天</td><td>0.50%</td></tr>
            <tr><td>大于等于365天，小于730天</td><td>0.25%</td></tr>
            <tr><td>大于等于730天</td><td>0.00%</td></tr>
            </tbody></table>
            </div></div>
            </body></html>
            """;

    // ===== 005919 天弘中证500ETF联接C(C类)HTML 片段 =====
    // 申购费率:单列 0.00%(无 strike/|);销售服务费 0.20%/年;赎回两档
    private static final String HTML_005919 = """
            <html><body>
            <div class="box"><div class="boxitem w790">
            <h4 class="t"><label class="left">运作费用</label></h4>
            <table class="w650 comm"><tbody>
            <tr><td>管理费率</td><td>0.50%（每年）</td></tr>
            <tr><td>托管费率</td><td>0.10%（每年）</td></tr>
            <tr><td>销售服务费率</td><td class="w135">0.20%（每年）</td></tr>
            </tbody></table>
            </div></div>
            <div class="box"><div class="boxitem w790">
            <h4 class="t"><label class="left">申购费率</label></h4><div class="space0"></div>
            <table class="w650 comm jjfl"><thead><tr><th>适用金额</th><th>费率</th></tr></thead>
            <tbody><tr><td>小于100万元</td><td>0.00%</td></tr></tbody></table>
            </div></div>
            <div class="box"><div class="boxitem w790">
            <h4 class="t"><label class="left">赎回费率</label></h4><div class="space0"></div>
            <table class="w650 comm jjfl"><thead><tr><th>适用期限</th><th>赎回费率</th></tr></thead>
            <tbody>
            <tr><td>小于7天</td><td>1.50%</td></tr>
            <tr><td>大于等于7天</td><td>0.00%</td></tr>
            </tbody></table>
            </div></div>
            </body></html>
            """;

    @Test
    void parsePurchaseRate_001071_A类_strike原费率加优惠费率() {
        FundFeeHtmlParser.PurchaseFeeRate result = FundFeeHtmlParser.parsePurchaseRate(HTML_001071);
        assertThat(result).isNotNull();
        assertThat(result.originalRate()).isEqualByComparingTo(new BigDecimal("0.015"));
        assertThat(result.discountRate()).isEqualByComparingTo(new BigDecimal("0.0015"));
    }

    @Test
    void parsePurchaseRate_005919_C类_单列零费率() {
        FundFeeHtmlParser.PurchaseFeeRate result = FundFeeHtmlParser.parsePurchaseRate(HTML_005919);
        assertThat(result).isNotNull();
        assertThat(result.originalRate()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.discountRate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parseRedemptionLadder_001071_五档阶梯() {
        List<RedemptionTier> ladder = FundFeeHtmlParser.parseRedemptionLadder(HTML_001071);
        assertThat(ladder).hasSize(5);
        assertThat(ladder.get(0).maxDays()).isEqualTo(7);
        assertThat(ladder.get(0).rate()).isEqualByComparingTo(new BigDecimal("0.015"));
        assertThat(ladder.get(1).maxDays()).isEqualTo(30);
        assertThat(ladder.get(1).rate()).isEqualByComparingTo(new BigDecimal("0.0075"));
        assertThat(ladder.get(2).maxDays()).isEqualTo(365);
        assertThat(ladder.get(2).rate()).isEqualByComparingTo(new BigDecimal("0.005"));
        assertThat(ladder.get(3).maxDays()).isEqualTo(730);
        assertThat(ladder.get(3).rate()).isEqualByComparingTo(new BigDecimal("0.0025"));
        assertThat(ladder.get(4).maxDays()).isNull();
        assertThat(ladder.get(4).rate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parseRedemptionLadder_005919_C类_两档() {
        List<RedemptionTier> ladder = FundFeeHtmlParser.parseRedemptionLadder(HTML_005919);
        assertThat(ladder).hasSize(2);
        assertThat(ladder.get(0).maxDays()).isEqualTo(7);
        assertThat(ladder.get(0).rate()).isEqualByComparingTo(new BigDecimal("0.015"));
        assertThat(ladder.get(1).maxDays()).isNull();
        assertThat(ladder.get(1).rate()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parseSalesServiceFee_001071_A类_零() {
        BigDecimal fee = FundFeeHtmlParser.parseSalesServiceFee(HTML_001071);
        assertThat(fee).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void parseSalesServiceFee_005919_C类_百分之零点二() {
        BigDecimal fee = FundFeeHtmlParser.parseSalesServiceFee(HTML_005919);
        assertThat(fee).isEqualByComparingTo(new BigDecimal("0.002"));
    }

    @Test
    void parsePercent_各种格式() {
        assertThat(FundFeeHtmlParser.parsePercent("1.50%")).isEqualByComparingTo(new BigDecimal("0.015"));
        assertThat(FundFeeHtmlParser.parsePercent("0.00%（每年）")).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(FundFeeHtmlParser.parsePercent("0.75%")).isEqualByComparingTo(new BigDecimal("0.0075"));
        assertThat(FundFeeHtmlParser.parsePercent("无费率")).isNull();
        assertThat(FundFeeHtmlParser.parsePercent(null)).isNull();
    }

    @Test
    void parseMaxDays_各种期限文本() {
        assertThat(FundFeeHtmlParser.parseMaxDays("小于7天")).isEqualTo(7);
        assertThat(FundFeeHtmlParser.parseMaxDays("大于等于7天，小于30天")).isEqualTo(30);
        assertThat(FundFeeHtmlParser.parseMaxDays("大于等于730天")).isNull();
        assertThat(FundFeeHtmlParser.parseMaxDays(null)).isNull();
    }

    @Test
    void parsePurchaseRate_空HTML返null() {
        assertThat(FundFeeHtmlParser.parsePurchaseRate(null)).isNull();
        assertThat(FundFeeHtmlParser.parsePurchaseRate("")).isNull();
    }

    @Test
    void parseRedemptionLadder_空HTML返空列表() {
        assertThat(FundFeeHtmlParser.parseRedemptionLadder(null)).isEmpty();
        assertThat(FundFeeHtmlParser.parseRedemptionLadder("")).isEmpty();
    }
}
