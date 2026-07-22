package com.fundpilot.backend.integration.yangjibao;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.service.FundPositionService;
import com.fundpilot.backend.fund.service.FundService;
import com.fundpilot.backend.fund.service.FundTransactionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class YangjibaoImportServiceTest {
    @Mock YangjibaoClient client;
    @Mock FundRepository fundRepository;
    @Mock FundPositionService positionService;
    @Mock FundService fundService;
    @Mock FundTransactionService transactionService;
    YangjibaoImportService service;

    @BeforeEach
    void setUp() {
        service = new YangjibaoImportService(client, fundRepository, positionService, fundService, transactionService, Runnable::run);
        ReflectionTestUtils.setField(service, "ttl", Duration.ofMinutes(30));
    }

    @Test
    void importsSelectedNewFundFromServerPreviewSnapshot() {
        when(client.createQrCode()).thenReturn(new YangjibaoClient.QrCode("qr", "https://qr"));
        when(client.qrState("qr")).thenReturn(new YangjibaoClient.QrState("2", "token"));
        when(client.accounts("token")).thenReturn(List.of(new YangjibaoClient.Account("a", "支付宝")));
        when(client.holdings("token", "a")).thenReturn(List.of(new YangjibaoClient.Holding(
                "h", "017093", "示例基金", new BigDecimal("100.00"), new BigDecimal("1.23"))));
        when(fundRepository.findByFundCode("017093")).thenReturn(Optional.empty());

        String id = service.create().sessionId();
        service.state(id);
        var preview = service.preview(id);
        service.startImport(id, List.of(new YangjibaoImportController.Selection(preview.getFirst().itemId(), null)));
        var results = service.importStatus(id).results();

        assertThat(results.getFirst().status()).isEqualTo("CREATED");
        verify(fundService).create(any());
    }

    @Test
    void rejectsSelectingTwoAccountsForSameFundCode() {
        when(client.createQrCode()).thenReturn(new YangjibaoClient.QrCode("qr", "https://qr"));
        when(client.qrState("qr")).thenReturn(new YangjibaoClient.QrState("2", "token"));
        when(client.accounts("token")).thenReturn(List.of(
                new YangjibaoClient.Account("a", "A"), new YangjibaoClient.Account("b", "B")));
        when(client.holdings(eq("token"), anyString())).thenAnswer(invocation -> List.of(new YangjibaoClient.Holding(
                "h", "017093", "示例基金", BigDecimal.TEN, BigDecimal.ONE)));
        when(fundRepository.findByFundCode("017093")).thenReturn(Optional.empty());

        String id = service.create().sessionId();
        service.state(id);
        var preview = service.preview(id);

        assertThatThrownBy(() -> service.startImport(id, preview.stream()
                .map(item -> new YangjibaoImportController.Selection(item.itemId(), null)).toList()))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(fundService);
    }

    @Test
    void retriesOnlyFailedImports() {
        when(client.createQrCode()).thenReturn(new YangjibaoClient.QrCode("qr", "https://qr"));
        when(client.qrState("qr")).thenReturn(new YangjibaoClient.QrState("2", "token"));
        when(client.accounts("token")).thenReturn(List.of(new YangjibaoClient.Account("a", "支付宝")));
        when(client.holdings("token", "a")).thenReturn(List.of(new YangjibaoClient.Holding(
                "h", "017093", "示例基金", BigDecimal.TEN, BigDecimal.ONE)));
        when(fundRepository.findByFundCode("017093")).thenReturn(Optional.empty());
        when(fundService.create(any())).thenThrow(new RuntimeException("temporary failure")).thenReturn(null);

        String id = service.create().sessionId();
        service.state(id);
        var item = service.preview(id).getFirst();
        service.startImport(id, List.of(new YangjibaoImportController.Selection(item.itemId(), null)));

        assertThat(service.importStatus(id).failed()).isEqualTo(1);
        assertThat(service.retryFailed(id).failed()).isZero();
        verify(fundService, times(2)).create(any());
    }
}
