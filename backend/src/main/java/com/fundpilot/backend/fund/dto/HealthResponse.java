package com.fundpilot.backend.fund.dto;

import java.time.Instant;

public record HealthResponse(
        String status,
        Instant checkedAt
) {
}
