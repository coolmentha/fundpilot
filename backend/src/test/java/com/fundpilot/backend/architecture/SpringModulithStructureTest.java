package com.fundpilot.backend.architecture;

import com.fundpilot.backend.FundPilotBackendApplication;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class SpringModulithStructureTest {

    static final Set<String> BUSINESS_MODULES = Set.of(
            "identityaccess",
            "productcatalog",
            "portfolio",
            "accounting",
            "marketdata",
            "discipline",
            "investmentplan",
            "insights",
            "importing"
    );

    private static final Set<String> EXPECTED_MODULES = Set.of(
            "identityaccess", "productcatalog", "portfolio", "accounting", "marketdata",
            "discipline", "investmentplan", "insights", "importing", "platform", "sharedkernel"
    );

    @Test
    void 显式模块边界通过SpringModulith验证() {
        ApplicationModules modules = ApplicationModules.of(FundPilotBackendApplication.class);

        modules.verify();

        assertThat(modules.stream()
                .map(module -> module.getIdentifier().toString())
                .collect(Collectors.toSet()))
                .isEqualTo(EXPECTED_MODULES);
    }

    @Test
    void 旧Mvc顶层包不被误识别为应用模块() {
        ApplicationModules modules = ApplicationModules.of(FundPilotBackendApplication.class);

        assertThat(Set.of("admin", "common", "config", "dca", "exception", "fund",
                "integration", "market", "metrics", "signal", "strategy", "user"))
                .allSatisfy(name -> assertThat(modules.getModuleByName(name)).isEmpty());
    }

    @Test
    void 模块文档模型可生成且覆盖全部业务模块(@TempDir Path outputFolder) {
        ApplicationModules modules = ApplicationModules.of(FundPilotBackendApplication.class);

        new Documenter(modules, Documenter.Options.defaults()
                .withOutputFolder(outputFolder.toString()))
                .writeDocumentation();

        assertThat(outputFolder).isDirectoryContaining(path -> path.getFileName().toString()
                .endsWith(".puml"));
        BUSINESS_MODULES.forEach(name -> assertThat(outputFolder.resolve("module-" + name + ".adoc"))
                .as(name + " module canvas")
                .exists());
    }

    @Test
    void 业务模块公开api和events命名接口() {
        ApplicationModules modules = ApplicationModules.of(FundPilotBackendApplication.class);

        BUSINESS_MODULES.forEach(name -> {
            var namedInterfaces = modules.getModuleByName(name).orElseThrow().getNamedInterfaces();
            assertThat(namedInterfaces.getByName("api")).as(name + "::api").isPresent();
            assertThat(namedInterfaces.getByName("events")).as(name + "::events").isPresent();
        });
    }
}
