package com.fundpilot.backend.user.service;

import com.fundpilot.backend.exception.BusinessException;
import com.fundpilot.backend.exception.ErrorCode;
import com.fundpilot.backend.user.controller.UserConfigView;
import com.fundpilot.backend.user.entity.UserConfigEntity;
import com.fundpilot.backend.user.repository.UserConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 用户配置服务(issue #16):单用户场景,只有一行 UserConfig。
 * Controller 只做 HTTP 路由,逻辑下沉到本层。返回 {@link UserConfigView} DTO。
 */
@Service
@RequiredArgsConstructor
public class UserConfigService {

    /** 默认关注指数(用户未配置时兜底):上证指数 + 沪深300 + 创业板指。 */
    public static final String DEFAULT_WATCHED_INDICES = "1.000001,1.000300,0.399006";

    private final UserConfigRepository userConfigRepository;

    /** 取唯一配置视图;未初始化抛 400。 */
    public UserConfigView get() {
        return UserConfigView.from(requireConfig());
    }

    /** 取总可投资金;未初始化抛 400(供其它服务校验资金相关约束复用,单一事实源)。 */
    public BigDecimal requireTotalInvestableCapital() {
        return requireConfig().getTotalInvestableCapital();
    }

    /**
     * 取用户关注的大盘指数 secid 列表(供行情缓存层按需拉取)。
     * <p>未初始化或字段空时返默认列表(不抛错——行情展示不应被资金未配置阻塞)。
     * 单一事实源,行情缓存层与控制器均复用此方法。
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

    /** 更新配置(无则新建);任一参数为 null 表示不修改该字段。 */
    @Transactional
    public UserConfigView update(BigDecimal totalInvestableCapital, List<String> watchedIndices) {
        List<UserConfigEntity> all = userConfigRepository.findAll();
        UserConfigEntity config = all.isEmpty() ? new UserConfigEntity() : all.get(0);
        if (totalInvestableCapital != null) {
            config.setTotalInvestableCapital(totalInvestableCapital);
        }
        if (watchedIndices != null) {
            config.setWatchedIndices(watchedIndices.isEmpty() ? null : String.join(",", watchedIndices));
        }
        return UserConfigView.from(userConfigRepository.save(config));
    }

    /** 取唯一配置实体;未初始化抛 400(get/requireTotalInvestableCapital 共用的单一事实源)。 */
    private UserConfigEntity requireConfig() {
        List<UserConfigEntity> all = userConfigRepository.findAll();
        if (all.isEmpty()) {
            throw new BusinessException(ErrorCode.USER_CONFIG_NOT_INITIALIZED,
                    "用户配置尚未初始化,请先调用 PUT /api/user-config 设置总可投资金");
        }
        return all.get(0);
    }

    private static List<String> parseSecids(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }
}
