package com.fundpilot.backend.importing.adapter.web.importsession;

import com.fundpilot.backend.importing.application.command.importsession.YangjibaoImportCommandHandler;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportActorGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportSessionGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.YangjibaoSourceGateway;
import com.fundpilot.backend.importing.infrastructure.remote.yangjibao.YangjibaoClient;
import com.fundpilot.backend.importing.infrastructure.remote.yangjibao.YangjibaoSigner;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class YangjibaoImportErrorRedactionTest {
    private static final String SECRET = "test-only-http-chain-signing-secret";
    private static final String TOKEN = "test-only-http-chain-token";
    private static final String ACCOUNT_ID = "fixture-account";

    @Test
    void remoteFailureIsRedactedThroughClientGatewayHandlerControllerAndAdvice() throws Exception {
        var requests = new CopyOnWriteArrayList<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().toString();
            requests.add(path + "|" + exchange.getRequestHeaders().getFirst("Authorization"));
            boolean accounts = path.equals("/user_account");
            byte[] response = (accounts
                    ? "{\"code\":200,\"data\":{\"list\":[{\"id\":\"" + ACCOUNT_ID
                            + "\",\"title\":\"fixture\"}]}}"
                    : "remote rejected secret=" + SECRET + ", token=" + TOKEN + ", request=" + path)
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(accounts ? 200 : 401, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var client = new YangjibaoClient(new ObjectMapper(), new YangjibaoSigner(),
                    "http://127.0.0.1:" + server.getAddress().getPort(), SECRET, Duration.ofSeconds(2));
            var source = realSourceGateway(client);
            var actors = mock(ImportActorGateway.class);
            var sessions = mock(ImportSessionGateway.class);
            when(actors.currentOwnerId()).thenReturn(7L);
            when(sessions.findOwned("fixture-session", 7L)).thenReturn(Optional.of(new ImportSessionGateway.Snapshot(
                    "fixture-session", 7L, "fixture-qr", "fixture-url", TOKEN, "CONNECTED",
                    Instant.now(), Instant.now(), Instant.now().plusSeconds(60),
                    List.of(), List.of(), List.of(), null)));
            var commands = new YangjibaoImportCommandHandler(source, mock(ImportedHoldingGateway.class), actors,
                    Runnable::run, sessions);
            var mockMvc = standaloneSetup(new YangjibaoImportController(commands))
                    .setControllerAdvice(new YangjibaoImportExceptionHandler())
                    .build();

            String response = mockMvc.perform(get("/api/imports/yangjibao/sessions/fixture-session/preview"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.code").value("YANGJIBAO_API_FAILED"))
                    .andExpect(jsonPath("$.message").value("养基宝接口调用失败"))
                    .andReturn().getResponse().getContentAsString();

            assertThat(requests).containsExactly(
                    "/user_account|" + TOKEN,
                    "/fund_hold?account_id=" + ACCOUNT_ID + "|" + TOKEN);
            assertThat(response).doesNotContain(SECRET, TOKEN, ACCOUNT_ID, "fund_hold", "HTTP 401");
        } finally {
            server.stop(0);
        }
    }

    private YangjibaoSourceGateway realSourceGateway(YangjibaoClient client) throws Exception {
        var type = Class.forName(
                "com.fundpilot.backend.importing.infrastructure.gateway.importsession.YangjibaoSourceGatewayImpl");
        var constructor = type.getDeclaredConstructor(YangjibaoClient.class);
        constructor.setAccessible(true);
        return (YangjibaoSourceGateway) constructor.newInstance(client);
    }
}
