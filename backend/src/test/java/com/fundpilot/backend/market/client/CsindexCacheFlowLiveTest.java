package com.fundpilot.backend.marketdata.infrastructure.remote.marketfeed;

import com.fundpilot.backend.marketdata.adapter.api.indexkline.IndexKlineApi;
import com.fundpilot.backend.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 端到端 live 验证:真实 Spring 装配的 {@link MarketDataSource} 降级链 bean(链首 csindex)
 * → 拉取 930713.CSI 日 K → 落 {@code index_kline} 缓存 → 读回。
 * <p>这是 v0.4.6 的关键验收:证明借鉴 akshare 的 csindex.com.cn 替代被封锁的 push2his 后,
 * {@code MarketDataFetchService} refresh 流程能真正填充缓存,供 {@code KlineService} 渲染日/周/月 K。
 * <p>{@code @Tag("live")} 默认排除,通过 {@code mvn test -Plive} 触发;{@code @Transactional} 回滚缓存写入。
 */
@Tag("live")
@Transactional
class CsindexCacheFlowLiveTest extends AbstractIntegrationTest {

    @Autowired
    MarketDataSource marketDataSource;

    @Autowired
    IndexKlineApi indexKlineApi;

    @Test
    void 链首csindex_拉取930713日K_成功返回OHLCV() {
        // 真实降级链 bean:csindex(链首)→ eastmoney(兜底)。930713.CSI 应由 csindex 命中。
        IndexKline kline = marketDataSource.fetchIndexKline("2.930713", "6");

        assertThat(kline.bars()).isNotEmpty();
        IndexKline.Bar first = kline.bars().getFirst();
        assertThat(first.date()).isNotNull();
        assertThat(first.open()).isNotNull();
        assertThat(first.high()).isNotNull();
        assertThat(first.low()).isNotNull();
        assertThat(first.close()).isPositive();
        assertThat(first.volume()).isGreaterThanOrEqualTo(0L);
        // 5 年日 K 应有数百根
        assertThat(kline.bars().size()).isGreaterThan(200);
    }

    @Test
    void 链首csindex_周K经聚合_根数少于日K() {
        IndexKline daily = marketDataSource.fetchIndexKlineWithPeriod("2.930713", "101", "400");
        IndexKline weekly = marketDataSource.fetchIndexKlineWithPeriod("2.930713", "102", "400");

        assertThat(daily.bars()).isNotEmpty();
        assertThat(weekly.bars()).isNotEmpty();
        assertThat(weekly.bars().size()).isLessThan(daily.bars().size());
    }

    @Test
    void 落index_kline缓存_读回roundTrip一致() {
        // 模拟 MarketDataFetchService.upsertIndexKline:把 csindex 拉到的日 K 落库,再读回
        IndexKline kline = marketDataSource.fetchIndexKline("2.930713", "6");
        String indexCode = "930713.CSI";

        List<IndexKlineApi.Bar> toInsert = kline.bars().stream().limit(5)
                .map(b -> new IndexKlineApi.Bar(b.date(), b.open(), b.high(), b.low(), b.close(), b.volume()))
                .toList();
        indexKlineApi.upsert(indexCode, toInsert);

        List<IndexKlineApi.Bar> readBack = indexKlineApi.findAll(indexCode);
        assertThat(readBack).hasSize(5);
        assertThat(readBack.getFirst().open()).isEqualByComparingTo(kline.bars().getFirst().open());
        assertThat(readBack.getLast().close()).isEqualByComparingTo(kline.bars().get(4).close());
    }
}
