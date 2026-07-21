package com.fundpilot.backend.integration.yangjibao;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class YangjibaoClientTest {
    @Test
    void createsQrCodeWithAnonymousSignatureHeaders() throws Exception {
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200)
                    .addHeader("Content-Type", "application/json")
                    .setBody("{\"code\":200,\"data\":{\"id\":\"qr-1\",\"url\":\"https://qr.example\"}}"));
            ObjectMapper mapper = new ObjectMapper();
            RestClient.Builder builder = RestClient.builder();
            YangjibaoClient client = new YangjibaoClient(builder, mapper,
                    new YangjibaoSigner(), server.url("/").toString(), "secret", Duration.ofSeconds(2));

            assertThat(client.createQrCode()).isEqualTo(new YangjibaoClient.QrCode("qr-1", "https://qr.example"));
            var request = server.takeRequest();
            assertThat(request.getPath()).isEqualTo("/qr_code");
            assertThat(request.getHeaders().get("Request-Time")).isNotBlank();
            assertThat(request.getHeaders().get("Request-Sign")).hasSize(32);
        }
    }
}
