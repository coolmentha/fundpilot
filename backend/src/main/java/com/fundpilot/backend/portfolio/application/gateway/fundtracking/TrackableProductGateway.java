package com.fundpilot.backend.portfolio.application.gateway.fundtracking;

public interface TrackableProductGateway {
    boolean exists(long fundProductId);
}
