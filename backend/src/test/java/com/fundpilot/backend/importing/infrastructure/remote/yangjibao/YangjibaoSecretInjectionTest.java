package com.fundpilot.backend.importing.infrastructure.remote.yangjibao;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class YangjibaoSecretInjectionTest {
    private ApplicationContextRunner runner(Map<String, Object> environment) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(JacksonAutoConfiguration.class))
                .withUserConfiguration(YangjibaoClient.class, YangjibaoSigner.class)
                .withInitializer(context -> {
                    context.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance());
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove("systemEnvironment");
                    sources.remove("systemProperties");
                    sources.addFirst(new SystemEnvironmentPropertySource("injectedEnvironment", environment));
                    try {
                        new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                                .forEach(sources::addLast);
                    } catch (java.io.IOException exception) {
                        throw new java.io.UncheckedIOException(exception);
                    }
                });
    }

    @Test
    void missingSecretPreventsStartup() {
        runner(Map.of()).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasStackTraceContaining("fundpilot.yangjibao.secret");
        });
    }

    @Test
    void emptyOrWhitespaceSecretPreventsStartup() {
        for (String value : new String[]{"", "   "}) {
            runner(Map.of("YANGJIBAO_SECRET", value)).run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseMessage(
                        "Missing required configuration: fundpilot.yangjibao.secret");
            });
        }
    }

    @Test
    void injectedAndReplacedSecretSignsAnonymousAndAuthenticatedRequestsAfterRestart() throws Exception {
        var requests = new CopyOnWriteArrayList<Boolean>();
        var signatures = new CopyOnWriteArrayList<String>();
        var expectedSecret = new java.util.concurrent.atomic.AtomicReference<String>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            String token = exchange.getRequestHeaders().getFirst("Authorization");
            String timestamp = exchange.getRequestHeaders().getFirst("Request-Time");
            String signature = exchange.getRequestHeaders().getFirst("Request-Sign");
            try {
                String input = path + (token == null ? "" : token) + timestamp + expectedSecret.get();
                String expected = HexFormat.of().formatHex(MessageDigest.getInstance("MD5")
                        .digest(input.getBytes(StandardCharsets.UTF_8)));
                requests.add(expected.equals(signature));
                signatures.add(signature);
                byte[] response = (path.equals("/qr_code")
                        ? "{\"code\":200,\"data\":{\"id\":\"fixture\",\"url\":\"fixture\"}}"
                        : "{\"code\":200,\"data\":[]}").getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
            } catch (Exception exception) {
                throw new java.io.IOException(exception);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            for (String secret : new String[]{"test-only-first-signing-secret", "test-only-replaced-signing-secret"}) {
                expectedSecret.set(secret);
                runner(Map.of("YANGJIBAO_SECRET", secret))
                        .withPropertyValues("fundpilot.yangjibao.base-url=http://127.0.0.1:" + server.getAddress().getPort())
                        .run(context -> {
                            assertThat(context).hasNotFailed();
                            var client = context.getBean(YangjibaoClient.class);
                            assertThat(client.createQrCode().id()).isEqualTo("fixture");
                            assertThat(client.holdings("test-only-token", "fixture-account")).isEmpty();
                        });
            }
            assertThat(requests).containsExactly(true, true, true, true);
            assertThat(signatures.get(0)).isNotEqualTo(signatures.get(2));
            assertThat(signatures.get(1)).isNotEqualTo(signatures.get(3));
        } finally {
            server.stop(0);
        }
    }
}
