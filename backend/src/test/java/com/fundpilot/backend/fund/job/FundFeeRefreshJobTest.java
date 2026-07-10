package com.fundpilot.backend.fund.job;

import com.fundpilot.backend.fund.service.FundFeeService;
import org.junit.jupiter.api.Test;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FundFeeRefreshJobTest {

    @Test
    void refreshDaily_委托刷新持仓基金费率() {
        FundFeeService service = mock(FundFeeService.class);
        FundFeeRefreshJob job = new FundFeeRefreshJob(service);

        job.refreshDaily();

        verify(service).refreshHoldingFunds();
    }

    @Test
    void 不在ApplicationReadyEvent同步执行逐基金费率爬取() {
        boolean hasStartupListener = Arrays.stream(FundFeeRefreshJob.class.getDeclaredMethods())
                .anyMatch(method -> method.isAnnotationPresent(EventListener.class));

        assertThat(hasStartupListener).isFalse();
    }

    @Test
    void refreshDaily_北京时间两点半执行_早于三点交易确认() throws NoSuchMethodException {
        Scheduled scheduled = FundFeeRefreshJob.class.getDeclaredMethod("refreshDaily")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 30 2 * * *");
        assertThat(scheduled.zone()).isEqualTo("Asia/Shanghai");
    }
}
