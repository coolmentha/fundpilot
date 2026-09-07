package com.fundpilot.backend.importing.application.command.importsession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundpilot.backend.importing.application.gateway.importsession.ImportActorGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportSessionGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.ImportedHoldingGateway;
import com.fundpilot.backend.importing.application.gateway.importsession.YangjibaoSourceGateway;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(OutputCaptureExtension.class)
class FailedItemClassificationTest {
    private YangjibaoSourceGateway source;
    private ImportedHoldingGateway holdings;
    private ImportActorGateway actors;
    private YangjibaoImportCommandHandler handler;

    @BeforeEach
    void setUp() {
        source = mock(YangjibaoSourceGateway.class);
        holdings = mock(ImportedHoldingGateway.class);
        actors = mock(ImportActorGateway.class);
        lenient().when(actors.currentOwnerId()).thenReturn(1L);
        lenient().doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(actors).runAsOwner(eq(1L), any(Runnable.class));
        handler = handler(new InMemoryImportSessionGateway());
    }

    @ParameterizedTest
    @MethodSource("failures")
    void failedItemsExposeStableSafeClassification(RuntimeException failure, String expectedCode,
                                                     String expectedMessage) {
        connectedHolding();
        when(holdings.find(1L, "017093")).thenReturn(Optional.empty());
        when(holdings.importItem(any())).thenThrow(failure);

        var result = importOne(handler);

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.failureCode()).isEqualTo(expectedCode);
        assertThat(result.message()).isEqualTo(expectedMessage);
        assertThat(result.correlationId()).isNotBlank();
        assertThat(result.message()).doesNotContain("secret", "jdbc", "token");
    }

    @org.junit.jupiter.api.Test
    void successfulItemsKeepFailureFieldsNull() {
        connectedHolding();
        when(holdings.find(1L, "017093")).thenReturn(Optional.empty());
        when(holdings.importItem(any())).thenReturn(new ImportedHoldingGateway.ItemResult(
                ImportedHoldingGateway.ItemStatus.CREATED, "已新增基金", 9L));

        var result = importOne(handler);

        assertThat(result.failureCode()).isNull();
        assertThat(result.correlationId()).isNull();
    }

    @org.junit.jupiter.api.Test
    void anotherOwnerCannotRetryFailedItems() {
        connectedHolding();
        when(holdings.find(1L, "017093")).thenReturn(Optional.empty());
        when(holdings.importItem(any())).thenThrow(new RuntimeException("jdbc secret"));
        String id = startOne(handler);
        when(actors.currentOwnerId()).thenReturn(2L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> handler.retryFailed(id))
                .isInstanceOf(YangjibaoImportFailure.class);
    }

    @org.junit.jupiter.api.Test
    void failureLogCanBeCorrelatedWithoutSensitiveExceptionText(CapturedOutput output) {
        connectedHolding();
        when(holdings.find(1L, "017093")).thenReturn(Optional.empty());
        when(holdings.importItem(any())).thenThrow(new RuntimeException("jdbc password=secret"));

        var result = importOne(handler);

        assertThat(output).contains(result.correlationId(), "IMPORT_INTERNAL_FAILED", "java.lang.RuntimeException")
                .doesNotContain("password", "secret");
    }

    private YangjibaoImportCommandHandler handler(ImportSessionGateway sessions) {
        var value = new YangjibaoImportCommandHandler(source, holdings, actors, Runnable::run, sessions);
        ReflectionTestUtils.setField(value, "ttl", Duration.ofMinutes(30));
        return value;
    }

    private YangjibaoImportCommandHandler.ImportResult importOne(YangjibaoImportCommandHandler commands) {
        String id = startOne(commands);
        return commands.importStatus(id).results().getFirst();
    }

    private String startOne(YangjibaoImportCommandHandler commands) {
        String id = commands.create().sessionId();
        commands.state(id);
        var item = commands.preview(id).getFirst();
        commands.startImport(id, List.of(new YangjibaoImportCommandHandler.Selection(item.itemId(), null)));
        return id;
    }

    private void connectedHolding() {
        when(source.createQrCode()).thenReturn(new YangjibaoSourceGateway.QrCode("qr", "https://qr"));
        when(source.qrState("qr")).thenReturn(new YangjibaoSourceGateway.QrState("2", "token"));
        when(source.accounts("token")).thenReturn(List.of(new YangjibaoSourceGateway.Account("a", "支付宝")));
        when(source.holdings("token", "a")).thenReturn(List.of(new YangjibaoSourceGateway.Holding(
                "h", "017093", "示例基金", new BigDecimal("100.00"), new BigDecimal("1.23"))));
    }

    private static Stream<Arguments> failures() {
        return Stream.of(
                Arguments.of(new IllegalArgumentException("secret validation"),
                        "IMPORT_VALIDATION_FAILED", "导入数据校验失败，请检查后重试"),
                Arguments.of(new YangjibaoImportFailure(
                                YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_CONFLICT, "secret conflict"),
                        "IMPORT_CONFLICT", "导入冲突，请选择有效的处理方式"),
                Arguments.of(new YangjibaoImportFailure(
                                YangjibaoImportFailure.Code.YANGJIBAO_IMPORT_DEPENDENCY_FAILED, "token secret"),
                        "IMPORT_DEPENDENCY_FAILED", "暂时无法完成导入，请稍后重试"),
                Arguments.of(new RuntimeException("jdbc password=secret"),
                        "IMPORT_INTERNAL_FAILED", "导入失败，请联系支持并提供关联标识")
        );
    }
}
