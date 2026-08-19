package com.chua.starter.oauth.client.support.properties;

import com.google.common.collect.Sets;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * 客户端配置
 *
 * @author CH
 */
@Data
@ConfigurationProperties(prefix = AuthClientProperties.PRE, ignoreInvalidFields = true)
public class AuthClientProperties {

    /**
     * 是否启用
     */
    private boolean enable = true;

    /** PRE */
    public static final String PRE = "plugin.oauth";

    /**
     * 内置默认白名单（不会被 yaml 配置覆盖）
     */
    private static final List<String> DEFAULT_WHITELIST = List.of(
            "/**/captcha",
            "/**/login",
            "/**/oauth",
            "/**/doLogin",
            "/**/logout",
            "/doc.html",
            "/actuator",
            "/actuator/**",
            "/report",
            "/webjars/**",
            "/markdown/**",
            "/v1/sys/setting",
            "/static/**",
            "/v1/file/**",
            "/v1/models",
            "/v1/chat/**",
            "/v1/completions",
            "/v1/embeddings",
            "/v1/cc/**",
            "/**/*.html",
            "/**/users/loginCode",
            "/v3/api-docs/**",
            "/**/node/receive_push"
    );

    public AuthClientProperties() {
    }

    /**
     * 获取完整白名单（内置默认 + yaml 自定义 + 动态添加）
     */
    public List<String> getEffectiveWhitelist() {
        List<String> effective = new java.util.ArrayList<>(DEFAULT_WHITELIST);
        if (whitelist != null) {
            effective.addAll(whitelist);
        }
        effective.addAll(dynamicWhitelist);
        return effective;
    }

    /**
     * 动态白名单（运行时可增删，线程安全）
     */
    private final java.util.Set<String> dynamicWhitelist = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 动态添加白名单规则
     *
     * @param pattern URL Pattern，支持 Ant 风格，例如 /v2/face/**
     */
    public void addWhitelist(String pattern) {
        if (null != pattern && !pattern.isBlank()) {
            dynamicWhitelist.add(pattern.trim());
        }
    }

    /**
     * 动态删除白名单规则
     *
     * @param pattern 已添加的 URL Pattern
     */
    public void removeWhitelist(String pattern) {
        dynamicWhitelist.remove(pattern);
    }

    /**
     * 清空动态白名单
     */
    public void clearDynamicWhitelist() {
        dynamicWhitelist.clear();
    }
    /**
     * 连接超时时间（毫秒）
     */
    private long connectTimeout = 5000;

    /**
     * 读取超时时间（毫秒）
     */
    private long readTimeout = 10000;

    /**
     * 请求超时时间（毫秒）
     */
    private long requestTimeout = 15000;

    /**
     * 重试配置
     */
    private RetryConfig retry = new RetryConfig();

    /**
     * 熔断配置
     */
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    /**
     * 限流配置
     * <p>
     * OAuth 客户端限流能力，支持接口 QPS 限流与登录防暴力破解限流。
     * </p>
     */
    private RateLimit rateLimit = new RateLimit();

    /**
     * 客户端缓存鉴权信息(用于提高访问效率)(s)
     */
    private long cacheTimeout =  6 * 60 * 60;
    /**
     * 是否开启客户端缓存冷热备份
     */
    private boolean cacheHotColdBackup = true;
    /**
     * 鉴权地址
     */
    private String address;
    /**
     * 登录地址
     */
    private String loginAddress = "${plugin.oauth.auth-address:}";
    /**
     * 加密方式
     */
    private String encryption = "aes";

    /**
     * 是否启用数据加密
     */
    private boolean enableEncryption = true;

    /**
     * 拦截地址
     */
    private Set<String> blockAddress = Sets.newHashSet("/*");

    /**
     * 白名单
     */
    private List<String> whitelist = new LinkedList<>();

    /**
     * 外部白名单文件路径
     * <p>
     * 支持动态加载和热更新，文件变更后自动重新加载。
     * 文件格式：每行一个URL Pattern，支持 # 注释
     * </p>
     */
    private String whitelistFile = "config/oauth.whitelist";

    /**
     * 协议类型
     * <p>
     * 可选值：
     * <ul>
     * <li><strong>armeria</strong> - Armeria协议，基于Netty高性能异步框架，[推荐]</li>
     * <li><strong>http</strong> - HTTP协议，基于Unirest，兼容性好</li>
     * <li><strong>static</strong> - 静态协议，用于测试</li>
     * </ul>
     * </p>
     */
    private String protocol = "static";
    /**
     * token-name
     */
    private String tokenName = "x-oauth-token";
    /**
     * cookie-name
     */
    private String cookieName = "x-oauth-cookie";
    /**
     * token 别名
     */
    private String alias = "Boren-Token";
    /**
     * 登录页
     */
    private String loginPage = "/login";
    /**
     * 登出页
     */
    private String logoutPage = "/logout";
    /**
     * 临时令牌页
     */
    private String temporaryTokenPage = "/temporary-token";
    /**
     * 升级页
     */
    private String upgradePage = "/upgrade";
    /**
     * 鉴权地址
     */
    private String oauthUrl = "/verify";
    /**
     * 均衡模式
     */
    private String balance = "polling";

    /**
     * 无权限页面
     */
    private String noPermissionPage = "/oauth-page";

    /**
     * 密钥
     */
    private Aksk key = new Aksk();
    /**
     * 临时用户
     */
    private TempUser temp = new TempUser();

    /**
     * 指纹验证配置
     */
    private Fingerprint fingerprint = new Fingerprint();


    /**
     * AK/SK 密钥配置
     * <p>
     * 用于对请求数据进行二次加密，提高安全性
     * </p>
     */
    @Data
    public static class Aksk {
        /**
         * 是否启用AK/SK加密
         */
        private boolean enabled = false;
        /**
         * AccessKey (用于标识客户端)
         */
        private String accessKey;
        /**
         * SecretKey (用于加密数据)
         */
        private String secretKey;
    }

    @Data
    public static class TempUser {

        /**
         * 菜单文件
         */
        private String menuPath;
        /**
         * 临时账号配置(只用于账号类型为Embed)
         * <p>
         * [安全警告]生产环境禁止启用临时账号功能！
         * 格式: username:password;username2:password2
         * </p>
         */
        private String user = "admin:admin@123!456";
    }

    /**
     * 指纹验证配置
     * <p>
     * 用于配置浏览器指纹验证功能
     * </p>
     */
    @Data
    public static class Fingerprint {
        /**
         * 是否开启全局指纹验证
         * <p>
         * 开启后，所有需要认证的接口都会验证浏览器指纹，
         * 而不仅仅是标记了 @VerifyFingerprint 注解的接口。
         * </p>
         * <p>
         * 默认关闭，只有标记了 @VerifyFingerprint 注解的接口才会验证指纹。
         * </p>
         */
        private boolean globalVerification = false;
        
        /**
         * 是否启用客户端指纹校验
         * <p>
         * 开启后，客户端在发送认证请求前会先校验指纹是否在白名单中。
         * 如果指纹不在白名单中，客户端会拒绝发送请求，返回错误信息。
         * </p>
         * <p>
         * 默认关闭，指纹校验由服务端完成。
         * </p>
         */
        private boolean enableClientVerification = false;
        
        /**
         * 指纹配置缓存时间（秒）
         * <p>
         * 客户端会缓存从服务端获取的 appKey 指纹配置，此配置控制缓存的有效期。
         * 默认 1 小时（3600 秒）。
         * </p>
         */
        private long configCacheTimeout = 3600;
        
        /**
         * AppKey 指纹配置映射
         * <p>
         * Key: AppKey
         * Value: 指纹配置（enableFingerprint: 0-不启用, 1-启用; fingerprintWhitelist: 指纹白名单，多个用逗号分隔）
         * </p>
         * <p>
         * 配置示例：
         * <pre>
         * plugin:
         *   oauth:
         *     fingerprint:
         *       appKeyConfigs:
         *         "appKey1":
         *           enableFingerprint: 1
         *           fingerprintWhitelist: "fingerprint1,fingerprint2"
         *         "appKey2":
         *           enableFingerprint: 0
         * </pre>
         * </p>
         */
        private java.util.Map<String, AppKeyFingerprintConfig> appKeyConfigs = new java.util.HashMap<>();
    }
    
    /**
     * AppKey 指纹配置
     */
    @Data
    public static class AppKeyFingerprintConfig {
        /**
         * 是否启用指纹检测
         * <p>
         * 0: 不启用, 1: 启用
         * </p>
         */
        private Integer enableFingerprint = 0;
        
        /**
         * 指纹白名单（多个指纹用逗号分隔）
         */
        private String fingerprintWhitelist;
    }

    /**
     * 重试配置
     */
    @Data
    public static class RetryConfig {
        /**
         * 是否启用重试
         */
        private boolean enabled = true;

        /**
         * 最大重试次数
         */
        private int maxAttempts = 3;

        /**
         * 重试间隔（毫秒）
         */
        private long delay = 1000;

        /**
         * 重试间隔倍数（指数退避）
         */
        private double multiplier = 1.5;

        /**
         * 最大重试间隔（毫秒）
         */
        private long maxDelay = 5000;
    }

    /**
     * 熔断配置
     */
    @Data
    public static class CircuitBreakerConfig {
        /**
         * 是否启用熔断
         */
        private boolean enabled = true;

        /**
         * 失败率阈值（百分比），超过则触发熔断
         */
        private int failureRateThreshold = 50;

        /**
         * 慢调用率阈值（百分比）
         */
        private int slowCallRateThreshold = 80;

        /**
         * 慢调用时间阈值（毫秒）
         */
        private long slowCallDurationThreshold = 3000;

        /**
         * 滑动窗口大小
         */
        private int slidingWindowSize = 10;

        /**
         * 熔断器打开后等待时间（秒）
         */
        private int waitDurationInOpenState = 30;

        /**
         * 半开状态允许的请求数
         */
        private int permittedCallsInHalfOpenState = 5;

        /**
         * 降级响应消息
         */
        private String fallbackMessage = "认证服务暂时不可用，请稍后重试";
    }

    /**
     * 限流配置类
     * <p>
     * 配置前缀：<code>plugin.oauth.rate-limit</code>。
     * </p>
     */
    @Data
    public static class RateLimit {

        /**
         * 是否启用 OAuth 限流（总开关）
         * <p>
         * 默认值：false
         * </p>
         */
        private boolean enable = false;

        /**
         * 登录限流配置
         */
        private LoginLimit login = new LoginLimit();

        /**
         * 全局接口限流配置
         */
        private ApiLimit api = new ApiLimit();

        /**
         * 登录限流配置类（防暴力破解）
         */
        @Data
        public static class LoginLimit {

            /**
             * 是否启用登录限流
             * <p>
             * 默认值：true（在总开关 enable=true 前提下）
             * </p>
             */
            private boolean enable = true;

            /**
             * 同一 IP 窗口内最大登录尝试次数
             * <p>
             * 默认值：30 次
             * </p>
             */
            private int ipLimit = 30;

            /**
             * 登录 IP 限流窗口（秒）
             * <p>
             * 默认值：60 秒
             * </p>
             */
            private long ipWindowSeconds = 60;

            /**
             * 同一账号失败多少次后锁定
             * <p>
             * 默认值：5 次
             * </p>
             */
            private int accountFailLimit = 5;

            /**
             * 账号锁定时间（分钟）
             * <p>
             * 默认值：5 分钟
             * </p>
             */
            private long accountLockMinutes = 5;
        }

        /**
         * 全局接口限流配置类（防刷/防 DoS）
         */
        @Data
        public static class ApiLimit {

            /**
             * 是否启用全局接口限流
             * <p>
             * 默认值：true（在总开关 enable=true 前提下）
             * </p>
             */
            private boolean enable = true;

            /**
             * 同一 IP 窗口内最大请求次数（QPS）
             * <p>
             * 默认值：200 次/秒
             * </p>
             */
            private int qps = 200;

            /**
             * 全局接口限流窗口（秒）
             * <p>
             * 默认值：1 秒
             * </p>
             */
            private long windowSeconds = 1;
        }
    }
}
