package com.fundpilot.backend.importing.infrastructure.gateway.importsession;

import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportActorGateway;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ImportActorGatewayImpl implements ImportActorGateway {
    private final CurrentActorApi actors;
    @Override public long currentOwnerId() { return actors.userId(); }
    @Override public void runAsOwner(long ownerId, Runnable action) { actors.runAsSystem(ownerId, action); }
}
