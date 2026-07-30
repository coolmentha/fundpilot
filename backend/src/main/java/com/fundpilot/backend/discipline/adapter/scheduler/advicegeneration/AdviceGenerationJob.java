package com.fundpilot.backend.discipline.adapter.scheduler.advicegeneration;

import com.fundpilot.backend.discipline.application.command.advicegeneration.AdviceGenerationCommandHandler;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AdviceGenerationJob {
    private final AdviceGenerationCommandHandler commands;
    private final Clock clock;

    public void generateDaily() {
        var now = clock.instant();
        log.info("纪律建议生成任务启动 date={}", now);
        commands.generateDaily(now);
        log.info("纪律建议生成任务完成 date={}", now);
    }
}
