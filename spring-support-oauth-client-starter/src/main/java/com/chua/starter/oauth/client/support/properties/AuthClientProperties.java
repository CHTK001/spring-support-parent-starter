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

    public static final String PRE = "plugin.oauth";

    public AuthClientProperties() {
        whitelist.add("/**/captcha");
        whitelist.add("/**/login");
        whitelist.add("/**/oauth");
        whitelist.add("/**/doLogin");
        whitelist.add("/**/logout");
        whitelist.add("/doc.html");
        whitelist.add("/actuator");
        whitelist.add("/actuator/**");
        whitelist.add("/report");
        whitelist.add("/webjars/**");
        whitelist.add("/markdown/**");
        whitelist.add("/v1/sys/setting");
        whitelist.add("/static/**");
        whitelist.add("/v1/file/**");
        whitelist.add("/**/*.html");
        whitelist.add("/**/users/loginCode");
        whitelist.add("/v3/api-docs/**");
        whitelist.add("/**/node/receive_push");
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
     * <li><strong>armeria</strong> - Armeria协议，基于Netty高性能异步框架，【推荐】</li>
     * <li><strong>http</strong> - HTTP协议，基于Unirest，兼容性好</li>
     * <li><strong>static</strong> - 静态协议，用于测试</li>
     * </ul>
     * </p>
     */
    private String protocol = "armeria";
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
     * 鉴权地址
     */
    private String oauthUrl = "/oauth";
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
     * 密钥
     */
    @Data
    public static class Aksk {
        /**
         * ak
         */
        private String accessKey;
        /**
         * sk
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
         * 【安全警告】生产环境禁止启用临时账号功能！
         * 格式: username:password;username2:password2
         * </p>
         */
        private String user = "admin:admin@123!456";
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
}
