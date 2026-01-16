package com.chua.starter.strategy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略模块配置属性
 * <p>
 * 统一管理策略模块的所有配置项，包括限流、防抖、熔断等功能开关和实现类型。
 * </p>
 *
 * <h3>配置示例：</h3>
 * <pre>
 * plugin:
 *   strategy:
 *     enable: true                    # 策略模块总开关
 *     rate-limiter:
 *       enabled: true                 # 限流功能开关
 *       type: local                    # 限流实现类型：local(本地) / redis(分布式)
 *     debounce:
 *       enabled: true                  # 防抖功能开关
 *       type: local                    # 防抖实现类型：local(本地) / redis(分布式)
 *     circuit-breaker:
 *       enabled: true                  # 熔断功能开关
 * </pre>
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Data
@ConfigurationProperties(prefix = "plugin.strategy", ignoreInvalidFields = true)
public class StrategyProperties {

    /**
     * 策略模块总开关
     */
    private boolean enable = true;

    /**
     * 限流配置
     */
    private RateLimiterConfig rateLimiter = new RateLimiterConfig();

    /**
     * 防抖配置
     */
    private DebounceConfig debounce = new DebounceConfig();

    /**
     * 熔断配置
     */
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    /**
     * XSS 防护配置
     */
    private XssConfig xss = new XssConfig();

    /**
     * 路径穿透防护配置
     */
    private PathTraversalConfig pathTraversal = new PathTraversalConfig();

    /**
     * SQL注入防护配置
     */
    private SqlInjectionConfig sqlInjection = new SqlInjectionConfig();

    /**
     * CSRF防护配置
     */
    private CsrfConfig csrf = new CsrfConfig();

    /**
     * 请求大小限制配置
     */
    private RequestSizeLimitConfig requestSizeLimit = new RequestSizeLimitConfig();

    /**
     * HTTP方法限制配置
     */
    private HttpMethodRestrictionConfig httpMethodRestriction = new HttpMethodRestrictionConfig();

    /**
     * 请求超时配置
     */
    private RequestTimeoutConfig requestTimeout = new RequestTimeoutConfig();

    /**
     * 参数数量限制配置
     */
    private ParameterCountLimitConfig parameterCountLimit = new ParameterCountLimitConfig();

    /**
     * 内容安全策略配置
     */
    private ContentSecurityPolicyConfig contentSecurityPolicy = new ContentSecurityPolicyConfig();

    /**
     * 点击劫持防护配置
     */
    private ClickjackingProtectionConfig clickjackingProtection = new ClickjackingProtectionConfig();

    /**
     * 限流配置
     */
    @Data
    public static class RateLimiterConfig {
        /**
         * 限流功能开关
         */
        private boolean enabled = true;

        /**
         * 限流实现类型：local(本地) / redis(分布式)
         */
        private String type = "local";
    }

    /**
     * 防抖配置
     */
    @Data
    public static class DebounceConfig {
        /**
         * 防抖功能开关
         */
        private boolean enabled = true;

        /**
         * 防抖实现类型：local(本地) / redis(分布式)
         */
        private String type = "local";
    }

    /**
     * 熔断配置
     */
    @Data
    public static class CircuitBreakerConfig {
        /**
         * 熔断功能开关
         */
        private boolean enabled = true;
    }

    /**
     * XSS 防护配置
     */
    @Data
    public static class XssConfig {
        /**
         * 是否开启 XSS 防护
         */
        private boolean enabled = false;

        /**
         * 命中规则时返回给前端的提示信息
         */
        private String message = "请求参数包含非法脚本内容";

        /**
         * 需要进行 XSS 检测的路径模式
         */
        private List<String> includePatterns = new ArrayList<>(
                Collections.singletonList("/**"));

        /**
         * 不需要进行 XSS 检测的路径模式
         */
        private List<String> excludePatterns = new ArrayList<>();
    }

    /**
     * 路径穿透防护配置
     */
    @Data
    public static class PathTraversalConfig {
        /**
         * 是否开启路径穿透防护
         */
        private boolean enabled = true;

        /**
         * 命中规则时返回给前端的提示信息
         */
        private String message = "请求包含非法路径";

        /**
         * 需要进行路径穿透检测的路径模式
         */
        private List<String> includePatterns = new ArrayList<>(
                Collections.singletonList("/**"));

        /**
         * 不需要进行路径穿透检测的路径模式
         */
        private List<String> excludePatterns = new ArrayList<>();
    }

    /**
     * SQL注入防护配置
     */
    @Data
    public static class SqlInjectionConfig {
        /**
         * 是否开启SQL注入防护
         */
        private boolean enabled = true;

        /**
         * 命中规则时返回给前端的提示信息
         */
        private String message = "请求参数包含SQL注入攻击";

        /**
         * 需要进行SQL注入检测的路径模式
         */
        private List<String> includePatterns = new ArrayList<>(
                Collections.singletonList("/**"));

        /**
         * 不需要进行SQL注入检测的路径模式
         */
        private List<String> excludePatterns = new ArrayList<>();
    }

    /**
     * CSRF防护配置
     */
    @Data
    public static class CsrfConfig {
        /**
         * 是否开启CSRF防护
         */
        private boolean enabled = false;

        /**
         * CSRF Token请求头名称
         */
        private String headerName = "X-CSRF-Token";

        /**
         * CSRF Token请求参数名称
         */
        private String parameterName = "_csrf";

        /**
         * 豁免的HTTP方法
         */
        private List<String> exemptMethods = new ArrayList<>(
                List.of("GET", "HEAD", "OPTIONS"));

        /**
         * 豁免的路径模式
         */
        private List<String> exemptPatterns = new ArrayList<>();

        /**
         * 命中规则时返回给前端的提示信息
         */
        private String message = "CSRF Token验证失败";
    }

    /**
     * 请求大小限制配置
     */
    @Data
    public static class RequestSizeLimitConfig {
        /**
         * 是否开启请求大小限制
         */
        private boolean enabled = true;

        /**
         * 默认最大请求大小（字节），默认10MB
         */
        private long maxSize = 10 * 1024 * 1024;

        /**
         * 路径级别的最大请求大小配置
         */
        private Map<String, Long> pathMaxSizes = new HashMap<>();

        /**
         * 命中规则时返回给前端的提示信息
         */
        private String message = "请求体大小超过限制";

        /**
         * 需要进行请求大小限制的路径模式
         */
        private List<String> includePatterns = new ArrayList<>(
                Collections.singletonList("/**"));

        /**
         * 不需要进行请求大小限制的路径模式
         */
        private List<String> excludePatterns = new ArrayList<>();
    }

    /**
     * HTTP方法限制配置
     */
    @Data
    public static class HttpMethodRestrictionConfig {
        /**
         * 是否开启HTTP方法限制
         */
        private boolean enabled = false;

        /**
         * 默认允许的HTTP方法
         */
        private List<String> defaultAllowedMethods = new ArrayList<>(
                List.of("GET", "POST", "PUT", "DELETE", "PATCH"));

        /**
         * 路径级别的HTTP方法配置
         */
        private Map<String, List<String>> pathMethods = new HashMap<>();

        /**
         * 命中规则时返回给前端的提示信息
         */
        private String message = "不允许的HTTP方法";
    }

    /**
     * 请求超时配置
     */
    @Data
    public static class RequestTimeoutConfig {
        /**
         * 是否开启请求超时控制
         */
        private boolean enabled = false;

        /**
         * 默认超时时间（毫秒），默认30秒
         */
        private long defaultTimeout = 30000;

        /**
         * 路径级别的超时配置
         */
        private Map<String, Long> pathTimeouts = new HashMap<>();

        /**
         * 需要进行请求超时控制的路径模式
         */
        private List<String> includePatterns = new ArrayList<>(
                Collections.singletonList("/**"));

        /**
         * 不需要进行请求超时控制的路径模式
         */
        private List<String> excludePatterns = new ArrayList<>();
    }

    /**
     * 参数数量限制配置
     */
    @Data
    public static class ParameterCountLimitConfig {
        /**
         * 是否开启参数数量限制
         */
        private boolean enabled = true;

        /**
         * 默认最大参数数量
         */
        private int maxParameters = 100;

        /**
         * 路径级别的参数数量限制配置
         */
        private Map<String, Integer> pathLimits = new HashMap<>();

        /**
         * 命中规则时返回给前端的提示信息
         */
        private String message = "请求参数数量超过限制";

        /**
         * 需要进行参数数量限制的路径模式
         */
        private List<String> includePatterns = new ArrayList<>(
                Collections.singletonList("/**"));

        /**
         * 不需要进行参数数量限制的路径模式
         */
        private List<String> excludePatterns = new ArrayList<>();
    }

    /**
     * 内容安全策略配置
     */
    @Data
    public static class ContentSecurityPolicyConfig {
        /**
         * 是否开启内容安全策略
         */
        private boolean enabled = false;

        /**
         * 默认CSP策略
         */
        private String policy = "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'";

        /**
         * 路径级别的CSP策略配置
         */
        private Map<String, String> pathPolicies = new HashMap<>();

        /**
         * CSP报告URI
         */
        private String reportUri = null;

        /**
         * 需要进行CSP设置的路径模式
         */
        private List<String> includePatterns = new ArrayList<>(
                Collections.singletonList("/**"));

        /**
         * 不需要进行CSP设置的路径模式
         */
        private List<String> excludePatterns = new ArrayList<>();
    }

    /**
     * 点击劫持防护配置
     */
    @Data
    public static class ClickjackingProtectionConfig {
        /**
         * 是否开启点击劫持防护
         */
        private boolean enabled = true;

        /**
         * X-Frame-Options策略值：DENY, SAMEORIGIN, ALLOW-FROM
         */
        private String policy = "DENY";

        /**
         * 路径级别的X-Frame-Options策略配置
         */
        private Map<String, String> pathPolicies = new HashMap<>();

        /**
         * 豁免的路径模式
         */
        private List<String> exemptPatterns = new ArrayList<>();

        /**
         * 需要进行点击劫持防护的路径模式
         */
        private List<String> includePatterns = new ArrayList<>(
                Collections.singletonList("/**"));
    }
}

