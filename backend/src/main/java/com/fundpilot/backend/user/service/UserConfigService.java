package com.fundpilot.backend.user.service;

import com.fundpilot.backend.user.controller.UserConfigView;
import com.fundpilot.backend.user.entity.UserConfigEntity;
import com.fundpilot.backend.user.event.WatchedIndicesChangedEvent;
import com.fundpilot.backend.user.repository.UserConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Arrays;
import java.util.List;

/**
 * 用户配置服务:行情工作台转向后,只管理关注指数列表(watchedIndices)。
 * Controller 只做 HTTP 路由,逻辑下沉到本层。返回 {@link UserConfigView} DTO。
 *
 * <p>未初始化时不抛错——行情展示不应被配置缺失阻塞,get() 返默认指数列表。
 */
@Service
@RequiredArgsConstructor
public class UserConfigService {

    /** 默认关注指数(用户未配置时兜底):上证指数 + 沪深300 + 创业板指。 */
    public static final String DEFAULT_WATCHED_INDICES = "1.000001,1.000300,0.399006";

    private final UserConfigRepository userConfigRepository;
    private final ApplicationEventPublisher eventPublisher;

    /** 取配置视图;未初始化返默认(空 watchedIndices,由 getWatchedIndices 兜底默认指数)。 */
    public UserConfigView get() {
        List<UserConfigEntity> all = userConfigRepository.findAll();
        return all.isEmpty() ? UserConfigView.from(null) : UserConfigView.from(all.get(0));
    }

    /**
     * 取用户关注的大盘指数 secid 列表(供行情缓存层按需拉取)。
     * <p>未初始化或字段空时返默认列表(不抛错——行情展示不应被配置缺失阻塞)。单一事实源。
     */
    public List<String> getWatchedIndices() {
        List<UserConfigEntity> all = userConfigRepository.findAll();
        if (all.isEmpty()) {
            return parseSecids(DEFAULT_WATCHED_INDICES);
        }
        String raw = all.get(0).getWatchedIndices();
        if (raw == null || raw.isBlank()) {
            return parseSecids(DEFAULT_WATCHED_INDICES);
        }
        return parseSecids(raw);
    }

    /**
     * 更新关注指数列表(无则新建)。事务提交后发 {@link WatchedIndicesChangedEvent},
     * 让行情缓存即时重读新列表(不必等 30s cron;非交易时段 cron 不跑,全靠事件驱动)。
     * 发在 commit 后而非事务内,避免监听者读到未提交数据。
     */
    @Transactional
    public UserConfigView update(List<String> watchedIndices) {
        List<UserConfigEntity> all = userConfigRepository.findAll();
        UserConfigEntity config = all.isEmpty() ? new UserConfigEntity() : all.get(0);
        if (watchedIndices != null) {
            config.setWatchedIndices(watchedIndices.isEmpty() ? null : String.join(",", watchedIndices));
        }
        UserConfigView view = UserConfigView.from(userConfigRepository.save(config));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { eventPublisher.publishEvent(new WatchedIndicesChangedEvent()); }
            });
        } else {
            eventPublisher.publishEvent(new WatchedIndicesChangedEvent());
        }
        return view;
    }

    private static List<String> parseSecids(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
