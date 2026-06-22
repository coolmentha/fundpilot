package com.fundpilot.backend.fund.service;

import com.fundpilot.backend.fund.dto.HealthResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

@Service
public class HealthService {

    private final Clock clock;

    public HealthService() {
        this(Clock.systemUTC());
    }

    HealthService(Clock clock) {
        this.clock = clock;
    }

    public HealthResponse getHealth() {
        return new HealthResponse("UP", Instant.now(clock));
    }
}
