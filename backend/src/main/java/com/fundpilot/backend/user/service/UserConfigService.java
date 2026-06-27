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
import java.util.List;

/**
 * 用户配置服务(issue #16):单用户场景,只有一行 UserConfig。
 * Controller 只做 HTTP 路由,逻辑下沉到本层。返回 {@link UserConfigView} DTO。
 */
@Service
@RequiredArgsConstructor
public class UserConfigService {

    private final UserConfigRepository userConfigRepository;

    /** 取唯一配置;未初始化抛 400。 */
    public UserConfigView get() {
        return UserConfigView.from(requireConfig());
    }

    /** 取总可投资金;未初始化抛 400(供其它服务校验资金相关约束复用,单一事实源)。 */
    public BigDecimal requireTotalInvestableCapital() {
        return requireConfig().getTotalInvestableCapital();
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

    /** 更新 totalInvestableCapital(无则新建)。 */
    @Transactional
    public UserConfigView update(BigDecimal totalInvestableCapital) {
        List<UserConfigEntity> all = userConfigRepository.findAll();
        UserConfigEntity config = all.isEmpty() ? new UserConfigEntity() : all.get(0);
        if (totalInvestableCapital != null) {
            config.setTotalInvestableCapital(totalInvestableCapital);
        }
        return UserConfigView.from(userConfigRepository.save(config));
    }
}
