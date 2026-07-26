package com.fundpilot.backend.user.service;
import com.fundpilot.backend.identityaccess.adapter.api.currentactor.CurrentActorApi;

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
 * 用户配置服务:管理可选月度定投预算。
 * Controller 只做 HTTP 路由,逻辑下沉到本层。返回 {@link UserConfigView} DTO。
 */
@Service
@RequiredArgsConstructor
public class UserConfigService {

    private static final BigDecimal MAX_MONTHLY_DCA_BUDGET = new BigDecimal("99999999999.99999999");

    private final UserConfigRepository userConfigRepository;
    private final CurrentActorApi currentActorApi;

    /** 取月度预算配置；未初始化时返回空配置。 */
    public UserConfigView get() {
        List<UserConfigEntity> all = configs();
        return all.isEmpty() ? UserConfigView.from(null) : UserConfigView.from(all.get(0));
    }

    /** 更新可选月度定投预算；无配置时创建当前用户的配置行。 */
    @Transactional
    public UserConfigView update(BigDecimal monthlyDcaBudget) {
        validateMonthlyDcaBudget(monthlyDcaBudget);
        userConfigRepository.lockSingleton();
        List<UserConfigEntity> all = configs();
        UserConfigEntity config = all.isEmpty() ? new UserConfigEntity() : all.get(0);
        if (config.getOwnerId() == null) config.setOwnerId(currentActorApi.userId());
        config.setMonthlyDcaBudget(monthlyDcaBudget);
        return UserConfigView.from(userConfigRepository.save(config));
    }

    @Transactional(readOnly = true)
    public BigDecimal getMonthlyDcaBudget() {
        return configs().stream().findFirst()
                .map(UserConfigEntity::getMonthlyDcaBudget)
                .orElse(null);
    }

    private List<UserConfigEntity> configs() {
        long userId = currentActorApi.userId();
        return userConfigRepository.findAllByOwnerId(userId);
    }

    private void validateMonthlyDcaBudget(BigDecimal monthlyDcaBudget) {
        if (monthlyDcaBudget == null) {
            return;
        }
        if (monthlyDcaBudget.signum() <= 0 || monthlyDcaBudget.scale() > 8
                || monthlyDcaBudget.compareTo(MAX_MONTHLY_DCA_BUDGET) > 0) {
            throw new BusinessException(ErrorCode.MONTHLY_DCA_BUDGET_INVALID,
                    "每月定投预算必须大于 0 且最多 8 位小数");
        }
    }
}
