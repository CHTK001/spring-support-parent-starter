// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.oauth.client.support.properties;

import com.google.common.collect.Sets;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * 认证客户端配置（配置前缀：{@code plugin.oauth}）。
 *
 * <p>由 {@code AuthClientConfiguration} 经 {@code @EnableConfigurationProperties} 装配；
 * {@code ignoreInvalidFields = true} 表示类型不匹配的字段忽略绑定、取默认值，不中断启动。
 * 所有键均为可选，未配置时取字段初始化默认值；各字段的含义、默认值与回落行为见字段注释。</p>
 *
 * <p>完整 YAML 配置示例（含配置前缀与关键项注释）：</p>
 * <pre>
 * plugin:
 *   oauth:
 *     enable: true                           # 总开关，false 时整组认证 Bean 不装配
 *     protocol: http                         # tcp / http / http-lite / oauth2 / rsocket / static
 *     address: http://auth-server:9090       # 认证服务端地址，多实例逗号分隔；不配则远程校验失败
 *     oauth-url: /verify                     # 令牌校验路径
 *     login-address: http://auth-server:9090 # 登录端点地址；缺省取 ${plugin.oauth.auth-address:}
 *     balance: polling                       # 多地址负载均衡策略（LoadBalance SPI 名）
 *     token-name: x-oauth-token              # 令牌请求头名
 *     cookie-name: x-oauth-cookie            # 令牌 Cookie 名
 *     alias: Boren-Token                     # 令牌别名头
 *     encryption: aes                        # 传输加密算法
 *     enable-encryption: true                # 是否启用传输加密
 *     block-address: /*                      # 过滤器拦截地址（urlPatterns），不是白名单
 *     whitelist:                             # 自定义白名单（Ant 风格，只能增加）
 *       - /api/public/**
 *     whitelist-file: config/oauth.whitelist # 外部白名单文件（热更新，# 注释）
 *     use-default-whitelist: true            # 是否启用内置默认白名单
 *     login-page: /login                     # 登录页
 *     login-form-action: /login/form         # 自渲染登录表单提交端点
 *     logout-page: /logout                   # 登出页
 *     temporary-token-page: /temporary-token # 临时令牌页
 *     upgrade-page: /upgrade                 # 升级页
 *     no-permission-page: /oauth-page        # 无权限页
 *     cache-timeout: 21600                   # 认证缓存秒数（默认 6 小时）
 *     cache-hot-cold-backup: true            # 缓存冷热备份
 *     connect-timeout: 5000                  # 连接超时（毫秒）
 *     read-timeout: 10000                    # 读取超时（毫秒）
 *     request-timeout: 15000                 # 请求超时（毫秒）
 *     static-token-expire-seconds: 86400     # 静态账号令牌有效期（秒），夹到 [60, 604800]
 *     local-admin-fallback: false            # 无用户上下文兜底 admin，仅限本地联调
 *     retry:                                 # 重试配置
 *       enabled: true
 *       max-attempts: 3
 *       delay: 1000
 *       multiplier: 1.5
 *       max-delay: 5000
 *     circuit-breaker:                       # 熔断配置
 *       enabled: true
 *       failure-rate-threshold: 50
 *       slow-call-rate-threshold: 80
 *       slow-call-duration-threshold: 3000
 *       sliding-window-size: 10
 *       wait-duration-in-open-state: 30
 *       permitted-calls-in-half-open-state: 5
 *       fallback-message: 认证服务暂时不可用，请稍后重试
 *     rate-limit:                            # 限流配置（总开关默认关闭）
 *       enable: false
 *       login:
 *         enable: true
 *         ip-limit: 30
 *         ip-window-seconds: 60
 *         account-fail-limit: 5
 *         account-lock-minutes: 5
 *       api:
 *         enable: true
 *         qps: 200
 *         window-seconds: 1
 *     key:                                   # AK/SK 二次加密
 *       enabled: false
 *       access-key: your-access-key          # 占位符，请替换
 *       secret-key: change-me                # 占位符，请替换
 *     temp:                                  # 静态账号（联调/内网场景）
 *       open: true                           # 仅作 tempProvider 装配条件，不是绑定字段
 *       menu-path: classpath:menu.json
 *       user: "admin:盐$摘要"                # 默认留空；未配置时静态登录被拒绝
 *     fingerprint:                           # 指纹校验
 *       global-verification: false
 *       enable-client-verification: false
 *       config-cache-timeout: 3600
 *       app-key-configs:
 *         "your-app-key":                    # 占位符
 *           enable-fingerprint: 1
 *           fingerprint-whitelist: "fp-1,fp-2"
 *     ai-app-key:                            # AI AppKey 本地校验
 *       enabled: false
 * </pre>
 *
 * @author CH
 * @since 4.0.0
 */
@Data
@ConfigurationProperties(prefix = AuthClientProperties.PRE, ignoreInvalidFields = true)
public class AuthClientProperties {

    /**
     * 是否启用认证客户端（自动装配总开关）。
     * <p>默认 {@code true}（{@code matchIfMissing = true}，不配置也生效）；显式 {@code false}
     * 时本模块整组认证 Bean 不装配（dev/test profile 下可由 {@code AuthFilterBypassConfiguration}
     * 提供匿名直通过滤器）。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     enable: true   # 默认 true
     * </pre>
     */
    private boolean enable = true;

    /**
     * 是否生成 OAuth 传输报文签名，默认关闭；启用时服务端也须开启签名校验。
     * <p>该无密钥摘要不替代 AK/SK 身份认证或 HTTPS。</p>
     */
    private boolean signEnabled = false;

    /**
     * 权限拦截器是否允许「无用户上下文即本地 admin」兜底
     * <p>该兜底等价于绕过鉴权，仅供没有 oauth 服务端的本地联调显式打开；
     * 默认 {@code false}：取不到用户上下文即拒绝。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     local-admin-fallback: false   # 默认 false；生产环境严禁 true
     * </pre>
     */
    private boolean localAdminFallback = false;

    /**
     * 静态账号（用户名 / 密码）登录令牌的有效期（秒）。
     * <p>历史上硬编码为 7 天，令牌签发后长期有效、后端重启也仍被前端长期缓存；
     * 现缩短为默认 24 小时，实际取值经 {@link #getClampedStaticTokenExpireSeconds()}
     * 夹到 [60, 7天=604800] 区间：配置 ≤60 按 60 秒生效，≥604800 按 7 天生效。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     static-token-expire-seconds: 86400   # 默认 86400（24 小时）
     * </pre>
     */
    private long staticTokenExpireSeconds = 24L * 60 * 60;

    /**
     * 获取静态令牌有效期（秒），夹到 [60, 7天]，避免误配成超长或立即失效。
     *
     * @return 有效期秒数
     */
    public long getClampedStaticTokenExpireSeconds() {
        long max = 7L * 24 * 60 * 60;
        long v = staticTokenExpireSeconds;
        if (v < 60) {
            v = 60;
        }
        if (v > max) {
            v = max;
        }
        return v;
    }

    /**
     * 配置前缀：{@code plugin.oauth}。
     */
    public static final String PRE = "plugin.oauth";

    /**
     * 登录链路必须放行的路径（<b>不受任何配置影响</b>）
     *
     * <p>登录与鉴权存在循环依赖：不登录拿不到 token，没有 token 就过不了鉴权。
     * 所以登录链路一旦被收紧成「需要 token」，服务就自锁 —— 现象是登录接口返回
     * 403，日志为「未匹配任何白名单规则，需要认证」。这不是某个应用的特殊需求，
     * 而是鉴权组件自身的必备豁免，因此 {@link #useDefaultWhitelist} 与 yaml 清单
     * 都无权把它关掉（{@code plugin.oauth.whitelist} 只能增加，不能减少）。</p>
     *
     * <p>注：Ant 语义下 {@code /**&#47;login} <b>不匹配</b> {@code /login/form}，
     * 故必须单列深层条目 {@code /**&#47;login/**}，否则自渲染登录表单 POST 会被拦回
     * 登录页（登录页自锁，现象是点登录无限回到登录页）。</p>
     */
    private static final List<String> ALWAYS_WHITELIST = List.of(
            "/login",
            "/**/login",
            "/**/login/**",
            "/**/doLogin",
            "/**/captcha",
            "/**/users/loginCode"
    );

    /**
     * 内置默认白名单（可被 {@code use-default-whitelist: false} 关闭）
     *
     * <p><b>修复</b>：原清单里混入了下列「别的应用专用」的路径，而本 starter 是被所有
     * 服务共用的 —— 任何应用只要恰好注册了同名路由，就会被静默地关闭鉴权。这类
     * 默认值比不写更危险：它看起来是「框架统一放行的安全路径」，实际是攻击面。
     * 已从默认值中移除，需要的应用请在自己的 {@code plugin.oauth.whitelist} 里显式声明
     * （这样「谁放行了什么」是可见的，而不是藏在依赖的默认值里）：</p>
     * <ul>
     *   <li>{@code /api/providers/} 下任意深度的 {@code credentials} 子路径 ——
     *       <b>供应商凭据</b>。把「读密钥」的路径写进免鉴权清单，任何实现该路由的
     *       服务都会直接泄露凭据。绝不能作为默认值。</li>
     *   <li>{@code /api/auth/user} —— 当前登录用户信息，未登录也返回即身份信息泄露。</li>
     *   <li>{@code /api/settings/client} —— 客户端配置。</li>
     *   <li>{@code /v1/models}、{@code /v1/chat/**}、{@code /v1/completions}、
     *       {@code /v1/embeddings}、{@code /v1/cc/**} —— AI 协议端点。<b>这些端点的凭证是
     *       业务 AppKey（{@code sys_ai_app_key}），不是登录令牌</b>；把它们写进免鉴权清单，
     *       等于让所有使用本 starter 的服务在 AI 路由上完全匿名，AppKey 校验形同虚设。
     *       正确做法是由各应用在自己的 {@code plugin.oauth.whitelist} 中显式声明
     *       （如 {@code /v2/v1/**}）并把 AppKey 校验交给控制器 / 服务实例端口，
     *       或开启 {@code plugin.oauth.ai-app-key.enabled} 让鉴权层直接校验 AppKey。</li>
     * </ul>
     *
     * <p>保留 {@code /v1/sys/setting} 是有意的：它正是前端启动时拉取远程配置的入口，
     * 必须匿名可访问。它不构成用户数据泄露，但「匿名可写的配置注入」风险由前端侧
     * 的配置键白名单（见 vue-support-parent-starter 的 config/setting 校验）承担。</p>
     */
    private static final List<String> DEFAULT_WHITELIST = List.of(
            "/**/oauth",
            "/**/logout",
            "/logout",
            "/doc.html",
            "/actuator",
            "/actuator/**",
            "/report",
            "/webjars/**",
            "/markdown/**",
            "/v1/sys/setting",
            "/static/**",
            "/v1/file/**",
            "/**/*.html",
            "/v3/api-docs/**",
            "/**/node/receive_push",
            "/api/system/info",
            "/api/auth/status",
              "/api/extensions/**",
              "/api/workshop/**",
              "/v2/usage/**",
              "/v2/logger/ingest",
              "/v2/logger/sdk.js",
             "/api/terminals",
            "/api/terminals/**"
    );

    /**
     * 是否启用框架内置默认白名单
     * <p>内置清单混有历史应用专用路径（如 /api/extensions/**、/v2/usage/**），
     * 新应用需要按自身路由收紧时置 false，此时仅 yaml 与动态白名单生效。</p>
     * <p><b>注意</b>：关闭它<b>不会</b>影响 {@link #ALWAYS_WHITELIST} —— 登录链路
     * 是鉴权组件自身的必备豁免，关掉会让服务自锁（登录 403）。</p>
     * 默认 {@code true}，保持既有应用行为不变。
     * <pre>
     * plugin:
     *   oauth:
     *     use-default-whitelist: true   # 默认 true；按自身路由收紧时置 false
     * </pre>
     */
    private boolean useDefaultWhitelist = true;

    /**
     * 创建 认证客户端属性 实例
    */
    public AuthClientProperties() {
    }

    /**
     * 获取完整白名单（登录链路必放行 + 内置默认 + yaml 自定义 + 动态添加）
     *
     * <p>{@link #ALWAYS_WHITELIST} 永远排在最前且无法通过配置移除：登录接口必须能
     * 匿名访问，否则「拿 token」这件事本身需要 token，形成死循环。</p>
     *
     * @return 获取effectivewhitelist的结果
     */
    public List<String> getEffectiveWhitelist() {
        List<String> effective = new java.util.ArrayList<>(ALWAYS_WHITELIST);
        if (useDefaultWhitelist) {
            effective.addAll(DEFAULT_WHITELIST);
        }
        if (whitelist != null) {
            effective.addAll(whitelist);
        }
        effective.addAll(dynamicWhitelist);
        return effective;
    }

    /**
     * 动态白名单（运行时可增删，线程安全）。
     * <p>不通过 YAML 绑定：初始为空集，由运行时经 {@link #addWhitelist(String)} /
     * {@link #removeWhitelist(String)} / {@link #clearDynamicWhitelist()} 维护，
     * 并参与 {@link #getEffectiveWhitelist()} 汇总。</p>
     */
    private final java.util.Set<String> dynamicWhitelist = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 动态添加白名单规则
     *
     * @param pattern URL 模式，支持 Ant 风格，例如 /v2/face/**
     */
    public void addWhitelist(String pattern) {
        if (null != pattern && !pattern.isBlank()) {
            dynamicWhitelist.add(pattern.trim());
        }
    }

    /**
     * 动态删除白名单规则
     *
     * @param pattern 已添加的 URL 模式
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
     * 连接认证服务端超时时间（毫秒）。
     * <p>默认 {@code 5000}。本模块未对 ≤0 做下限夹取，配置 ≤0 时按原值交给底层传输实现
     * 处理，建议保持正数。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     connect-timeout: 5000   # 默认 5000 毫秒
     * </pre>
     */
    private long connectTimeout = 5000;

    /**
     * 读取认证服务端响应超时时间（毫秒）。
     * <p>默认 {@code 10000}。本模块未对 ≤0 做下限夹取，配置 ≤0 时按原值交给底层传输实现
     * 处理，建议保持正数。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     read-timeout: 10000   # 默认 10000 毫秒
     * </pre>
     */
    private long readTimeout = 10000;

    /**
     * 整体请求超时时间（毫秒）。
     * <p>默认 {@code 15000}。本模块未对 ≤0 做下限夹取，配置 ≤0 时按原值交给底层传输实现
     * 处理，建议保持正数。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     request-timeout: 15000   # 默认 15000 毫秒
     * </pre>
     */
    private long requestTimeout = 15000;

    /**
     * 重试配置（{@code plugin.oauth.retry.*}）。
     * <p>默认 {@link RetryConfig}：启用，最多 3 次，首次间隔 1000 毫秒，
     * 指数退避倍数 1.5，最大间隔 5000 毫秒。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     retry:
     *       enabled: true      # 默认 true
     *       max-attempts: 3    # 默认 3
     *       delay: 1000        # 默认 1000 毫秒
     *       multiplier: 1.5    # 默认 1.5
     *       max-delay: 5000    # 默认 5000 毫秒
     * </pre>
     */
    private RetryConfig retry = new RetryConfig();

    /**
     * 熔断配置（{@code plugin.oauth.circuit-breaker.*}）。
     * <p>默认 {@link CircuitBreakerConfig}：启用，失败率阈值 50%，慢调用率阈值 80%，
     * 慢调用阈值 3000 毫秒，滑动窗口 10，熔断等待 30 秒，半开探测 5 次。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     circuit-breaker:
     *       enabled: true
     *       failure-rate-threshold: 50
     *       slow-call-rate-threshold: 80
     *       slow-call-duration-threshold: 3000
     *       sliding-window-size: 10
     *       wait-duration-in-open-state: 30
     *       permitted-calls-in-half-open-state: 5
     *       fallback-message: 认证服务暂时不可用，请稍后重试
     * </pre>
     */
    private CircuitBreakerConfig circuitBreaker = new CircuitBreakerConfig();

    /**
     * 限流配置（{@code plugin.oauth.rate-limit.*}）。
     * <p>
     * OAuth 客户端限流能力，支持接口 QPS 限流与登录防暴力破解限流。
     * 总开关 {@code rate-limit.enable} 默认 {@code false}（关闭）；
     * 登录 / 接口子开关默认均为 {@code true}，打开总开关即生效。
     * </p>
     * <pre>
     * plugin:
     *   oauth:
     *     rate-limit:
     *       enable: false            # 总开关，默认 false
     *       login:
     *         enable: true           # 登录限流（防暴破），默认 true
     *         ip-limit: 30           # 同一 IP 窗口内最大登录尝试次数
     *         ip-window-seconds: 60  # 登录 IP 限流窗口（秒）
     *         account-fail-limit: 5  # 同一账号失败锁定阈值（次）
     *         account-lock-minutes: 5 # 账号锁定时间（分钟）
     *       api:
     *         enable: true           # 全局接口限流，默认 true
     *         qps: 200               # 同一 IP 每窗口最大请求数
     *         window-seconds: 1      # 接口限流窗口（秒）
     * </pre>
     */
    private RateLimit rateLimit = new RateLimit();

    /**
     * 客户端缓存鉴权信息的有效期（秒），用于提高访问效率。
     * <p>默认 {@code 21600}（6 小时）；该值直接作为认证缓存 TTL 传入 {@code CacheProvider}
     * （见 {@code AbstractProtocol}）。缓存窗口内权限变更 / 踢人不立即生效，
     * 可调用 {@code AbstractProtocol#invalidateCache} / {@code #revokeToken} 主动失效。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     cache-timeout: 21600   # 默认 21600 秒（6 小时）
     * </pre>
     */
    private long cacheTimeout =  6 * 60 * 60;
    /**
     * 是否开启客户端缓存冷热备份。
     * <p>默认 {@code true}。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     cache-hot-cold-backup: true   # 默认 true
     * </pre>
     */
    private boolean cacheHotColdBackup = true;
    /**
     * 认证服务端地址，多个实例用英文逗号分隔（配合 {@link #balance} 做客户端负载均衡）。
     * <p>默认无（{@code null}）：远程校验时取不到地址会告警「鉴权地址不存在」并返回
     * {@code null}，令牌校验随之失败 —— 接入真实认证服务端前必须配置。
     * 支持 <code>${...}</code> 占位符（经 {@code OauthClientRuntimeContext#resolvePlaceholders} 解析）。</p>
     * <p><b>发现服务优先</b>：值写成 {@code discovery://<服务名>}（或裸服务名，即不含协议/冒号/逗号的单值）时，
     * 先由 {@code DiscoveryService} 解析出实例地址列表；解析不到再回落本值。服务端扩缩容/换址因此不必改配置。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     address: http://auth-server:9090,http://auth-server-backup:9090
     *     # 或走发现中心（服务名与认证服务端注册时使用的名称一致）
     *     # address: discovery://auth-server
     * </pre>
     */
    private String address;
    /**
     * 登录端点地址。
     * <p>默认 <code>${plugin.oauth.auth-address:}</code>（占位符：优先取系统属性 / 环境变量
     * {@code plugin.oauth.auth-address}，取不到则解析为空串）。为空时
     * {@code WebRequest#isFailure()} 直接判定鉴权失败 —— 依赖登录端点的场景必须显式配置。</p>
     * <p>与 {@link #address} 同口径支持 {@code discovery://<服务名>}：登录页跳转地址按发现中心解析。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     login-address: http://auth-server:9090
     * </pre>
     */
    private String loginAddress = "${plugin.oauth.auth-address:}";
    /**
     * 传输加密算法。
     * <p>默认 {@code aes}。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     encryption: aes   # 默认 aes
     * </pre>
     */
    private String encryption = "aes";

    /**
     * 是否启用数据（传输）加密。
     * <p>默认 {@code true}。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     enable-encryption: true   # 默认 true
     * </pre>
     */
    private boolean enableEncryption = true;

    /**
     * 过滤器拦截地址（Servlet urlPatterns，Ant 风格），决定「拦谁」，与白名单是两套机制。
     * <p>默认 {@code /*}（拦截所有请求）；配置为空集时过滤器不拦任何路径。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     block-address: /*    # 默认 /*；多值可用列表形式
     * </pre>
     */
    private Set<String> blockAddress = Sets.newHashSet("/*");

    /**
     * 自定义白名单（Ant 风格），叠加在 ALWAYS / DEFAULT / 动态白名单之上；
     * 只能增加放行范围，不能移除 ALWAYS 登录链路豁免。
     * <p>默认空列表。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     whitelist:
     *       - /api/public/**
     *       - /v2/v1/**
     * </pre>
     */
    private List<String> whitelist = new LinkedList<>();

    /**
     * 外部白名单文件路径
     * <p>
     * 支持动态加载和热更新，文件变更后自动重新加载。
     * 文件格式：每行一个URL 模式，支持 # 注释。
     * 默认 {@code config/oauth.whitelist}。
     * </p>
     * <pre>
     * plugin:
     *   oauth:
     *     whitelist-file: config/oauth.whitelist   # 默认该路径
     * </pre>
     */
    private String whitelistFile = "config/oauth.whitelist";

    /**
     * 协议类型。
     * <p>
     * 可选值（{@code Protocol} SPI 名，不区分大小写匹配）：
     * <ul>
     * <li><strong>tcp</strong> - TCP协议，基于原生 Socket，默认</li>
     * <li><strong>http</strong> / <strong>http-lite</strong> - HTTP协议，基于Unirest，同步实现</li>
     * <li><strong>oauth2</strong> - OAuth2 协议</li>
     * <li><strong>rsocket</strong> - RSocket 协议</li>
     * <li><strong>static</strong> - 静态协议，用于测试</li>
     * </ul>
     * 默认 {@code tcp}。
     * </p>
     * <pre>
     * plugin:
     *   oauth:
     *     protocol: http   # 默认 tcp
     * </pre>
     */
    private String protocol = "tcp";
    /**
     * 令牌请求头名称（同时兼容 {@code Authorization: Bearer}）。
     * <p>默认 {@code x-oauth-token}。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     token-name: x-oauth-token   # 默认 x-oauth-token
     * </pre>
     */
    private String tokenName = "x-oauth-token";
    /**
     * 令牌 Cookie 名称。
     * <p>默认 {@code x-oauth-cookie}。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     cookie-name: x-oauth-cookie   # 默认 x-oauth-cookie
     * </pre>
     */
    private String cookieName = "x-oauth-cookie";
    /**
     * 令牌别名请求头。
     * <p>默认 {@code Boren-Token}。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     alias: Boren-Token   # 默认 Boren-Token
     * </pre>
     */
    private String alias = "Boren-Token";
    /**
     * 登录页路径。
     * <p>默认 {@code /login}。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     login-page: /login   # 默认 /login
     * </pre>
     */
    private String loginPage = "/login";
    /**
     * 登出页路径。
     * <p>默认 {@code /logout}。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     logout-page: /logout   # 默认 /logout
     * </pre>
     */
    private String logoutPage = "/logout";
    /**
     * 自渲染登录表单提交端点（表单编码 POST）。
     *
     * <p>浏览器原生表单只能发 {@code application/x-www-form-urlencoded}，
     * {@code /v1/users/login} 只收 JSON，表单投递到这里完成
     * 「验参 + 调协议 + 写 Cookie + 302 回跳」。默认值挂在 loginPage 之下。
     * 注意 Ant 语义：{@code /**&#47;login} <b>不匹配</b> {@code /login/form}，
     * 因此该路径必须显式出现在白名单（见 DEFAULT_WHITELIST 的深层条目），否则
     * 表单提交会被鉴权拦回登录页自锁。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     login-form-action: /login/form   # 默认 /login/form
     * </pre>
     */
    private String loginFormAction = "/login/form";
    /**
     * 临时令牌页路径。
     * <p>默认 {@code /temporary-token}。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     temporary-token-page: /temporary-token   # 默认 /temporary-token
     * </pre>
     */
    private String temporaryTokenPage = "/temporary-token";
    /**
     * 升级页路径。
     * <p>默认 {@code /upgrade}。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     upgrade-page: /upgrade   # 默认 /upgrade
     * </pre>
     */
    private String upgradePage = "/upgrade";
    /**
     * 令牌校验路径（拼接在 {@link #address} 之后）。
     * <p>默认 {@code /verify}。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     oauth-url: /verify   # 默认 /verify
     * </pre>
     */
    private String oauthUrl = "/verify";
    /**
     * 多认证服务端地址的负载均衡模式（{@code LoadBalance} SPI 名）。
     * <p>默认 {@code polling}（轮询）。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     balance: polling   # 默认 polling
     * </pre>
     */
    private String balance = "polling";

    /**
     * 无权限页面路径。
     * <p>默认 {@code /oauth-page}。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     no-permission-page: /oauth-page   # 默认 /oauth-page
     * </pre>
     */
    private String noPermissionPage = "/oauth-page";

    /**
     * AK/SK 密钥配置（{@code plugin.oauth.key.*}）。
     * <p>用于对请求数据进行二次加密，提高安全性；默认关闭。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     key:
     *       enabled: false                # 默认 false
     *       access-key: your-access-key   # 占位符，请替换
     *       secret-key: change-me         # 占位符，请替换
     * </pre>
     */
    private Aksk key = new Aksk();
    /**
     * 临时用户（静态账号）配置（{@code plugin.oauth.temp.*}）。
     * <p>注意：{@code plugin.oauth.temp.open} 不是本类绑定字段，仅作为
     * {@code UserStatisticProvider} 的装配条件（须显式 {@code true} 才装配本地登录端点）。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     temp:
     *       open: true                       # 装配条件，非绑定字段
     *       menu-path: classpath:menu.json   # 菜单文件，默认无
     *       user: "admin:盐$摘要"            # 默认留空；未配置时静态登录被拒绝
     * </pre>
     */
    private TempUser temp = new TempUser();

    /**
     * 指纹验证配置（{@code plugin.oauth.fingerprint.*}）。
     * <p>用于配置浏览器指纹验证功能；各子项默认关闭 / 空映射。</p>
     * <pre>
     * plugin:
     *   oauth:
     *     fingerprint:
     *       global-verification: false        # 默认 false
     *       enable-client-verification: false # 默认 false
     *       config-cache-timeout: 3600        # 默认 3600 秒
     *       app-key-configs: {}               # 默认空 Map
     * </pre>
     */
    private Fingerprint fingerprint = new Fingerprint();

    /**
     * AI appKey 本地认证配置（{@code plugin.oauth.ai-app-key.*}）。
     * <p>
     * 启用后，OAuth 过滤器对 Bearer 令牌 先做本地 AI appKey 校验。
     * appKey 台账属于 AI 模块，查询通过
     * {@link com.chua.starter.common.support.appkey.AppKeyAuthenticator} 契约由 AI 模块提供，
     * 本模块不再配置表名 / 列名，也不直连 AI 的持久层。
     * 默认 {@code false}；开启但容器内无 {@code AppKeyAuthenticator} 实现时该开关静默无效。
     * </p>
     * <pre>
     * plugin:
     *   oauth:
     *     ai-app-key:
     *       enabled: false   # 默认 false
     * </pre>
     */
    private AiAppKey aiAppKey = new AiAppKey();

    /**
     * AI appKey 本地认证配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class AiAppKey {

        /**
         * 是否启用 AI appKey 本地认证。
         * <p>默认 {@code false}。开启后还需容器内存在 AI 模块提供的
         * {@code AppKeyAuthenticator} 实现，否则静默无效（继续走认证协议）。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     ai-app-key:
         *       enabled: false   # 默认 false
         * </pre>
         */
        private boolean enabled = false;
    }


    /**
     * AK/SK 密钥配置
     * <p>
     * 用于对请求数据进行二次加密，提高安全性
     * </p>
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class Aksk {
        /**
         * 是否启用AK/SK加密。
         * <p>默认 {@code false}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     key:
         *       enabled: false   # 默认 false
         * </pre>
         */
        private boolean enabled = false;
        /**
         * accessKey (用于标识客户端)。
         * <p>默认无（{@code null}）；启用 AK/SK 加密时必配。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     key:
         *       access-key: your-access-key   # 占位符，请替换
         * </pre>
         */
        private String accessKey;
        /**
         * secretKey (用于加密数据)。
         * <p>默认无（{@code null}）；启用 AK/SK 加密时必配，建议经环境变量 / 配置中心注入，
         * 不要把真实值提交进仓库。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     key:
         *       secret-key: change-me   # 占位符，请替换
         * </pre>
         */
        private String secretKey;
    }

    @Data
    /**
     * 临时用户（静态账号）配置类。
     *
     * @author CH
     * @since 4.0.0
     */
    public static class TempUser {

        /**
         * 菜单文件路径。
         * <p>默认无（{@code null}）。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     temp:
         *       menu-path: classpath:menu.json
         * </pre>
         */
        private String menuPath;
        /**
         * 临时账号配置（只用于账号类型为 Embed）。
         *
         * <p>[安全警告] 生产环境禁止启用临时账号功能！</p>
         *
         * <p>格式：{@code 用户名:口令;用户名2:口令2}。口令支持三种写法，按序判定：</p>
         * <ol>
         *   <li><b>加盐摘要</b>（{@code 用户名:<盐>$<MD5(盐+明文口令)>}，推荐）：
         *       配置里不出现明文，且每账号盐不同，一张彩虹表无法批量比对。
         *       盐是公开的——它的价值正在于此，详见
         *       {@link com.chua.starter.oauth.client.support.user.SaltedPassword}</li>
         *   <li>明文（历史兼容）</li>
         *   <li>明文的 MD5（历史兼容，前端可只提交摘要）</li>
         * </ol>
         *
         * <p><b>默认值刻意留空</b>：早期这里硬编码了 {@code admin:admin@123!456}，
         * 那等于把一个可登录的弱口令提交进了版本库——任何拿到源码或镜像的人
         * 都能直接登录生产后台，而配置是否被覆盖要等到运行时看日志才知道。
         * 现在默认无账号，必须由部署方显式配置 {@code plugin.oauth.temp.user}，
         * 并建议以加盐摘要形式传入；未配置时静态登录无可用账号，
         * 登录会被明确拒绝，而不是放行一个默认口令。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     temp:
         *       user: "admin:盐$MD5摘要"   # 默认留空；推荐加盐摘要，禁止明文口令入库
         * </pre>
         */
        private String user = "";
    }

    /**
     * 指纹验证配置
     * <p>
     * 用于配置浏览器指纹验证功能
     * </p>
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class Fingerprint {
        /**
         * 是否开启全局指纹验证
         * <p>
         * 开启后，所有需要认证的接口都会验证浏览器指纹，
         * 而不仅仅是标记了 {@code @VerifyFingerprint} 注解的接口。
         * </p>
         * <p>
         * 默认关闭（{@code false}），只有标记了 {@code @VerifyFingerprint} 注解的接口才会验证指纹。
         * </p>
         * <pre>
         * plugin:
         *   oauth:
         *     fingerprint:
         *       global-verification: false   # 默认 false
         * </pre>
         */
        private boolean globalVerification = false;
        
        /**
         * 是否启用客户端指纹校验
         * <p>
         * 开启后，客户端在发送认证请求前会先校验指纹是否在白名单中。
         * 如果指纹不在白名单中，客户端会拒绝发送请求，返回错误信息。
         * </p>
         * <p>
         * 默认关闭（{@code false}），指纹校验由服务端完成。
         * </p>
         * <pre>
         * plugin:
         *   oauth:
         *     fingerprint:
         *       enable-client-verification: false   # 默认 false
         * </pre>
         */
        private boolean enableClientVerification = false;
        
        /**
         * 指纹配置缓存时间（秒）
         * <p>
         * 客户端会缓存从服务端获取的 appKey 指纹配置，此配置控制缓存的有效期。
         * 默认 1 小时（{@code 3600} 秒）。
         * </p>
         * <pre>
         * plugin:
         *   oauth:
         *     fingerprint:
         *       config-cache-timeout: 3600   # 默认 3600 秒
         * </pre>
         */
        private long configCacheTimeout = 3600;
        
        /**
         * appKey 指纹配置映射
         * <p>
         * Key: appKey
         * 值: 指纹配置（enableFingerprint: 0-不启用, 1-启用; fingerprintWhitelist: 指纹白名单，多个用逗号分隔）
         * </p>
         * <p>
         * 默认空 Map。
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
     * appKey 指纹配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class AppKeyFingerprintConfig {
        /**
         * 是否启用指纹检测
         * <p>
         * 0: 不启用, 1: 启用，默认 0。
         * </p>
         * <pre>
         * plugin:
         *   oauth:
         *     fingerprint:
         *       app-key-configs:
         *         "your-app-key":          # 占位符
         *           enable-fingerprint: 1  # 0 不启用 / 1 启用，默认 0
         * </pre>
         */
        private Integer enableFingerprint = 0;
        
        /**
         * 指纹白名单（多个指纹用逗号分隔）。
         * <p>默认无（{@code null}）。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     fingerprint:
         *       app-key-configs:
         *         "your-app-key":                      # 占位符
         *           fingerprint-whitelist: "fp-1,fp-2"
         * </pre>
         */
        private String fingerprintWhitelist;
    }

    /**
     * 重试配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class RetryConfig {
        /**
         * 是否启用重试。
         * <p>默认 {@code true}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     retry:
         *       enabled: true   # 默认 true
         * </pre>
         */
        private boolean enabled = true;

        /**
         * 最大重试次数。
         * <p>默认 {@code 3}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     retry:
         *       max-attempts: 3   # 默认 3
         * </pre>
         */
        private int maxAttempts = 3;

        /**
         * 首次重试间隔（毫秒）。
         * <p>默认 {@code 1000}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     retry:
         *       delay: 1000   # 默认 1000 毫秒
         * </pre>
         */
        private long delay = 1000;

        /**
         * 重试间隔倍数（指数退避）。
         * <p>默认 {@code 1.5}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     retry:
         *       multiplier: 1.5   # 默认 1.5
         * </pre>
         */
        private double multiplier = 1.5;

        /**
         * 最大重试间隔（毫秒）。
         * <p>默认 {@code 5000}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     retry:
         *       max-delay: 5000   # 默认 5000 毫秒
         * </pre>
         */
        private long maxDelay = 5000;
    }

    /**
     * 熔断配置
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class CircuitBreakerConfig {
        /**
         * 是否启用熔断。
         * <p>默认 {@code true}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     circuit-breaker:
         *       enabled: true   # 默认 true
         * </pre>
         */
        private boolean enabled = true;

        /**
         * 失败率阈值（百分比），超过则触发熔断。
         * <p>默认 {@code 50}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     circuit-breaker:
         *       failure-rate-threshold: 50   # 默认 50
         * </pre>
         */
        private int failureRateThreshold = 50;

        /**
         * 慢调用率阈值（百分比）。
         * <p>默认 {@code 80}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     circuit-breaker:
         *       slow-call-rate-threshold: 80   # 默认 80
         * </pre>
         */
        private int slowCallRateThreshold = 80;

        /**
         * 慢调用时间阈值（毫秒）。
         * <p>默认 {@code 3000}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     circuit-breaker:
         *       slow-call-duration-threshold: 3000   # 默认 3000 毫秒
         * </pre>
         */
        private long slowCallDurationThreshold = 3000;

        /**
         * 滑动窗口大小。
         * <p>默认 {@code 10}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     circuit-breaker:
         *       sliding-window-size: 10   # 默认 10
         * </pre>
         */
        private int slidingWindowSize = 10;

        /**
         * 熔断器打开后等待时间（秒）。
         * <p>默认 {@code 30}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     circuit-breaker:
         *       wait-duration-in-open-state: 30   # 默认 30 秒
         * </pre>
         */
        private int waitDurationInOpenState = 30;

        /**
         * 半开状态允许的请求数。
         * <p>默认 {@code 5}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     circuit-breaker:
         *       permitted-calls-in-half-open-state: 5   # 默认 5
         * </pre>
         */
        private int permittedCallsInHalfOpenState = 5;

        /**
         * 降级响应消息（熔断打开时随「认证服务端异常」一并返回，不会静默放行）。
         * <p>默认 {@code 认证服务暂时不可用，请稍后重试}。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     circuit-breaker:
         *       fallback-message: 认证服务暂时不可用，请稍后重试
         * </pre>
         */
        private String fallbackMessage = "认证服务暂时不可用，请稍后重试";
    }

    /**
     * 限流配置类
     * <p>
     * 配置前缀：<code>plugin.oauth.rate-limit</code>。
     * </p>
     * @author CH
     * @since 4.0.0
     */
    @Data
    public static class RateLimit {

        /**
         * 是否启用 OAuth 限流（总开关）。
         * <p>默认 {@code false}（关闭）；登录 / 接口子开关默认均为 {@code true}，
         * 打开总开关即生效。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     rate-limit:
         *       enable: false   # 默认 false
         * </pre>
         */
        private boolean enable = false;

        /**
         * 登录限流配置（{@code plugin.oauth.rate-limit.login.*}，防暴力破解）。
         * <p>默认 {@link LoginLimit}：启用，同一 IP 30 次 / 60 秒，账号失败 5 次锁 5 分钟。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     rate-limit:
         *       login:
         *         enable: true
         *         ip-limit: 30
         *         ip-window-seconds: 60
         *         account-fail-limit: 5
         *         account-lock-minutes: 5
         * </pre>
         */
        private LoginLimit login = new LoginLimit();

        /**
         * 全局接口限流配置（{@code plugin.oauth.rate-limit.api.*}）。
         * <p>默认 {@link ApiLimit}：启用，同一 IP 200 次 / 1 秒。</p>
         * <pre>
         * plugin:
         *   oauth:
         *     rate-limit:
         *       api:
         *         enable: true
         *         qps: 200
         *         window-seconds: 1
         * </pre>
         */
        private ApiLimit api = new ApiLimit();

        /**
         * 登录限流配置类（防暴力破解）
         * @author CH
         * @since 4.0.0
         */
        @Data
        public static class LoginLimit {

            /**
             * 是否启用登录限流。
             * <p>默认 {@code true}（在总开关 {@code rate-limit.enable=true} 前提下生效）。</p>
             * <pre>
             * plugin:
             *   oauth:
             *     rate-limit:
             *       login:
             *         enable: true   # 默认 true
             * </pre>
             */
            private boolean enable = true;

            /**
             * 同一 IP 窗口内最大登录尝试次数。
             * <p>默认 {@code 30} 次。</p>
             * <pre>
             * plugin:
             *   oauth:
             *     rate-limit:
             *       login:
             *         ip-limit: 30   # 默认 30
             * </pre>
             */
            private int ipLimit = 30;

            /**
             * 登录 IP 限流窗口（秒）。
             * <p>默认 {@code 60} 秒。</p>
             * <pre>
             * plugin:
             *   oauth:
             *     rate-limit:
             *       login:
             *         ip-window-seconds: 60   # 默认 60 秒
             * </pre>
             */
            private long ipWindowSeconds = 60;

            /**
             * 同一账号失败多少次后锁定。
             * <p>默认 {@code 5} 次。</p>
             * <pre>
             * plugin:
             *   oauth:
             *     rate-limit:
             *       login:
             *         account-fail-limit: 5   # 默认 5
             * </pre>
             */
            private int accountFailLimit = 5;

            /**
             * 账号锁定时间（分钟）。
             * <p>默认 {@code 5} 分钟。</p>
             * <pre>
             * plugin:
             *   oauth:
             *     rate-limit:
             *       login:
             *         account-lock-minutes: 5   # 默认 5 分钟
             * </pre>
             */
            private long accountLockMinutes = 5;
        }

        /**
         * 全局接口限流配置类（防刷）
         * @author CH
         * @since 4.0.0
         */
        @Data
        public static class ApiLimit {

            /**
             * 是否启用全局接口限流。
             * <p>默认 {@code true}（在总开关 {@code rate-limit.enable=true} 前提下生效）。</p>
             * <pre>
             * plugin:
             *   oauth:
             *     rate-limit:
             *       api:
             *         enable: true   # 默认 true
             * </pre>
             */
            private boolean enable = true;

            /**
             * 同一 IP 窗口内最大请求次数（QPS）。
             * <p>默认 {@code 200} 次/秒。</p>
             * <pre>
             * plugin:
             *   oauth:
             *     rate-limit:
             *       api:
             *         qps: 200   # 默认 200
             * </pre>
             */
            private int qps = 200;

            /**
             * 全局接口限流窗口（秒）。
             * <p>默认 {@code 1} 秒。</p>
             * <pre>
             * plugin:
             *   oauth:
             *     rate-limit:
             *       api:
             *         window-seconds: 1   # 默认 1 秒
             * </pre>
             */
            private long windowSeconds = 1;
        }
    }
}
