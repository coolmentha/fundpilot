package com.fundpilot.backend.architecture;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 模块契约门禁:补充 {@link DddLayerArchitectureTest} 的层级方向检查,约束模块 API、
 * 读写 Handler、Gateway 命名、跨模块错误映射、同步调用事务和事件转换链路。
 */
class ModuleContractArchitectureTest {

    private static final String ROOT = "com.fundpilot.backend.";
    private static final Set<String> BUSINESS_MODULES = SpringModulithStructureTest.BUSINESS_MODULES;

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.fundpilot.backend");

    /** 所有模块四层包名,用于判定“职责子包是否存在”。 */
    private final Set<String> packageNames = collectPackageNames();

    @Test
    void 模块API按业务职责划分且禁止万能与纯技术命名() {
        List<String> violations = new ArrayList<>();

        forEachModuleType((module, layer, type) -> {
            if (!type.getPackageName().startsWith(ROOT + module + ".adapter.api")) {
                return;
            }
            String capability = subPackage(type.getPackageName(), ROOT + module + ".adapter.api");
            if (capability == null) {
                violations.add(type.getName() + " 必须放在 adapter.api.<business-capability> 子包内");
                return;
            }
            String simpleName = type.getSimpleName();
            if (!simpleName.endsWith("Api")) {
                return;
            }
            if (simpleName.equalsIgnoreCase(module + "Api")) {
                violations.add(type.getName() + " 是模块级万能 API,必须按业务职责命名");
            }
            if (simpleName.endsWith("CommandApi") || simpleName.endsWith("QueryApi")) {
                violations.add(type.getName() + " 使用纯技术型 API 命名,必须按业务职责命名");
            }
        });

        assertThat(violations).isEmpty();
    }

    @Test
    void application按功能类别与业务职责两级分包且读写Handler命名一致() {
        List<String> violations = new ArrayList<>();

        for (String category : List.of("command", "query", "gateway", "event")) {
            forEachModuleType((module, layer, type) -> {
                String categoryRoot = ROOT + module + ".application." + category;
                if (!type.getPackageName().startsWith(categoryRoot)) {
                    return;
                }
                if (subPackage(type.getPackageName(), categoryRoot) == null) {
                    violations.add(type.getName() + " 必须放在 application." + category
                            + ".<business-capability> 子包内");
                    return;
                }
                String simpleName = type.getSimpleName();
                if (!simpleName.endsWith("Handler")) {
                    return;
                }
                if (category.equals("command") && !simpleName.endsWith("CommandHandler")) {
                    violations.add(type.getName() + " 位于 command 包必须命名为 XxxCommandHandler");
                }
                if (category.equals("query") && !simpleName.endsWith("QueryHandler")) {
                    violations.add(type.getName() + " 位于 query 包必须命名为 XxxQueryHandler");
                }
            });
        }

        assertThat(violations).isEmpty();
    }

    @Test
    void 跨模块目标API只能被调用方GatewayImpl引用并完成错误映射() {
        List<String> violations = new ArrayList<>();

        forEachModuleType((module, layer, source) -> {
            for (Dependency dependency : source.getDirectDependenciesFromSelf()) {
                String targetPackage = dependency.getTargetClass().getPackageName();
                String targetModule = moduleName(targetPackage);
                if (!BUSINESS_MODULES.contains(targetModule) || targetModule.equals(module)) {
                    continue;
                }
                if (!targetPackage.contains(".adapter.api.")) {
                    continue;
                }
                boolean gatewayImpl = source.getPackageName()
                        .startsWith(ROOT + module + ".infrastructure.gateway.")
                        && source.getSimpleName().endsWith("GatewayImpl");
                if (!gatewayImpl) {
                    violations.add(source.getName() + " 直接引用目标模块 API "
                            + dependency.getTargetClass().getName()
                            + ",只有 infrastructure.gateway 中的 GatewayImpl 可以引用并转换错误");
                }
            }
        });

        assertThat(violations).isEmpty();
    }

    @Test
    void Gateway按调用方业务职责命名且不使用目标模块术语() {
        List<String> violations = new ArrayList<>();

        forEachModuleType((module, layer, type) -> {
            String simpleName = type.getSimpleName();
            if (!simpleName.endsWith("Gateway") && !simpleName.endsWith("GatewayImpl")) {
                return;
            }
            String packageName = type.getPackageName();
            String capability = null;
            for (String holder : List.of(ROOT + module + ".application.gateway",
                    ROOT + module + ".infrastructure.gateway",
                    ROOT + module + ".infrastructure.remote",
                    ROOT + module + ".infrastructure.messaging")) {
                if (packageName.startsWith(holder)) {
                    capability = subPackage(packageName, holder);
                }
            }
            if (capability == null) {
                violations.add(type.getName()
                        + " Gateway 必须放在 application.gateway / infrastructure.gateway"
                        + " / infrastructure.remote / infrastructure.messaging 的业务职责子包内");
                return;
            }
            boolean callerCapability =
                    packageNames.contains(ROOT + module + ".application.command." + capability)
                            || packageNames.contains(ROOT + module + ".application.query." + capability)
                            || gatewayIsUsedByHandler(module, type);
            if (!callerCapability) {
                violations.add(type.getName() + " 的职责包 " + capability
                        + " 不是调用方业务职责,必须与本模块 application.command/query 的职责一致");
            }
            String lowerName = simpleName.toLowerCase(Locale.ROOT);
            BUSINESS_MODULES.stream()
                    .filter(other -> !other.equals(module))
                    .filter(lowerName::startsWith)
                    .forEach(other -> violations.add(type.getName()
                            + " 以目标模块名 " + other + " 开头,必须使用调用方业务语言命名"));
        });

        assertThat(violations).isEmpty();
    }

    @Test
    void 同步跨模块调用链共享本地事务且禁止REQUIRES_NEW() {
        List<String> violations = new ArrayList<>();

        forEachModuleType((module, layer, type) -> {
            String packageName = type.getPackageName();
            boolean syncChain = packageName.startsWith(ROOT + module + ".adapter.api")
                    || packageName.startsWith(ROOT + module + ".application.")
                    || packageName.startsWith(ROOT + module + ".infrastructure.gateway.");
            if (!syncChain) {
                return;
            }
            if (requiresNew(type.tryGetAnnotationOfType(Transactional.class).orElse(null))) {
                violations.add(type.getName() + " 在同步调用链上使用 REQUIRES_NEW");
            }
            for (JavaMethod method : type.getMethods()) {
                if (requiresNew(method.tryGetAnnotationOfType(Transactional.class).orElse(null))) {
                    violations.add(type.getName() + "#" + method.getName()
                            + " 在同步调用链上使用 REQUIRES_NEW");
                }
            }
        });

        assertThat(violations).isEmpty();
    }

    @Test
    void 事件监听器只在adapterEvent并以提交后独立事务执行() {
        List<String> violations = new ArrayList<>();

        forEachModuleType((module, layer, type) -> {
            for (JavaMethod method : type.getMethods()) {
                boolean plainListener = method.isAnnotatedWith(EventListener.class);
                boolean transactionalListener = method.isAnnotatedWith(TransactionalEventListener.class);
                boolean moduleListener = method.isAnnotatedWith(ApplicationModuleListener.class);
                boolean integrationEvent = method.getRawParameterTypes().stream()
                        .anyMatch(eventType -> eventType.getPackageName().contains(".application.event."));
                if ((!plainListener && !transactionalListener && !moduleListener) || !integrationEvent) {
                    continue;
                }
                String location = type.getName() + "#" + method.getName();
                if (!type.getPackageName().startsWith(ROOT + module + ".adapter.event")) {
                    violations.add(location + " 事件监听器必须位于 adapter.event 入站适配包");
                }
                if (plainListener && !moduleListener) {
                    violations.add(location + " 必须使用 @TransactionalEventListener,在源事务提交后消费");
                }
                if (moduleListener) {
                    continue;
                }
                Transactional transactional = method.tryGetAnnotationOfType(Transactional.class)
                        .orElse(type.tryGetAnnotationOfType(Transactional.class).orElse(null));
                if (!requiresNew(transactional)) {
                    violations.add(location + " 事件消费必须声明 REQUIRES_NEW 独立事务");
                }
            }
        });

        assertThat(violations).isEmpty();
    }

    @Test
    void 只有infrastructureMessaging可直接使用事件发布基础设施() {
        List<String> violations = new ArrayList<>();

        forEachModuleType((module, layer, source) -> {
            boolean messaging = source.getPackageName()
                    .startsWith(ROOT + module + ".infrastructure.messaging");
            if (messaging) {
                return;
            }
            source.getDirectDependenciesFromSelf().stream()
                    .map(Dependency::getTargetClass)
                    .filter(target -> target.getFullName().equals(ApplicationEventPublisher.class.getName()))
                    .forEach(target -> violations.add(source.getName()
                            + " 直接依赖事件发布基础设施,集成事件必须由 infrastructure.messaging 投递"));
        });

        assertThat(violations).isEmpty();
    }

    @Test
    void domain按聚合职责分包且根包不放业务类型() {
        List<String> violations = new ArrayList<>();

        forEachModuleType((module, layer, type) -> {
            if (type.getPackageName().equals(ROOT + module + ".domain")) {
                violations.add(type.getName() + " 必须归入 domain.<aggregate> 聚合职责包");
            }
        });

        assertThat(violations).isEmpty();
    }

    private static boolean requiresNew(Transactional transactional) {
        return transactional != null && transactional.propagation() == Propagation.REQUIRES_NEW;
    }

    /** 返回 {@code prefix} 之后的第一级子包名;类型直接位于 {@code prefix} 时返回 null。 */
    private static String subPackage(String packageName, String prefix) {
        if (!packageName.startsWith(prefix + ".")) {
            return null;
        }
        String remainder = packageName.substring(prefix.length() + 1);
        int separator = remainder.indexOf('.');
        return separator < 0 ? remainder : remainder.substring(0, separator);
    }

    private Set<String> collectPackageNames() {
        Set<String> names = new HashSet<>();
        classes.forEach(type -> names.add(type.getPackageName()));
        return names;
    }

    private boolean gatewayIsUsedByHandler(String module, JavaClass gateway) {
        String gatewayName = gateway.getSimpleName().replaceFirst("Impl$", "");
        return classes.stream().anyMatch(candidate -> candidate.getPackageName()
                        .startsWith(ROOT + module + ".application.")
                && candidate.getSimpleName().endsWith("Handler")
                && candidate.getDirectDependenciesFromSelf().stream().anyMatch(dependency -> dependency
                        .getTargetClass().getSimpleName().equals(gatewayName)));
    }

    private void forEachModuleType(ModuleTypeVisitor visitor) {
        for (JavaClass type : classes) {
            if (type.getSimpleName().equals("package-info") || type.getSimpleName().isEmpty()) {
                continue;
            }
            String module = moduleName(type.getPackageName());
            if (!BUSINESS_MODULES.contains(module)) {
                continue;
            }
            String layer = layerName(type.getPackageName(), module);
            if (layer == null) {
                continue;
            }
            visitor.visit(module, layer, type);
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

    /** 仅识别四层包;legacy MVC 包返回 null 并被跳过。 */
    private static String layerName(String packageName, String module) {
        String layer = subPackage(packageName, ROOT + module);
        if (layer == null) {
            layer = packageName.equals(ROOT + module) ? null : layer;
        }
        return Set.of("adapter", "application", "domain", "infrastructure").contains(layer) ? layer : null;
    }

    @FunctionalInterface
    private interface ModuleTypeVisitor {
        void visit(String module, String layer, JavaClass type);
    }
}
