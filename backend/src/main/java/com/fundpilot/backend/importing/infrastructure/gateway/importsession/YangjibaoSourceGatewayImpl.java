package com.fundpilot.backend.importing.infrastructure.gateway.importsession;

import com.fundpilot.backend.importing.application.gateway.importsession.YangjibaoSourceGateway;
import com.fundpilot.backend.importing.infrastructure.remote.yangjibao.YangjibaoClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class YangjibaoSourceGatewayImpl implements YangjibaoSourceGateway {
    private final YangjibaoClient client;

    @Override public QrCode createQrCode() { return call(() -> { var v = client.createQrCode(); return new QrCode(v.id(), v.url()); }); }
    @Override public QrState qrState(String id) { return call(() -> { var v = client.qrState(id); return new QrState(v.state(), v.token()); }); }
    @Override public List<Account> accounts(String token) { return call(() -> client.accounts(token).stream()
            .map(v -> new Account(v.id(), v.title())).toList()); }
    @Override public List<Holding> holdings(String token, String accountId) { return call(() -> client.holdings(token, accountId)
            .stream().map(v -> new Holding(v.id(), v.code(), v.short_name(), v.hold_share(), v.hold_cost())).toList()); }

    private static <T> T call(java.util.function.Supplier<T> request) {
        try { return request.get(); }
        catch (RuntimeException exception) { throw new Failure("养基宝接口调用失败", exception); }
    }
}
