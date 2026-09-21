package com.fundpilot.backend.alerting.infrastructure.gateway.notificationdelivery;

import com.fundpilot.backend.alerting.application.gateway.notificationdelivery.AlertRecipientGateway;
import com.fundpilot.backend.identityaccess.adapter.api.usercontact.UserContactApi;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AlertRecipientGatewayImpl implements AlertRecipientGateway {

    private final UserContactApi contacts;

    @Override
    public Optional<String> emailOf(long ownerId) {
        return contacts.emailOf(ownerId);
    }
}
