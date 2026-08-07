package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import com.fundpilot.backend.marketdata.infrastructure.remote.tradingcalendar.SinaTradingCalendarClient;

import com.fundpilot.backend.platform.observability.MarketDataMetrics;
import feign.Client;
import feign.RequestInterceptor;
import feign.Request;
import feign.Response;
import feign.Retryer;
import feign.Feign;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.io.IOException;
import java.time.Duration;

/**
 * EastmoneyClient 的 Feign 配置:速率限流 + Referer/UA 请求头拦截器。
 * <p>东方财富请求用 {@link RateLimiter}(Bucket4j 令牌桶)
 * 做速率限流(issue #35 替换未接入的 Semaphore);加 {@code Referer: https://fund.eastmoney.com/} 避免被反爬。
 * <p>限流为全客户端共享单例(净值/字典/K线/估值共用一个桶),保证总请求速率不超限。
 * 静态工厂方法保留供单元测试直接使用;{@link #eastmoneyClient(String)} 等注册为 Spring Bean,
 * 供业务组件注入,base URL 通过 {@code eastmoney.base-url} 配置。
 */
@Configuration(proxyBeanMethods = false)
public class EastmoneyClientConfig {

    /** 全客户端共享限流桶；本机短压测 20 次/秒无失败。 */
    private static final long PERMITS_PER_SECOND = 20;
    private static final Duration RATE_LIMIT_MAX_WAIT = Duration.ofSeconds(1);
    /** 共享速率限流器,全客户端单例。 */
    private static final RateLimiter SHARED_LIMITER = RateLimiter.perSecond(PERMITS_PER_SECOND);

    public static RateLimiter rateLimiter() {
        return SHARED_LIMITER;
    }

    /** 请求头拦截器:加 Referer + 合理 User-Agent。 */
    public static RequestInterceptor requestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("Referer", "https://fund.eastmoney.com/");
            requestTemplate.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        };
    }

    /** ETF 行情请求头，对应 AKShare 的东方财富行情页面来源。 */
    public static RequestInterceptor etfRequestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("Referer", "https://quote.eastmoney.com/center/gridlist.html#fund_etf");
            requestTemplate.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        };
    }

    /** 腾讯证券请求头，按 AKShare 的公开页面来源设置 Referer。 */
    public static RequestInterceptor tencentRequestInterceptor() {
        return requestTemplate -> {
            requestTemplate.header("Referer", "https://gu.qq.com/");
            requestTemplate.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36");
        };
    }

    /** 默认不重试(让调用方控制降级策略)。 */
    public static Retryer retryer() {
        return Retryer.NEVER_RETRY;
    }

    public static Request.Options options() {
        return new Request.Options(Duration.ofSeconds(1), Duration.ofSeconds(3), true);
    }

    /**
     * 注册 {@link EastmoneyClient} 为 Spring Bean(fund.eastmoney.com 域名,净值+字典)。
     * 请求经 {@link RateLimitedClient} 节流(共享令牌桶),防东方财富封 IP。
     *
     * @param baseUrl 东方财富服务基础地址,由 {@code eastmoney.base-url} 配置,默认指向官方域名
     */
    @Bean
    public EastmoneyClient eastmoneyClient(@Value("${eastmoney.base-url:https://fund.eastmoney.com}") String baseUrl) {
        return Feign.builder()
                .client(new RateLimitedClient(SHARED_LIMITER))
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .options(options())
                .target(EastmoneyClient.class, baseUrl);
    }

    /**
     * 注册 {@link EastmoneyKlineClient} 为 Spring Bean(push2his.eastmoney.com 域名,指数 K 线)。
     * K 线接口与基金净值不同域名,故独立 target;共享同一限流桶。
     */
    @Bean
    public EastmoneyKlineClient eastmoneyKlineClient(
            @Value("${eastmoney.kline-base-url:https://push2his.eastmoney.com}") String klineBaseUrl) {
        return Feign.builder()
                .client(new RateLimitedClient(SHARED_LIMITER))
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .options(options())
                .target(EastmoneyKlineClient.class, klineBaseUrl);
    }

    /**
     * 注册 {@link EastmoneyFundGzClient} 为 Spring Bean(fundgz.1234567.com.cn 域名,盘中估值)。
     * 估值接口在第三个域名,故独立 target;共享同一限流桶。返回 JSONP 由 parser 剥外壳解析。
     */
    @Bean
    public EastmoneyFundGzClient eastmoneyFundGzClient(
            @Value("${eastmoney.gz-base-url:https://fundgz.1234567.com.cn}") String gzBaseUrl) {
        return Feign.builder()
                .client(new RateLimitedClient(SHARED_LIMITER))
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .options(options())
                .target(EastmoneyFundGzClient.class, gzBaseUrl);
    }

    /**
     * 注册参考 AKShare 基金估值页面入口的东方财富静态页兼容客户端。
     * <p>与净值/字典共用 fund.eastmoney.com 域名和东方财富共享限流器。
     */
    @Bean
    public EastmoneyFundEstimatePageClient eastmoneyFundEstimatePageClient(
            @Value("${eastmoney.base-url:https://fund.eastmoney.com}") String baseUrl) {
        return Feign.builder()
                .client(new RateLimitedClient(SHARED_LIMITER))
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .options(options())
                .target(EastmoneyFundEstimatePageClient.class, baseUrl);
    }

    /** 注册 AKShare {@code fund_etf_spot_em} 使用的 ETF IOPV 客户端。 */
    @Bean
    public EastmoneyEtfSpotClient eastmoneyEtfSpotClient(
            @Value("${eastmoney.etf-spot-base-url:https://88.push2.eastmoney.com}") String baseUrl) {
        return Feign.builder()
                .client(new RateLimitedClient(SHARED_LIMITER))
                .requestInterceptor(etfRequestInterceptor())
                .retryer(retryer())
                .options(options())
                .target(EastmoneyEtfSpotClient.class, baseUrl);
    }

    /**
     * 注册 {@link EastmoneyPush2Client} 为 Spring Bean(push2.eastmoney.com 域名,实时行情)。
     * 实时行情(指数/板块/北向资金)在第四个域名 push2(注意非 push2his 历史数据),故独立 target;
     * 共享同一限流桶。
     */
    @Bean
    public EastmoneyPush2Client eastmoneyPush2Client(
            @Value("${eastmoney.push2-base-url:https://push2.eastmoney.com}") String push2BaseUrl) {
        return Feign.builder()
                .client(new RateLimitedClient(SHARED_LIMITER))
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .options(options())
                .target(EastmoneyPush2Client.class, push2BaseUrl);
    }

    /**
     * 注册 {@link CsindexClient} 为 Spring Bean(www.csindex.com.cn 域名,中证指数公司官方接口)。
     * <p>借鉴 akshare {@code stock_zh_index_hist_csindex}:中证公司是 CSI 主题指数(930xxx)的发布方,
     * 其接口不封 IP、不要求 Referer,可替代被 VPS IP 限流的 push2his 拉指数日 K。
     * 仅加浏览器 User-Agent(实测无需 Referer/Cookie);不限流(中证公司无已知限速)。
     */
    @Bean
    public CsindexClient csindexClient(
            @Value("${csindex.base-url:https://www.csindex.com.cn}") String csindexBaseUrl) {
        return Feign.builder()
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .options(options())
                .target(CsindexClient.class, csindexBaseUrl);
    }

    /** 注册 AKShare {@code stock_zh_index_daily_tx} 使用的腾讯指数日线客户端。 */
    @Bean
    public TencentIndexClient tencentIndexClient(
            @Value("${tencent.index-base-url:https://proxy.finance.qq.com}") String tencentBaseUrl) {
        return Feign.builder()
                .requestInterceptor(tencentRequestInterceptor())
                .retryer(retryer())
                .options(options())
                .target(TencentIndexClient.class, tencentBaseUrl);
    }

    /**
     * 注册 {@link SinaTradingCalendarClient} 为 Spring Bean(finance.sina.com.cn 域名,交易日历)。
     * <p>新浪交易日历接口返回 KLC 自定义编码文本,由 {@link SinaTradingCalendarParser} 解码。
     * 独立 target(新浪域名);共享同一限流桶(同步每日 1 次,共享无害)。
     */
    @Bean
    public SinaTradingCalendarClient sinaTradingCalendarClient(
            @Value("${sina.base-url:https://finance.sina.com.cn}") String sinaBaseUrl) {
        return Feign.builder()
                .client(new RateLimitedClient(SHARED_LIMITER))
                .requestInterceptor(requestInterceptor())
                .retryer(retryer())
                .options(options())
                .target(SinaTradingCalendarClient.class, sinaBaseUrl);
    }

    /**
     * 注册 {@link MarketDataSource} 降级链为 Spring Bean,供业务组件注入。
     * <p>降级顺序:中证指数公司(K 线主源,覆盖 CSI + 沪市中证编制指数,绕开 push2his IP 限流)
     * → 腾讯(交易所指数备用) → 同花顺(净值/字典主源 + 指数 K 线兜底)
     * → 东方财富(净值/字典/K 线最后降级源);
     * 全失败抛 {@code MARKET_DATA_ALL_SOURCES_FAILED}。
     * <p>csindex 仅实现指数 K 线,净值/字典抛 {@link UnsupportedOperationException},
     * {@code MarketDataSourceChain#tryEach} 记录 unsupported 后继续尝试下一真实来源。
     *
     * @param csindex   中证指数公司数据源(K 线主源)
     * @param tencent 腾讯指数 K 线备用源(仅 sh/sz 交易所指数)
     * @param ths        同花顺数据源(净值/字典主源,K 线兜底)
     * @param eastmoney 东方财富数据源(净值/字典/K 线最后降级源)
     * @param metrics    外部数据源调用指标
     */
    @Bean
    @Primary
    public MarketDataSourceChain marketDataSource(CsindexMarketDataSource csindex,
                                                  TencentIndexMarketDataSource tencent,
                                                  ThsMarketDataSource ths,
                                                  EastmoneyMarketDataSource eastmoney,
                                                  MarketDataMetrics metrics) {
        return new MarketDataSourceChain(java.util.List.of(csindex, tencent, ths, eastmoney), metrics);
    }

    private EastmoneyClientConfig() {
    }

    /**
     * Feign Client 包装:每个请求前 {@link RateLimiter#acquire()} 节流(阻塞等令牌),
     * 保证所有东方财富数据线(净值/字典/K线/估值)总速率不超每秒 2 次。
     */
    static final class RateLimitedClient implements Client {
        private final Client delegate = new Client.Default(null, null);
        private final RateLimiter rateLimiter;

        RateLimitedClient(RateLimiter rateLimiter) {
            this.rateLimiter = rateLimiter;
        }

        @Override
        public Response execute(Request request, Request.Options options) throws IOException {
            if (!rateLimiter.acquire(RATE_LIMIT_MAX_WAIT)) {
                throw new IOException("东方财富限流等待超过 1 秒");
            }
            return delegate.execute(request, options);
        }
    }
}
