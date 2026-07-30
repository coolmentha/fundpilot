package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import org.springframework.stereotype.Component;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** 同花顺大盘涨跌停分钟统计，先访问主页建立 Cookie 会话。 */
@Component
public class ThsIndexFlashClient {

    private static final URI HOME_URI = URI.create("https://q.10jqka.com.cn/");
    private static final URI INDEX_FLASH_URI = URI.create("https://q.10jqka.com.cn/api.php?t=indexflash");
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(1))
            .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER))
            .build();

    public String fetchIndexFlashRaw() {
        send(HOME_URI, "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        return send(INDEX_FLASH_URI, "*/*");
    }

    private String send(URI uri, String accept) {
        try {
            HttpResponse<String> response = httpClient.send(HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(3))
                    .header("Accept", accept)
                    .header("Accept-Language", "zh-CN,zh;q=0.9")
                    .header("Referer", HOME_URI.toString())
                    .header("User-Agent", USER_AGENT)
                    .GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("同花顺 indexflash HTTP " + response.statusCode());
            }
            return response.body();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("同花顺 indexflash 请求失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("同花顺 indexflash 请求中断", e);
        }
    }
}
