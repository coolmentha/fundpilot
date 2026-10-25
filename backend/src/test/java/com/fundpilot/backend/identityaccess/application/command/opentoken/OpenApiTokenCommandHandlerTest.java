package com.fundpilot.backend.identityaccess.application.command.opentoken;

import com.fundpilot.backend.identityaccess.application.gateway.opentoken.OpenApiTokenGateway;
import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiToken;
import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiTokenHash;
import com.fundpilot.backend.identityaccess.domain.opentoken.OpenApiTokenRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenApiTokenCommandHandlerTest {

    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");

    private final OpenApiTokenRepository tokens = mock(OpenApiTokenRepository.class);
    private final OpenApiTokenGateway text = mock(OpenApiTokenGateway.class);
    private final OpenApiTokenCommandHandler handler = new OpenApiTokenCommandHandler(tokens, text,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void firstIssueStoresOnlyHashAndReturnsPlaintextOnce() {
        when(text.generate()).thenReturn("fp_new_secret");
        when(tokens.save(any())).thenAnswer(invocation -> {
            OpenApiToken token = invocation.getArgument(0);
            return OpenApiToken.rehydrate(11L, 0L, token.ownerId(), token.tokenHash(),
                    token.rotatedAt(), token.lastUsedAt());
        });

        var issued = handler.issueOrRotate(7L);

        assertThat(issued.token()).isEqualTo("fp_new_secret");
        assertThat(issued.id()).isEqualTo(11L);
        verify(tokens).save(org.mockito.ArgumentMatchers.argThat(token ->
                token.tokenHash().equals(OpenApiTokenHash.of("fp_new_secret"))));
    }

    @Test
    void refreshReplacesExistingTokenHash() {
        OpenApiToken existing = OpenApiToken.rehydrate(11L, 0L, 7L,
                OpenApiTokenHash.of("fp_old_secret"), NOW.minusSeconds(60), null);
        when(tokens.findActiveByOwnerForUpdate(7L)).thenReturn(Optional.of(existing));
        when(text.generate()).thenReturn("fp_rotated_secret");
        when(tokens.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var issued = handler.issueOrRotate(7L);

        assertThat(issued.token()).isEqualTo("fp_rotated_secret");
        verify(tokens).save(org.mockito.ArgumentMatchers.argThat(token ->
                token.ownerId() == 7L && token.tokenHash().equals(OpenApiTokenHash.of("fp_rotated_secret"))));
    }

    @Test
    void revokeDelegatesToPersistentRevocation() {
        handler.revoke(7L);

        verify(tokens).revokeActive(7L);
    }
}
