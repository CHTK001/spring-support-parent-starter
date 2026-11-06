// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.datasourcehub.support.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/**
 * 控制器映射唯一性契约测试。
 *
 * <p>背景：{@code DbConsoleController} 与 {@code DbSpiCapabilityController} 都挂在
 * {@code /ms/monitor/db} 下，前者早期用裸 JDBC 直写了一套</p>
 * <ul>
 *   <li>{@code users} / {@code users/grants}</li>
 *   <li>{@code processes} / {@code process/kill} / {@code processes/kill}</li>
 *   <li>{@code slow-queries}</li>
 * </ul>
 * <p>后者改用引擎 SPI（{@code UserManager} / {@code PermissionManager} /
 * {@code ProcessManager} / {@code SlowQueryManager}）重写了同名端点，但旧实现没有删干净。
 * 两套 {@code (HTTP 方法, 路径)} 完全相同，Spring 在请求到达时才报
 * <b>Ambiguous handler methods mapped</b>，整个功能 500 —— 启动期不报，编译期更不报。</p>
 *
 * <p>本测试把「同一个 (方法, 路径) 只能有一个处理器」变成可回归的断言：
 * 扫本模块所有 {@code @RestController}，按类级 {@code @RequestMapping} 前缀 + 方法级
 * 组合注解（{@code @GetMapping} 等经 {@link AnnotatedElementUtils} 合并成
 * {@link RequestMapping}）展开，任何重复都算失败，并直接指出两个方法。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
class ControllerMappingConflictTest {

    /**
     * 扫描的根包：本模块的控制器都在这下面。
     */
    private static final String BASE_PACKAGE = "com.chua.starter.datasourcehub";

    /**
     * 至少能扫到的控制器数量。小于它就说明扫描本身失效了（例如测试跑在没编译
     * 主代码的环境里），此时「零冲突」是假绿。
     */
    private static final int MIN_CONTROLLERS = 8;

    /**
     * 本模块内不允许出现两个处理器认领同一个 (HTTP 方法, 路径)。
     *
     * @throws ClassNotFoundException 扫描出的类加载不到（测试类路径残缺）
     */
    @Test
    @DisplayName("本模块 @RestController 的 (HTTP 方法, 路径) 组合不重复")
    void noDuplicateHandlerMappings() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        Set<BeanDefinition> candidates = scanner.findCandidateComponents(BASE_PACKAGE);
        assertTrue(candidates.size() >= MIN_CONTROLLERS,
                "只扫到 " + candidates.size() + " 个 @RestController，扫描口径失效");

        Map<String, String> owners = new LinkedHashMap<>();
        List<String> conflicts = new ArrayList<>();
        for (BeanDefinition candidate : candidates) {
            Class<?> type = Class.forName(candidate.getBeanClassName());
            List<String> bases = pathsOf(AnnotatedElementUtils.findMergedAnnotation(type, RequestMapping.class));
            for (Method method : type.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) {
                    continue;
                }
                String owner = type.getSimpleName() + "#" + method.getName();
                for (String verb : verbsOf(mapping)) {
                    for (String base : bases) {
                        for (String sub : pathsOf(mapping)) {
                            String key = verb + " " + normalize(base, sub);
                            String previous = owners.putIfAbsent(key, owner);
                            if (previous != null) {
                                conflicts.add(key + " -> " + previous + " 与 " + owner);
                            }
                        }
                    }
                }
            }
        }
        assertTrue(conflicts.isEmpty(),
                "同一 (HTTP 方法, 路径) 被多个处理器认领（请求期会报 Ambiguous handler methods）: " + conflicts);
        // 路径拼装自检：这几条正是当初冲突的端点，必须各存在且只存在一份。
        // 若 normalize / 组合注解解析失效，owners 会是空的，上面的「零冲突」就成了假绿。
        for (String expected : List.of("GET /ms/monitor/db/users",
                "GET /ms/monitor/db/processes",
                "POST /ms/monitor/db/processes/kill",
                "GET /ms/monitor/db/slow-queries")) {
            assertTrue(owners.containsKey(expected), "路径拼装失效或端点缺失: " + expected);
        }
    }

    /**
     * 取映射里的路径；未声明时按「根路径」处理（返回单个空串，便于拼接）。
     *
     * @param mapping 合并后的 {@link RequestMapping}，可为 {@code null}
     * @return 路径清单
     */
    private static List<String> pathsOf(RequestMapping mapping) {
        if (mapping == null || mapping.path().length == 0) {
            return List.of("");
        }
        return List.of(mapping.path());
    }

    /**
     * 取映射允许的 HTTP 方法；未限定时记为 {@code ANY}（{@code @RequestMapping} 的通配语义）。
     *
     * @param mapping 合并后的 {@link RequestMapping}
     * @return 方法名集合，按字典序稳定输出
     */
    private static Set<String> verbsOf(RequestMapping mapping) {
        RequestMethod[] methods = mapping.method();
        if (methods.length == 0) {
            return new TreeSet<>(Set.of("ANY"));
        }
        Set<String> verbs = new TreeSet<>();
        for (RequestMethod method : methods) {
            verbs.add(method.name());
        }
        return verbs;
    }

    /**
     * 拼接类级前缀与方法级子路径，折叠重复和尾部斜杠。
     *
     * @param base 类级前缀
     * @param sub  方法级子路径
     * @return 规范化后的完整路径
     */
    private static String normalize(String base, String sub) {
        String joined = ("/" + base + "/" + sub).replaceAll("/{2,}", "/");
        if (joined.length() > 1 && joined.endsWith("/")) {
            return joined.substring(0, joined.length() - 1);
        }
        return joined;
    }
}
