package com.fundpilot.backend.market.service.support;

import com.fundpilot.backend.market.client.FundNavSnapshot;
import com.fundpilot.backend.market.enums.WeeklyMacdState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyMacdCalculatorTest {

    @Test
    void 数据不足_30_周_返回_empty() {
        // 20 个工作日 = 4 周,远不足 30 周
        List<FundNavSnapshot> series = linearSeries(20, 1.0, 0.0);

        assertThat(WeeklyMacdCalculator.calculate(series)).isEmpty();
    }

    @Test
    void null_输入_返回_empty() {
        assertThat(WeeklyMacdCalculator.calculate(null)).isEmpty();
    }

    @Test
    void 单调上升序列_红柱_RED_SHRINKING() {
        // 200 个工作日 ≈ 40 周,累计净值线性上升 1.00 -> 3.00
        List<FundNavSnapshot> series = linearSeries(200, 1.0, 0.01);

        Optional<WeeklyMacdState> result = WeeklyMacdCalculator.calculate(series);

        assertThat(result).contains(WeeklyMacdState.RED_SHRINKING);
    }

    @Test
    void 单调下降序列_落入绿柱分支() {
        // 200 个工作日线性下降——稳态下绿柱趋稳,最末两周|今| <= |上|,落 GREEN_SHRINKING;
        // 加速下跌阶段会落 GREEN_EXPANDING。本测试只校验落入绿柱区(柱 < 0),不区分扩缩。
        List<FundNavSnapshot> series = linearSeries(200, 3.0, -0.01);

        Optional<WeeklyMacdState> result = WeeklyMacdCalculator.calculate(series);

        assertThat(result).isPresent();
        assertThat(result.get()).isIn(WeeklyMacdState.GREEN_SHRINKING, WeeklyMacdState.GREEN_EXPANDING);
    }

    @Test
    void classifyState_红柱_返回_RED_SHRINKING() {
        assertThat(WeeklyMacdCalculator.classifyState(0.05, 0.03))
                .isEqualTo(WeeklyMacdState.RED_SHRINKING);
    }

    @Test
    void classifyState_绿柱且绝对值扩大_返回_GREEN_EXPANDING() {
        // 今柱 -0.05,上柱 -0.03:|今| > |上|,绿柱扩大
        assertThat(WeeklyMacdCalculator.classifyState(-0.05, -0.03))
                .isEqualTo(WeeklyMacdState.GREEN_EXPANDING);
    }

    @Test
    void classifyState_绿柱且绝对值缩小_返回_GREEN_SHRINKING() {
        // 今柱 -0.03,上柱 -0.05:|今| < |上|,绿柱缩小
        assertThat(WeeklyMacdCalculator.classifyState(-0.03, -0.05))
                .isEqualTo(WeeklyMacdState.GREEN_SHRINKING);
    }

    @Test
    void classifyState_绿柱且绝对值相等_返回_GREEN_SHRINKING() {
        // 边界:|今| == |上|,按 &lt;= 处理落 GREEN_SHRINKING
        assertThat(WeeklyMacdCalculator.classifyState(-0.04, -0.04))
                .isEqualTo(WeeklyMacdState.GREEN_SHRINKING);
    }

    /** 工作日序列(跳过周末),起价 startPrice,每个工作日累加 delta。 */
    private static List<FundNavSnapshot> linearSeries(int workingDays, double startPrice, double delta) {
        return linearSeriesFrom(LocalDate.of(2025, 1, 1), workingDays, startPrice, delta);
    }

    private static List<FundNavSnapshot> linearSeriesFrom(
            LocalDate start, int workingDays, double startPrice, double delta) {
        List<FundNavSnapshot> result = new ArrayList<>();
        LocalDate cursor = start;
        double price = startPrice;
        int count = 0;
        while (count < workingDays) {
            if (cursor.getDayOfWeek().getValue() <= 5) {
                BigDecimal nav = BigDecimal.valueOf(price);
                result.add(new FundNavSnapshot(cursor, nav, nav));
                price += delta;
                count++;
            }
            cursor = cursor.plusDays(1);
        }
        return result;
    }
}
