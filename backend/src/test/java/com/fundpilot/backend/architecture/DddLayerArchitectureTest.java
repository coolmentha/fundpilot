package com.fundpilot.backend.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class DddLayerArchitectureTest {

    private static final String ROOT = "com.fundpilot.backend.";
    private static final Set<String> BUSINESS_MODULES = SpringModulithStructureTest.BUSINESS_MODULES;
    private static final Set<String> DOMAIN_FORBIDDEN_PREFIXES = Set.of(
            "org.springframework.",
            "jakarta.persistence.",
            "com.fasterxml.jackson.",
            "lombok."
    );
    private static final int PORTFOLIO_LEGACY_BASELINE = 12;

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.fundpilot.backend");

    @Test
    void 新模块遵守四层与跨模块依赖方向() {
        List<String> violations = new ArrayList<>();

        for (JavaClass source : classes) {
            if (source.getSimpleName().equals("package-info")) {
                continue;
            }
            ModuleType sourceType = ModuleType.of(source.getPackageName());
            if (sourceType == null) {
                continue;
            }
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                validateDependency(sourceType, source, dependency.getTargetClass(), violations);
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void domain与infrastructure按职责分包且禁止port弱语义() {
        List<String> violations = classes.stream()
                .filter(type -> !type.getSimpleName().equals("package-info"))
                .filter(type -> {
                    String packageName = type.getPackageName();
                    return packageName.contains(".domain.model")
                            || packageName.contains(".domain.repository")
                            || packageName.contains(".domain.service")
                            || packageName.contains(".domain.event")
                            || packageName.contains(".persistence.entity")
                            || packageName.contains(".persistence.repository")
                            || packageName.contains(".persistence.mapper")
                            || packageName.contains(".gateway.impl")
                            || packageName.contains(".remote.client")
                            || packageName.contains(".port.")
                            || packageName.endsWith(".port")
                            || type.getSimpleName().endsWith("Port");
                })
                .map(JavaClass::getName)
                .toList();

        assertThat(violations).isEmpty();
    }

    @Test
    void platform和sharedkernel不反向依赖业务模块() {
        List<String> violations = new ArrayList<>();

        classes.stream()
                .filter(type -> type.getPackageName().startsWith(ROOT + "platform")
                        || type.getPackageName().startsWith(ROOT + "sharedkernel"))
                .filter(type -> !type.getSimpleName().equals("package-info"))
                .forEach(source -> source.getDirectDependenciesFromSelf().stream()
                        .map(Dependency::getTargetClass)
                        .filter(target -> BUSINESS_MODULES.contains(moduleName(target.getPackageName())))
                        .forEach(target -> violations.add(source.getName() + " -> " + target.getName())));

        assertThat(violations).isEmpty();
    }

    @Test
    void portfolio旧Mvc例外只能减少() {
        long legacyTypes = classes.stream()
                .filter(type -> type.getPackageName().startsWith(ROOT + "portfolio."))
                .filter(type -> !type.getSimpleName().equals("package-info"))
                .filter(type -> ModuleType.of(type.getPackageName()) == null)
                .count();

        assertThat(legacyTypes)
                .as("portfolio legacy exception count")
                .isLessThanOrEqualTo(PORTFOLIO_LEGACY_BASELINE);
    }

    private void validateDependency(ModuleType sourceType, JavaClass source, JavaClass target,
                                    List<String> violations) {
        String targetPackage = target.getPackageName();
        String targetModule = moduleName(targetPackage);
        boolean targetBusinessModule = BUSINESS_MODULES.contains(targetModule);
        boolean sameModule = sourceType.module().equals(targetModule);

        if (isLegacyPackage(targetPackage)) {
            violations.add(source.getName() + " new module depends on legacy type " + target.getName());
            return;
        }

        if (sourceType.layer().equals("domain")) {
            if (DOMAIN_FORBIDDEN_PREFIXES.stream().anyMatch(targetPackage::startsWith)
                    || targetPackage.startsWith("org.springframework.modulith.")) {
                violations.add(source.getName() + " domain depends on framework " + target.getName());
            }
            if (targetPackage.startsWith(ROOT)
                    && !targetPackage.startsWith(ROOT + sourceType.module() + ".domain")
                    && !targetPackage.startsWith(ROOT + "sharedkernel")) {
                violations.add(source.getName() + " domain crosses boundary to " + target.getName());
            }
            return;
        }

        if (sourceType.layer().equals("adapter")) {
            if (sameModule && (targetPackage.contains(".domain.")
                    || targetPackage.contains(".infrastructure."))) {
                violations.add(source.getName() + " adapter bypasses application via " + target.getName());
            }
            if (targetBusinessModule && !sameModule
                    && !(source.getPackageName().contains(".adapter.event.")
                    && targetPackage.contains(".application.event."))) {
                violations.add(source.getName() + " adapter crosses boundary to " + target.getName());
            }
            return;
        }

        if (sourceType.layer().equals("application")) {
            if (sameModule && (targetPackage.contains(".adapter.")
                    || targetPackage.contains(".infrastructure."))) {
                violations.add(source.getName() + " application depends outward on " + target.getName());
            }
            if (targetBusinessModule && !sameModule) {
                violations.add(source.getName() + " application crosses boundary to " + target.getName());
            }
            return;
        }

        if (sourceType.layer().equals("infrastructure") && targetBusinessModule && !sameModule
                && !(source.getPackageName().contains(".infrastructure.gateway.")
                && targetPackage.contains(".adapter.api."))) {
            violations.add(source.getName() + " infrastructure crosses boundary outside gateway/API: "
                    + target.getName());
        }
    }

    private static String moduleName(String packageName) {
        if (!packageName.startsWith(ROOT)) {
            return "";
        }
        String remainder = packageName.substring(ROOT.length());
        int separator = remainder.indexOf('.');
        return separator < 0 ? remainder : remainder.substring(0, separator);
    }

    private static boolean isLegacyPackage(String packageName) {
        if (!packageName.startsWith(ROOT)) {
            return false;
        }
        String module = moduleName(packageName);
        if (module.equals("platform") || module.equals("sharedkernel")) {
            return false;
        }
        return ModuleType.of(packageName) == null;
    }

    private record ModuleType(String module, String layer) {

        private static ModuleType of(String packageName) {
            String module = moduleName(packageName);
            if (!BUSINESS_MODULES.contains(module)) {
                return null;
            }
            String prefix = ROOT + module + ".";
            if (!packageName.startsWith(prefix)) {
                return null;
            }
            String remainder = packageName.substring(prefix.length());
            int separator = remainder.indexOf('.');
            String layer = separator < 0 ? remainder : remainder.substring(0, separator);
            return Set.of("adapter", "application", "domain", "infrastructure").contains(layer)
                    ? new ModuleType(module, layer)
                    : null;
        }
    }
}
