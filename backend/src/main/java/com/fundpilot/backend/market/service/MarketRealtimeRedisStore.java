package com.fundpilot.backend.market.service;

import com.fundpilot.backend.market.client.FundEstimateSnapshot;
import com.fundpilot.backend.market.client.IndexRealtimeSnapshot;
import com.fundpilot.backend.market.client.MarketBreadthSnapshot;
import com.fundpilot.backend.market.client.MoneyFlowSnapshot;
import com.fundpilot.backend.market.client.SectorSnapshot;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MarketRealtimeRedisStore {

    private static final Logger log = LoggerFactory.getLogger(MarketRealtimeRedisStore.class);
    private static final String KEY = "fundpilot:market-realtime:v1";
    private static final JsonMapper MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final StringRedisTemplate redisTemplate;

    public Optional<Snapshot> load() {
        try {
            String json = redisTemplate.opsForValue().get(KEY);
            return json == null ? Optional.empty() : Optional.of(MAPPER.readValue(json, Snapshot.class));
        } catch (Exception e) {
            log.warn("Redis 行情缓存读取失败,使用进程内空缓存: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public void save(Snapshot snapshot) {
        try {
            redisTemplate.opsForValue().set(KEY, MAPPER.writeValueAsString(snapshot));
        } catch (Exception e) {
            log.warn("Redis 行情缓存写入失败,本进程继续使用内存缓存: {}", e.getMessage());
        }
    }

    public record Snapshot(
            List<IndexRealtimeSnapshot> indices,
            MarketBreadthSnapshot breadth,
            List<SectorSnapshot> sectors,
            MoneyFlowSnapshot moneyFlow,
            Map<String, FundEstimateSnapshot> estimates,
            Map<String, EstimateStatus> estimateStatuses) {
    }
}
