package com.fundpilot.backend.importing.application.gateway.importsession;

public interface ImportActorGateway {
    long currentOwnerId();
    void runAsOwner(long ownerId, Runnable action);
}
