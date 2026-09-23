package com.fundpilot.backend.architecture;

import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 测试分片守卫。CI 用 {@code -Pshard-N} 限定 surefire includes,只有被某个分片 includes 覆盖的
 * 测试类才会在 CI 执行;新增顶层包或调整分片边界时若漏配,整个包的测试会被静默跳过。
 * 分片的唯一事实来源是 {@code pom.xml} 的 shard profile,这里据此校验每个测试类恰好归属一个分片,
 * 并锁定 {@code ci.yml} 的 matrix 与 pom 的 profile 集合一致。
 * <p>
 * 匹配按"声明包"而非源文件目录进行:分片 includes 由 surefire 匹配 {@code target/test-classes}
 * 下的 class 路径,而本仓库存在源文件目录与声明包不一致的测试
 * (如 {@code exception/GlobalExceptionHandlerTest.java} 声明 {@code platform.web.error})。
 */
class TestShardAssignmentTest {

    private static final Path POM = Path.of("pom.xml");
    private static final Path CI_WORKFLOW = Path.of("..", ".github", "workflows", "ci.yml");
    private static final Path TEST_SOURCES = Path.of("src", "test", "java");
    private static final Pattern SHARD_PROFILE = Pattern.compile(
            "<id>(shard-\\d+)</id>(.*?)</profile>", Pattern.DOTALL);
    private static final Pattern SHARD_INCLUDE = Pattern.compile(
            "<include>(com/fundpilot/backend/[^<]+)</include>");
    private static final Pattern CI_SHARD_MATRIX = Pattern.compile("shard:\\s*\\[([^\\]]+)\\]");
    private static final Pattern CI_SHARD_NAME = Pattern.compile("[\\w-]+");
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;");
    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    @Test
    void 每个测试类恰好属于一个分片() throws IOException {
        Map<String, List<String>> shardIncludes = shardIncludes();
        assertThat(shardIncludes).as("pom.xml 中的 shard profile").hasSize(6);

        List<String> testClasses = testClasses();
        List<String> unassigned = new ArrayList<>();
        List<String> duplicated = new ArrayList<>();
        for (String testClass : testClasses) {
            List<String> owners = shardIncludes.entrySet().stream()
                    .filter(shard -> shard.getValue().stream()
                            .anyMatch(pattern -> MATCHER.match(pattern, testClass)))
                    .map(Map.Entry::getKey)
                    .toList();
            if (owners.isEmpty()) {
                unassigned.add(testClass);
            } else if (owners.size() > 1) {
                duplicated.add(testClass + " 同时命中 " + owners);
            }
        }

        assertThat(unassigned).as("未被任何分片 includes 覆盖的测试类,CI 会静默跳过").isEmpty();
        assertThat(duplicated).as("被多个分片重复执行的测试类").isEmpty();
        // 防止本测试自身静默通过:每个分片都必须真的选中测试,整体扫描也不能为空。
        assertThat(shardIncludes).allSatisfy((shard, patterns) -> assertThat(patterns)
                .as("%s 的 includes", shard)
                .isNotEmpty()
                .anyMatch(pattern -> testClasses.stream().anyMatch(candidate -> MATCHER.match(pattern, candidate))));
    }

    @Test
    void ci矩阵与pom分片profile一一对应() throws IOException {
        // Maven 对不存在的 profile id 只打 WARNING 不报错,只是不激活该 profile;
        // 于是 ci.yml 里分片名写错时,surefire 会退回默认 includes 把全量测试都跑一遍,
        // 既拿不到分片收益又不会有任何失败信号。所以两边必须逐一对应。
        assertThat(ciMatrixShards()).as("ci.yml 的 matrix.shard")
                .isNotEmpty()
                .doesNotHaveDuplicates()
                .containsExactlyInAnyOrderElementsOf(shardIncludes().keySet());
    }

    private static List<String> ciMatrixShards() throws IOException {
        Matcher matrix = CI_SHARD_MATRIX.matcher(Files.readString(CI_WORKFLOW));
        if (!matrix.find()) {
            throw new IllegalStateException("ci.yml 中找不到 matrix.shard 定义");
        }
        return CI_SHARD_NAME.matcher(matrix.group(1)).results()
                .map(MatchResult::group)
                .toList();
    }

    private static List<String> testClasses() throws IOException {
        try (Stream<Path> files = Files.walk(TEST_SOURCES)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> isTestSource(path.getFileName().toString()))
                    .map(TestShardAssignmentTest::classPath)
                    .sorted()
                    .toList();
        }
    }

    private static boolean isTestSource(String fileName) {
        return fileName.endsWith("Test.java") || fileName.endsWith("Tests.java")
                || fileName.endsWith("TestCase.java");
    }

    /** 还原 surefire 看到的路径:包目录 + 类名,与源文件所在目录无关。 */
    private static String classPath(Path source) {
        try {
            Matcher declaration = PACKAGE_DECLARATION.matcher(Files.readString(source));
            if (!declaration.find()) {
                throw new IllegalStateException("缺少 package 声明: " + source);
            }
            return declaration.group(1).replace('.', '/') + "/" + source.getFileName();
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取测试源文件 " + source, ex);
        }
    }

    private static Map<String, List<String>> shardIncludes() throws IOException {
        String pom = Files.readString(POM);
        Map<String, List<String>> includes = new LinkedHashMap<>();
        Matcher profile = SHARD_PROFILE.matcher(pom);
        while (profile.find()) {
            List<String> patterns = new ArrayList<>();
            Matcher include = SHARD_INCLUDE.matcher(profile.group(2));
            while (include.find()) {
                patterns.add(include.group(1));
            }
            includes.put(profile.group(1), List.copyOf(patterns));
        }
        return includes;
    }
}