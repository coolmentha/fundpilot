package com.fundpilot.backend.discipline.adapter.event.advicegeneration;

import com.fundpilot.backend.discipline.application.command.advicegeneration.AdviceGenerationCommandHandler;
import com.fundpilot.backend.marketdata.application.event.indicatorrefresh.MarketIndicatorsRefreshed;
import lombok.RequiredArgsConstructor;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class MarketIndicatorsRefreshedAdviceListener {
    private final AdviceGenerationCommandHandler commands;

    @ApplicationModuleListener
    public void onMarketIndicatorsRefreshed(MarketIndicatorsRefreshed event) {
        commands.generateDaily(event.occurredAt());
    }
}
