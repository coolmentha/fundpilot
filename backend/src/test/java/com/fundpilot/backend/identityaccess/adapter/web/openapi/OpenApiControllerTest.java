package com.fundpilot.backend.identityaccess.adapter.web.openapi;

import com.fundpilot.backend.identityaccess.adapter.web.opentoken.OpenApiTokenController;
import com.fundpilot.backend.identityaccess.application.command.opentoken.OpenApiTokenCommandHandler;
import com.fundpilot.backend.identityaccess.application.command.opentoken.OpenApiTokenCommandHandler.IssuedToken;
import com.fundpilot.backend.identityaccess.application.query.currentactor.CurrentActorQueryHandler;
import com.fundpilot.backend.identityaccess.application.query.opentoken.OpenApiTokenQueryHandler;
import com.fundpilot.backend.platform.adapter.web.openapi.OpenApiDiscoveryController;
import com.fundpilot.backend.platform.web.RequestActorAttributes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {OpenApiTokenController.class, OpenApiIdentityController.class,
        OpenApiDiscoveryController.class})
@Import({OpenApiTokenController.class, OpenApiIdentityController.class, OpenApiDiscoveryController.class,
        OpenApiControllerTest.TestConfig.class})
class OpenApiControllerTest {

    @SpringBootConfiguration
    static class TestConfig {
    }

    @Autowired MockMvc mockMvc;

    @MockitoBean OpenApiTokenCommandHandler commands;
    @MockitoBean OpenApiTokenQueryHandler queries;
    @MockitoBean CurrentActorQueryHandler actors;

    @Test
    void discoveryEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/api/open"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("v1"))
                .andExpect(jsonPath("$.data.basePath").value("/api/open/v1"));
    }

    @Test
    void tokenStatusNeverSerializesPlaintextOutsideIssueResponse() throws Exception {
        when(queries.findStatus(7L)).thenReturn(Optional.of(
                new OpenApiTokenQueryHandler.TokenStatus(true, Instant.parse("2026-10-25T00:00:00Z"), null)));

        mockMvc.perform(get("/api/open-api/token").requestAttr(RequestActorAttributes.USER_ID, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true))
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    void issueResponseReturnsPlaintextOnlyForThatRequest() throws Exception {
        when(commands.issueOrRotate(7L)).thenReturn(new IssuedToken(
                11L, "fp_once", Instant.parse("2026-10-25T00:00:00Z")));

        mockMvc.perform(post("/api/open-api/token").requestAttr(RequestActorAttributes.USER_ID, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").value("fp_once"));
    }
}
