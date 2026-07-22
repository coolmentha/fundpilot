package com.fundpilot.backend.user.service;

import com.fundpilot.backend.user.entity.SiteUserEntity;
import com.fundpilot.backend.user.entity.UserRole;
import com.fundpilot.backend.user.repository.SiteUserRepository;
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
    private final SiteUserRepository repository;
    private final PasswordService passwordService;
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
        SiteUserEntity existing = repository.findByUsername(properties.username().trim()).orElse(null);
        if (existing != null) {
            fundRepository.claimUnowned(existing.getId());
            fundGroupRepository.claimUnowned(existing.getId());
            userConfigRepository.claimUnowned(existing.getId());
            portfolioSnapshotRepository.claimUnowned(existing.getId());
            return;
        }
        SiteUserEntity admin = new SiteUserEntity();
        admin.setUsername(properties.username().trim());
        admin.setPasswordHash(passwordService.hash(properties.password()));
        admin.setRole(UserRole.ADMIN);
        admin.setEnabled(true);
        SiteUserEntity saved = repository.save(admin);
        fundRepository.claimUnowned(saved.getId());
        fundGroupRepository.claimUnowned(saved.getId());
        userConfigRepository.claimUnowned(saved.getId());
        portfolioSnapshotRepository.claimUnowned(saved.getId());
    }
}
