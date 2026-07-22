package com.fundpilot.backend.integration.yangjibao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Component
public class YangjibaoClient {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final YangjibaoSigner signer;
    private final String secret;
    private final Clock clock = Clock.systemUTC();

    public YangjibaoClient(ObjectMapper mapper, YangjibaoSigner signer,
                           @Value("${fundpilot.yangjibao.base-url}") String baseUrl,
                           @Value("${fundpilot.yangjibao.secret}") String secret,
                           @Value("${fundpilot.yangjibao.timeout:PT15S}") Duration timeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeout);
        requestFactory.setReadTimeout(timeout);
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
        this.mapper = mapper;
        this.signer = signer;
        this.secret = secret;
    }

    public QrCode createQrCode() { return read("/qr_code", "", true, QrCode.class); }
    public QrState qrState(String id) { return read("/qr_code_state/" + id, "", true, QrState.class); }

    public List<Account> accounts(String token) {
        JsonNode data = readNode("/user_account", token, false);
        return convertList(data.path("list"), Account.class);
    }

    public List<Holding> holdings(String token, String accountId) {
        String path = "/fund_hold?account_id=" + accountId;
        return convertList(readNode(path, token, false), Holding.class);
    }

    private <T> T read(String path, String token, boolean anonymous, Class<T> type) {
        return mapper.convertValue(readNode(path, token, anonymous), type);
    }

    private JsonNode readNode(String path, String token, boolean anonymous) {
        long ts = clock.instant().getEpochSecond();
        String sign = anonymous ? signer.anonymous(path, ts, secret) : signer.authenticated(path, token, ts, secret);
        try {
            String body = client.get().uri(path).headers(headers -> {
                headers.set("Authorization", token == null ? "" : token);
                headers.set("Request-Time", Long.toString(ts));
                headers.set("Request-Sign", sign);
            }).retrieve().body(String.class);
            JsonNode root = body == null ? null : mapper.readTree(body);
            if (root == null || root.path("code").asInt() != 200) {
                throw new BusinessException(ErrorCode.YANGJIBAO_API_FAILED, "养基宝接口返回失败");
            }
            return root.path("data");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.YANGJIBAO_API_FAILED,
                    "养基宝接口调用失败: " + e.getClass().getSimpleName());
        }
    }

    private <T> List<T> convertList(JsonNode node, Class<T> type) {
        List<T> result = new ArrayList<>();
        node.forEach(item -> result.add(mapper.convertValue(item, type)));
        return result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QrCode(String id, String url) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QrState(String state, String token) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Account(String id, String title) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Holding(String id, String code, String short_name, BigDecimal hold_share, BigDecimal hold_cost) {}
}
