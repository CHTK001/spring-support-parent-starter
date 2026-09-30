package com.chua.starter.eduai.support.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * application.yml 的安全配置回归测试。
 *
 * <p><b>为什么对配置文件做测试</b>：actuator 的暴露面与鉴权白名单是<b>运行时行为</b>，
 * 但它们由这份 yml 决定，且改动代价极小（一行），后果却极大 ——
 * 一旦有人把暴露面调回 {@code *}，{@code /actuator/env}、{@code loggers}、
 * {@code threaddump} 会立刻无鉴权可达，其中 {@code loggers} 还能被 POST 篡改日志级别。
 * 这类「一行改动打穿防护」的场景，编译与页面都不会给出任何提示，
 * 只有测试能拦住。</p>
 *
 * <p>实测背景（2026-09-30）：修复前 {@code /actuator/loggers} 无鉴权返回 200，
 * POST 修改 ROOT 级别返回 204 且确实生效；{@code health} 的 show-details 为
 * {@code always}，对外暴露数据库等组件明细。</p>
 *
 * <p>注意：本测试读的是 classpath 上的 {@code application.yml}（即构建产物），
 * 因此能真实反映将被打进 jar 的配置。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
class ApplicationConfigSecurityTest {

    /**
     * actuator 暴露面中明确不应出现的端点。
     *
     * <p>这些端点会泄露配置或允许运行期变更：
     * {@code env}/{@code configprops} 泄露配置（凭据虽被脱敏，但键名与结构仍属敏感）、
     * {@code loggers} 允许写、{@code threaddump} 泄露调用栈与 SQL 片段、
     * {@code beans}/{@code mappings}/{@code conditions} 泄露内部结构。</p>
     */
    private static final List<String> DANGEROUS_ENDPOINTS = List.of(
            "env", "beans", "configprops", "loggers", "threaddump",
            "mappings", "caches", "conditions", "sbom", "scheduledtasks");

    /**
     * 读取 classpath 上的 application.yml。
     *
     * @return 解析后的映射
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> loadApplicationYaml() {
        try (InputStream in = ApplicationConfigSecurityTest.class.getClassLoader()
                .getResourceAsStream("application.yml")) {
            assertNotNull(in, "classpath 上应存在 application.yml");
            return new Yaml().load(in);
        } catch (Exception e) {
            throw new IllegalStateException("读取 application.yml 失败", e);
        }
    }

    /**
     * 按路径逐层取值。
     *
     * @param root  根映射
     * @param paths 路径片段
     * @return 命中的值；任一层缺失返回 {@code null}
     */
    @SuppressWarnings("unchecked")
    private static Object dig(Map<String, Object> root, String... paths) {
        Object current = root;
        for (String path : paths) {
            if (!(current instanceof Map)) {
                return null;
            }
            current = ((Map<String, Object>) current).get(path);
        }
        return current;
    }

    /**
     * actuator 暴露面不得包含 {@code *}，也不得暴露危险端点。
     *
     * <p>{@code include} 为 {@code *} 等于把全部端点（含可写的 loggers）开放出去。</p>
     */
    @Test
    @DisplayName("actuator 暴露面不含 * 与危险端点")
    void exposure_shouldNotIncludeWildcardOrDangerousEndpoints() {
        Object include = dig(loadApplicationYaml(),
                "management", "endpoints", "web", "exposure", "include");
        assertNotNull(include, "应显式声明 management.endpoints.web.exposure.include，"
                + "否则会回落到框架默认的 \"*\"");

        String value = String.valueOf(include);
        assertTrue(!value.contains("*"),
                "exposure.include 不得含 *（会开放全部端点，含可写的 loggers），实际: " + value);

        for (String endpoint : DANGEROUS_ENDPOINTS) {
            assertTrue(!value.contains(endpoint),
                    "exposure.include 不应包含危险端点 " + endpoint + "，实际: " + value);
        }
    }

    /**
     * 健康详情不得设为 always。
     *
     * <p>{@code always} 会对外暴露数据库、磁盘等组件明细。</p>
     */
    @Test
    @DisplayName("health 的 show-details 不是 always")
    void healthShowDetails_shouldNotBeAlways() {
        Object showDetails = dig(loadApplicationYaml(),
                "management", "endpoint", "health", "show-details");
        assertNotNull(showDetails, "应显式声明 show-details，避免回落到框架默认的 always");
        assertTrue(!"always".equalsIgnoreCase(String.valueOf(showDetails)),
                "show-details 不得为 always（会暴露组件明细），实际: " + showDetails);
    }

    /**
     * 鉴权白名单不得放行 actuator 全部路径。
     *
     * <p>这是第二道防线：即便暴露面被调宽，白名单不放行则未带令牌的请求仍会被 403 拦下。
     * 只放行 {@code /actuator/health}（监控探活）。</p>
     */
    @Test
    @DisplayName("白名单只放行 /actuator/health，不放行 /actuator/**")
    void whitelist_shouldOnlyAllowActuatorHealth() {
        Object whitelist = dig(loadApplicationYaml(), "plugin", "oauth", "whitelist");
        assertNotNull(whitelist, "应显式声明白名单（use-default-whitelist 已置 false）");
        assertTrue(whitelist instanceof List, "白名单应为列表，实际: " + whitelist.getClass());

        @SuppressWarnings("unchecked")
        var entries = (List<Object>) whitelist;
        for (Object entry : entries) {
            String pattern = String.valueOf(entry);
            assertTrue(!"/actuator/**".equals(pattern) && !"/actuator".equals(pattern),
                    "白名单不得整体放行 actuator，实际包含: " + pattern);
        }
        assertTrue(entries.stream().map(String::valueOf).anyMatch("/actuator/health"::equals),
                "白名单应放行 /actuator/health 以便监控探活");
    }

    /**
     * 不继承框架默认白名单。
     *
     * <p>框架的 {@code DEFAULT_WHITELIST} 内置 {@code /actuator} 与 {@code /actuator/**}
     * （见 {@code AuthClientProperties}），继承它等于把 actuator 重新放开。</p>
     */
    @Test
    @DisplayName("use-default-whitelist 为 false（框架默认清单放开 actuator）")
    void defaultWhitelist_shouldBeDisabled() {
        Object useDefault = dig(loadApplicationYaml(), "plugin", "oauth", "use-default-whitelist");
        assertNotNull(useDefault, "应显式声明 use-default-whitelist");
        assertTrue(Boolean.FALSE.equals(useDefault) || "false".equalsIgnoreCase(String.valueOf(useDefault)),
                "必须关闭框架默认白名单（其内置 /actuator/** 会重新放开 actuator），实际: " + useDefault);
    }

    /**
     * 数据源口令不得是「非空的硬编码值」。
     *
     * <p>两种形式都是安全的，本测试都接受：</p>
     * <ul>
     *   <li>{@code ${DB_PASSWORD}} —— 纯占位符，未注入即启动失败（强制注入）</li>
     *   <li>{@code ${DB_PASSWORD:}} 或 {@code ""} —— 空默认，本地免密开发，
     *       生产由部署参数（systemd ExecStart）覆盖</li>
     * </ul>
     *
     * <p>要拦的是第三种：{@code password: 某口令} —— 一个写进仓库的、非空的真实口令。
     * 那等同于把凭据公开在版本库里，且未注入时会「用一个大家都知道的口令正常跑起来」，
     * 不会有任何提示。</p>
     */
    @Test
    @DisplayName("数据源口令不是非空的硬编码值")
    void datasourcePassword_shouldNotBeHardcoded() {
        Object password = dig(loadApplicationYaml(), "spring", "datasource", "password");
        assertNotNull(password, "应声明数据源口令");
        String value = String.valueOf(password).trim();

        // 空值：允许（本地免密 + 生产覆盖）
        if (value.isEmpty()) {
            return;
        }
        // 占位符：允许，无论是否带默认值
        assertTrue(value.startsWith("${") && value.endsWith("}"),
                "数据源口令要么为空（由部署覆盖）、要么是占位符（形如 ${DB_PASSWORD}），"
                        + "不得是写死的真实口令，实际: " + value);

        // 占位符若带默认值，该默认值必须是空串 —— 不能是真实口令
        int colon = value.indexOf(':');
        if (colon > 0) {
            String fallback = value.substring(colon + 1, value.length() - 1).trim();
            assertTrue(fallback.isEmpty(),
                    "占位符的默认值必须为空（本地免密），不得填入真实口令，实际: " + fallback);
        }
    }
}