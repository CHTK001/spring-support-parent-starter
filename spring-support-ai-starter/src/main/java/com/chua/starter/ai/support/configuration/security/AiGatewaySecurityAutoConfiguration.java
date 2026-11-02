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
 * <h2>顺序（越小越外层）</h2>
 * <pre>
 * 1 health  探针短路，不进任何后续过滤器
 * 2 ip      IP ACL（最早拒掉恶意来源）
 * 3 rate    QPM 限流（拒掉超额流量）
 * 4 budget  在途并发预算
 * 5 body    请求体大小（可能会消费正文，必须放在限流之后）
 * 6 timeout 逐请求超时（最外层，包住整条链）
 * </pre>
 *
 * <h2>生效路径</h2>
 * <p>{@code /v2/v1/*}（OpenAI 兼容网关）与 {@code /v2/ai/*}（AI 管理出口）。过滤器内部
 * 还会再按 {@code security_gateway_path_prefixes} 做一次精确判定，本配置只负责「别让
 * 它们参与全站请求」——管理后台首页、静态资源不该被限流器碰。</p>
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
     * 过滤器顺序：健康检查
     */
    private static final int ORDER_HEALTH = 1;
    /**
     * 过滤器顺序：IP ACL
     */
    private static final int ORDER_IP = 2;
    /**
     * 过滤器顺序：QPM 限流
     */
    private static final int ORDER_RATE_LIMIT = 3;
    /**
     * 过滤器顺序：在途并发
     */
    private static final int ORDER_CONNECTION_BUDGET = 4;
    /**
     * 过滤器顺序：请求体大小
     */
    private static final int ORDER_BODY_SIZE = 5;
    /**
     * 过滤器顺序：逐请求超时
     */
    private static final int ORDER_TIMEOUT = 6;

    /**
     * 设置快照：所有过滤器共享一份，避免各自建一份缓存
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
                    + "（多实例部署时每个节点各自计数，预算等效放大 N 倍）");
            return new LocalSlidingWindowLimiter();
        }
        log.info("[AiGatewaySecurity] QPM 限流使用 Redis ZSET 滑动窗口（多实例共享预算）");
        return new RedisSlidingWindowLimiter(redisTemplate);
    }

    /**
     * 超时看门狗：整机一个线程，只做定时回调。
     *
     * <p>用守护线程：它是网关的可选护栏，不该在应用关闭时拖住 JVM。取消任务时立即
     * 从队列移除，避免长跑进程堆积已结束请求的定时节点。</p>
     *
     * @return 调度器
     */
    @Bean(name = "gatewayRequestTimeoutWatchdog", destroyMethod = "shutdownNow")
    @ConditionalOnMissingBean(name = "gatewayRequestTimeoutWatchdog")
    public ScheduledExecutorService gatewayRequestTimeoutWatchdog() {
        AtomicInteger index = new AtomicInteger(0);
        return Executors.newScheduledThreadPool(1, runnable -> {
            Thread thread = new Thread(runnable, "gateway-timeout-watchdog-" + index.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * 1、健康检查过滤器：探针短路。
     *
     * @param settings 安全设置快照
     * @return 注册器
     */
    @Bean
    public FilterRegistrationBean<GatewayHealthCheckFilter> gatewayHealthCheckFilterRegistration(
            SecuritySettings settings) {
        return register(new GatewayHealthCheckFilter(settings), "gatewayHealthCheckFilter", ORDER_HEALTH);
    }

    /**
     * 2、IP 黑白名单过滤器。
     *
     * @param settings 安全设置快照
     * @return 注册器
     */
    @Bean
    public FilterRegistrationBean<GatewayIpFilter> gatewayIpFilterRegistration(SecuritySettings settings) {
        return register(new GatewayIpFilter(settings), "gatewayIpFilter", ORDER_IP);
    }

    /**
     * 3、QPM 限流过滤器。
     *
     * @param settings    安全设置快照
     * @param rateLimiter 限流器
     * @return 注册器
     */
    @Bean
    public FilterRegistrationBean<GatewayRateLimitFilter> gatewayRateLimitFilterRegistration(
            SecuritySettings settings, GatewayRateLimiter rateLimiter) {
        return register(new GatewayRateLimitFilter(settings, rateLimiter), "gatewayRateLimitFilter", ORDER_RATE_LIMIT);
    }

    /**
     * 4、在途并发预算过滤器。
     *
     * @param settings 安全设置快照
     * @return 注册器
     */
    @Bean
    public FilterRegistrationBean<GatewayConnectionBudgetFilter> gatewayConnectionBudgetFilterRegistration(
            SecuritySettings settings) {
        return register(new GatewayConnectionBudgetFilter(settings), "gatewayConnectionBudgetFilter",
                ORDER_CONNECTION_BUDGET);
    }

    /**
     * 5、请求体大小过滤器。
     *
     * @param settings 安全设置快照
     * @return 注册器
     */
    @Bean
    public FilterRegistrationBean<GatewayBodySizeFilter> gatewayBodySizeFilterRegistration(
            SecuritySettings settings) {
        return register(new GatewayBodySizeFilter(settings), "gatewayBodySizeFilter", ORDER_BODY_SIZE);
    }

    /**
     * 6、逐请求超时过滤器。
     *
     * @param settings 安全设置快照
     * @param watchdog 超时看门狗
     * @return 注册器
     */
    @Bean
    public FilterRegistrationBean<GatewayRequestTimeoutFilter> gatewayRequestTimeoutFilterRegistration(
            SecuritySettings settings, ScheduledExecutorService gatewayRequestTimeoutWatchdog) {
        return register(new GatewayRequestTimeoutFilter(settings, gatewayRequestTimeoutWatchdog),
                "gatewayRequestTimeoutFilter", ORDER_TIMEOUT);
    }

    /**
     * 统一的注册器构造：URL 规则 + 顺序 + 名称。
     *
     * @param filter 过滤器
     * @param name   过滤器名
     * @param order  顺序
     * @param <T>    过滤器类型
     * @return 注册器
     */
    private static <T extends Filter> FilterRegistrationBean<T> register(T filter, String name, int order) {
        FilterRegistrationBean<T> registration = new FilterRegistrationBean<>(filter);
        registration.setName(name);
        registration.setUrlPatterns(GATEWAY_URL_PATTERNS);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + order);
        return registration;
    }
}
