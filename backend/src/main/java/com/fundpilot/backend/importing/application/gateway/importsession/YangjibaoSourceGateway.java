package com.fundpilot.backend.importing.application.gateway.importsession;

import java.math.BigDecimal;
import java.util.List;

public interface YangjibaoSourceGateway {
    QrCode createQrCode();
    QrState qrState(String id);
    List<Account> accounts(String token);
    List<Holding> holdings(String token, String accountId);

    record QrCode(String id, String url) {}
    record QrState(String state, String token) {}
    record Account(String id, String title) {}
    record Holding(String id, String code, String name, BigDecimal shares, BigDecimal costPerShare) {}
    final class Failure extends RuntimeException {
        public Failure(String message, Throwable cause) { super(message, cause); }
    }
}
