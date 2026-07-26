package com.fundpilot.backend.user.service;

import com.fundpilot.backend.identityaccess.adapter.api.useradministration.UserAdministrationApi;
import com.fundpilot.backend.fund.repository.FundRepository;
import com.fundpilot.backend.fund.repository.FundGroupRepository;
import com.fundpilot.backend.user.repository.UserConfigRepository;
import com.fundpilot.backend.portfolio.repository.PortfolioReturnSnapshotRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

@ConfigurationProperties("fundpilot.auth.bootstrap")
record BootstrapAdminProperties(String username, String password) {}

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(BootstrapAdminProperties.class)
class UserAuthConfiguration {}

@Component
@RequiredArgsConstructor
public class InitialAdminInitializer implements ApplicationRunner {
    private final UserAdministrationApi users;
    private final BootstrapAdminProperties properties;
    private final FundRepository fundRepository;
    private final FundGroupRepository fundGroupRepository;
    private final UserConfigRepository userConfigRepository;
    private final PortfolioReturnSnapshotRepository portfolioSnapshotRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.username() == null || properties.username().isBlank()
                || properties.password() == null || properties.password().isBlank()
                ) return;
        long userId = users.ensureBootstrapAdmin(properties.username(), properties.password()).id();
        fundRepository.claimUnowned(userId);
        fundGroupRepository.claimUnowned(userId);
        userConfigRepository.claimUnowned(userId);
        portfolioSnapshotRepository.claimUnowned(userId);
    }
}
