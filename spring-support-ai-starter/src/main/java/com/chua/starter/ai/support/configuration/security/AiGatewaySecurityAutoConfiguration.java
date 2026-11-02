package com.chua.starter.ai.support.configuration.security;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.configuration.core.AiConfiguration;
import com.chua.starter.ai.support.security.GatewayBodySizeFilter;
import com.chua.starter.ai.support.security.GatewayConnectionBudgetFilter;
import com.chua.starter.ai.support.security.GatewayHealthCheckFilter;
import com.chua.starter.ai.support.security.GatewayIpFilter;
import com.chua.starter.ai.support.security.GatewayRateLimitFilter;
import com.chua.starter.ai.support.security.GatewayRateLimiter;
import com.chua.starter.ai.support.security.GatewayRequestTimeoutFilter;
import com.chua.starter.ai.support.security.LocalSlidingWindowLimiter;
import com.chua.starter.ai.support.security.RedisSlidingWindowLimiter;
import com.chua.starter.ai.support.security.SecuritySettings;
import jakarta.servlet.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 网关安全过滤链自动配置（阶段 3）。
 *
 * <h2>为什么用 FilterRegistrationBean 而不是 @Component / @Bean Filter</h2>
 * <p>Spring Boot 4.1 的后端里<b>没有</b>把裸 {@link Filter} Bean 自动挂到容器上的机制
 * （已用 {@code jar tf spring-boot-4.1.1.jar} 核对：只有
 * {@code ServletContextInitializerBeans} 的 {@code FilterRegistrationBeanAdapter}，
 * 没有旧版 {@code FilterRegistrationBeansPostProcessor}）。裸 Bean 只会被创建、永远不会
 * 被调用 —— 一个「配置都写了、代码在跑、就是不生效」的隐患。所以统一用
 * {@link FilterRegistrationBean} 显式声明 URL 规则与顺序。</p>
 *
 * <h2>顺序（Spring 语义：值越小越外层，先执行）</h2>
 * <pre>
 * 1 health  探针短路，最外层，不进任何后续过滤器
 * 2 timeout 逐请求超时，包住后面整条链（含慢速上传）
 * 3 ip      IP ACL（尽早拒掉恶意来源）
 * 4 rate    QPM 限流（拒掉超额流量，省下后面的预算）
 * 5 budget  在途并发预算
 * 6 body    请求体大小（可能消费正文，必须排在限流/并发之后）
 * </pre>
 *
 * <h2>生效路径</h2>
 * <p>业务过滤器只挂在 {@code /v2/v1/*}（OpenAI 兼容网关）、{@code /v2/v1beta/*} 与
 * {@code /v2/ai/*}（AI 管理出口）；健康检查过滤器挂在 {@code /*}，否则
 * {@code /healthz} 这类探针根本进不来，短路就形同虚设。过滤器内部还会按
 * {@code security_gateway_path_prefixes} 再判一次，两道闸都不会漏。</p>
 *
 * @author CH
 * @since 4.0.0.52
 */
@Slf4j
@AutoConfiguration(after = AiConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(Filter.class)
@ConditionalOnProperty(prefix = "plugin.ai.security", name = "enabled", havingValue = "true", matchIfMissing = true)
public class AiGatewaySecurityAutoConfiguration {

    /**
     * 网关接口的 URL 规则
     */
    private static final List<String> GATEWAY_URL_PATTERNS = List.of("/v2/v1/*", "/v2/v1beta/*", "/v2/ai/*");
    /**
     * 健康检查的 URL 规则：必须是全站，探针才进得来
     */
    private static final List<String> ALL_URL_PATTERNS = List.of("/*");

    /**
     * 过滤器顺序：健康检查
     */
    private static final int ORDER_HEALTH = 1;
    /**
     * 过滤器顺序：逐请求超时
     */
    private static final int ORDER_TIMEOUT = 2;
    /**
     * 过滤器顺序：IP ACL
     */
    private static final int ORDER_IP = 3;
    /**
     * 过滤器顺序：QPM 限流
     */
    private static final int ORDER_RATE_LIMIT = 4;
    /**
     * 过滤器顺序：在途并发
     */
    private static final int ORDER_CONNECTION_BUDGET = 5;
    /**
     * 过滤器顺序：请求体大小
     */
    private static final int ORDER_BODY_SIZE = 6;

    /**
     * 设置快照：所有过滤器共享一份，避免各自建一份本地缓存。
     *
     * @param globalSettingReader 全局设置读取器
     * @return 安全设置快照
     */
    @Bean
    @ConditionalOnMissingBean
    public SecuritySettings gatewaySecuritySettings(GlobalSettingReader globalSettingReader) {
        return new SecuritySettings(globalSettingReader);
    }

    /**
     * QPM 限流器：有 Redis 用 ZSET 滑动窗口（多实例共享预算），否则降级本地 Caffeine 窗口。
     *
     * @param redisTemplateProvider Redis 模板（宿主未启用 Redis 时为空）
     * @return 限流器
     */
    @Bean
    @ConditionalOnMissingBean
    public GatewayRateLimiter gatewayRateLimiter(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        StringRedisTemplate redisTemplate = redisTemplateProvider.getIfAvailable();
        if (redisTemplate == null) {
            log.warn("[AiGatewaySecurity] 未发现 StringRedisTemplate，QPM 限流降级为本地滑动窗口"
                    + "（多实例部署时各节点各自计数，集群总预算等效放大 N 倍）");
            return new LocalSlidingWindowLimiter();
        }
        log.info("[AiGatewaySecurity] QPM 限流使用 Redis ZSET 滑动窗口（多实例共享预算）");
        return new RedisSlidingWindowLimiter(redisTemplate);
    }

    /**
     * 超时看门狗：整机一个守护线程，只做定时回调。
     *
     * <p>用守护线程是因为它是可选护栏，不该在应用关闭时拖住 JVM；bean 名固定，
     * 便于 {@code gatewayRequestTimeoutFilterRegistration} 按名取用，也不会和宿主
     * 自己的 {@link ScheduledExecutorService} 冲突。</p>
     *
     * @return 调度器
     */
    @Bean(name = "gatewayRequestTimeoutWatchdog", destroyMethod = "shutdownNow")
    @ConditionalOnMissingBean(name = "gatewayRequestTimeoutWatchdog")
    public ScheduledExecutorService gatewayRequestTimeoutWatchdog() {
        AtomicInteger index = new AtomicInteger(0);
        return Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "gateway-timeout-watchdog-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 1、健康检查过滤器：探针短路（全站路径）。
     *
     * @param settings 安全设置快照
     * @return 注册器
     */
    @Bean
    public FilterRegistrationBean<GatewayHealthCheckFilter> gatewayHealthCheckFilterRegistration(
            SecuritySettings settings) {
        return register(new GatewayHealthCheckFilter(settings), "gatewayHealthCheckFilter",
                ORDER_HEALTH, ALL_URL_PATTERNS);
    }

    /**
     * 2、逐请求超时过滤器（包住后面整条链）。
     *
     * @param settings 安全设置快照
     * @param watchdog 超时看门狗
     * @return 注册器
     */
    @Bean
    public FilterRegistrationBean<GatewayRequestTimeoutFilter> gatewayRequestTimeoutFilterRegistration(
            SecuritySettings settings, ScheduledExecutorService gatewayRequestTimeoutWatchdog) {
        return register(new GatewayRequestTimeoutFilter(settings, gatewayRequestTimeoutWatchdog),
                "gatewayRequestTimeoutFilter", ORDER_TIMEOUT, GATEWAY_URL_PATTERNS);
    }

    /**
     * 3、IP 黑白名单过滤器。
     *
     * @param settings 安全设置快照
     * @return 注册器
     */
    @Bean
    public FilterRegistrationBean<GatewayIpFilter> gatewayIpFilterRegistration(SecuritySettings settings) {
        return register(new GatewayIpFilter(settings), "gatewayIpFilter", ORDER_IP, GATEWAY_URL_PATTERNS);
    }

    /**
     * 4、QPM 限流过滤器。
     *
     * @param settings    安全设置快照
     * @param rateLimiter 限流器
     * @return 注册器
     */
    @Bean
    public FilterRegistrationBean<GatewayRateLimitFilter> gatewayRateLimitFilterRegistration(
            SecuritySettings settings, GatewayRateLimiter rateLimiter) {
        return register(new GatewayRateLimitFilter(settings, rateLimiter), "gatewayRateLimitFilter",
                ORDER_RATE_LIMIT, GATEWAY_URL_PATTERNS);
    }

    /**
     * 5、在途并发预算过滤器。
     *
     * @param settings 安全设置快照
     * @return 注册器
     */
    @Bean
    public FilterRegistrationBean<GatewayConnectionBudgetFilter> gatewayConnectionBudgetFilterRegistration(
            SecuritySettings settings) {
        return register(new GatewayConnectionBudgetFilter(settings), "gatewayConnectionBudgetFilter",
                ORDER_CONNECTION_BUDGET, GATEWAY_URL_PATTERNS);
    }

    /**
     * 6、请求体大小过滤器。
     *
     * @param settings 安全设置快照
     * @return 注册器
     */
    @Bean
    public FilterRegistrationBean<GatewayBodySizeFilter> gatewayBodySizeFilterRegistration(
            SecuritySettings settings) {
        return register(new GatewayBodySizeFilter(settings), "gatewayBodySizeFilter",
                ORDER_BODY_SIZE, GATEWAY_URL_PATTERNS);
    }

    /**
     * 统一的注册器构造：URL 规则 + 顺序 + 名称。
     *
     * @param filter      过滤器
     * @param name        过滤器名
     * @param order       相对顺序（1 开始）
     * @param urlPatterns URL 规则
     * @param <T>         过滤器类型
     * @return 注册器
     */
    private static <T extends Filter> FilterRegistrationBean<T> register(T filter, String name, int order,
                                                                        List<String> urlPatterns) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setName(name);
        registration.setUrlPatterns(urlPatterns);
        // 用 HIGHEST_PRECEDENCE + n 保证这几个过滤器整体排在最外层，不受宿主过滤器默认顺序干扰
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + order);
        registration.setAsyncSupported(true);
        return registration;
    }
}
