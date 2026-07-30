package com.fundpilot.backend.discipline.adapter.api.advicegeneration;

import com.fundpilot.backend.discipline.application.command.advicegeneration.AdviceGenerationCommandHandler;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdviceGenerationApi {
    private final AdviceGenerationCommandHandler commands;

    public void generateDaily(Instant occurredAt) {
        commands.generateDaily(occurredAt);
    }
}
