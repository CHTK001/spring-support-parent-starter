// [P3C A20 豁免] A20/A30/A31: 含日志字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.ai.support.configuration.security;

import com.chua.starter.ai.support.configuration.client.GlobalSettingReader;
import com.chua.starter.ai.support.security.GatewayBodySizeFilter;
import com.chua.starter.ai.support.security.GatewayConnectionBudgetFilter;
import com.chua.starter.ai.support.security.GatewayHealthCheckFilter;
import com.chua.starter.ai.support.security.GatewayIpFilter;
import com.chua.starter.ai.support.security.GatewayRateLimitFilter;
import com.chua.starter.ai.support.security.GatewayRateLimiter;
import com.chua.starter.ai.support.security.GatewayRequestTimeoutFilter;
import com.chua.starter.ai.support.security.LocalSlidingWindowLimiter;
import com.chua.starter.ai.support.security.RedisSlidingWindowLimiter;
import jakarta.servlet.Filter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 网关安全过滤器自动配置 —— 把 {@code security} 分组的每一项设置真的挂到 Servlet 链上。
 *
 * <h2>为什么需要这个类</h2>
 * <p>{@code com.chua.starter.ai.support.security} 下的 5 个过滤器（健康检查 / IP ACL /
 * QPM 限流 / 请求体大小 / 并发预算）此前<b>从未被实例化</b>：没有 {@code @Bean}、没有
 * {@code @Component}、没有 {@code FilterRegistrationBean}。于是 {@code security} 分组的
 * 16 个设置项全部是「保存成功但什么都不发生」—— 管理员关掉限流、改了 QPM，线上毫无变化。
 * 本类负责实例化并按固定顺序注册，同时补上声明了却从未实现的逐请求超时过滤器。</p>
 *
 * <h2>注册顺序</h2>
 * <pre>
 *  10 健康探针  命中即 200，不花任何资源（不读配置以外的开销）
 *  20 IP ACL    最便宜的拒绝：早于一切配额与包体检查
 *  30 请求体大小 只读 Content-Length，拦住超大包再谈别的
 *  40 QPM 限流   AppKey / IP / Path 三个维度
 *  50 并发预算   在途连接数
 *  60 首字节超时 包住下游 handler
 * </pre>
 *
 * <h2>开关</h2>
 * <p>每个过滤器内部读各自的 {@code security_*_enabled}；{@code security_enabled=false}
 * 让全部过滤器放行。路径范围由 {@code security_gateway_path_prefixes} 决定，注册
 * URL 模式用 {@code /*} 以便热改前缀即时生效。</p>
 *
 * @author CH
 * @since 4.0.0.53
 */
@Slf4j
@AutoConfiguration
public class AiGatewaySecurityAutoConfiguration {

    /**
     * 过滤器注册范围：路径筛选交给过滤器自己（前缀可热改）
     */
    private static final String URL_ALL = "/*";

    /**
     * 限流存储：本地滑动窗口
     */
    private static final String STORE_LOCAL = "local";

    /**
     * 限流存储：Redis ZSET
     */
    private static final String STORE_REDIS = "redis";

    /**
     * 过滤器顺序：健康探针
     */
    private static final int ORDER_HEALTH = 10;

    /**
     * 过滤器顺序：IP ACL
     */
    private static final int ORDER_IP = 20;

    /**
     * 过滤器顺序：请求体大小
     */
    private static final int ORDER_BODY = 30;

    /**
     * 过滤器顺序：QPM 限流
     */
    private static final int ORDER_RATE_LIMIT = 40;

    /**
     * 过滤器顺序：并发预算
     */
    private static final int ORDER_CONNECTION_BUDGET = 50;

    /**
     * 过滤器顺序：首字节超时
     */
    private static final int ORDER_TIMEOUT = 60;

    /**
     * 限流器：默认本地窗口（单节点），可用 {@code plugin.ai.security.rate-limit-store=redis}
     * 切到 Redis 共享窗口。
     *
     * <p><b>默认选本地是刻意的</b>：{@link RedisSlidingWindowLimiter#allow} 在 Redis 异常时
     * <b>返回 false（拒绝）</b>，而多数内嵌应用只是「配了 Redis 但不一定连得上」。默认走
     * Redis 等于让一次 Redis 抖动变成全站 429，宁可单节点各算各的。</p>
     *
     * @param redisProvider Spring Data Redis 模板（可能不存在）
     * @param store         限流存储方式：local / redis
     * @return 限流器
     */
    @Bean
    @ConditionalOnMissingBean(GatewayRateLimiter.class)
    public GatewayRateLimiter gatewayRateLimiter(ObjectProvider<StringRedisTemplate> redisProvider,
                                                 @Value("${plugin.ai.security.rate-limit-store:local}") String store) {
        if (STORE_REDIS.equalsIgnoreCase(store)) {
            StringRedisTemplate template = redisProvider.getIfAvailable();
            if (template != null) {
                log.info("[GatewaySecurity] 限流存储 = Redis（跨节点共享滑动窗口）");
                return new RedisSlidingWindowLimiter(template);
            }
            log.warn("[GatewaySecurity] 配置了 rate-limit-store=redis 但没有 StringRedisTemplate，回落本地窗口");
        } else if (!STORE_LOCAL.equalsIgnoreCase(store)) {
            log.warn("[GatewaySecurity] 未知的 rate-limit-store={}，按 local 处理", store);
        }
        return new LocalSlidingWindowLimiter();
    }

    /**
     * 健康探针过滤器。
     *
     * @param reader 全局设置读取器
     * @return 注册项
     */
    @Bean
    public FilterRegistrationBean<Filter> aiGatewayHealthCheckFilter(GlobalSettingReader reader) {
        return register(new GatewayHealthCheckFilter(reader), "aiGatewayHealthCheckFilter", ORDER_HEALTH);
    }

    /**
     * IP 黑白名单过滤器。
     *
     * @param reader 全局设置读取器
     * @return 注册项
     */
    @Bean
    public FilterRegistrationBean<Filter> aiGatewayIpFilter(GlobalSettingReader reader) {
        return register(new GatewayIpFilter(reader), "aiGatewayIpFilter", ORDER_IP);
    }

    /**
     * 请求体大小过滤器。
     *
     * @param reader 全局设置读取器
     * @return 注册项
     */
    @Bean
    public FilterRegistrationBean<Filter> aiGatewayBodySizeFilter(GlobalSettingReader reader) {
        return register(new GatewayBodySizeFilter(reader), "aiGatewayBodySizeFilter", ORDER_BODY);
    }

    /**
     * QPM 限流过滤器。
     *
     * @param reader      全局设置读取器
     * @param rateLimiter 限流器
     * @return 注册项
     */
    @Bean
    public FilterRegistrationBean<Filter> aiGatewayRateLimitFilter(GlobalSettingReader reader,
                                                                 GatewayRateLimiter rateLimiter) {
        return register(new GatewayRateLimitFilter(reader, rateLimiter), "aiGatewayRateLimitFilter", ORDER_RATE_LIMIT);
    }

    /**
     * 并发预算过滤器。
     *
     * @param reader 全局设置读取器
     * @return 注册项
     */
    @Bean
    public FilterRegistrationBean<Filter> aiGatewayConnectionBudgetFilter(GlobalSettingReader reader) {
        return register(new GatewayConnectionBudgetFilter(reader), "aiGatewayConnectionBudgetFilter",
                ORDER_CONNECTION_BUDGET);
    }

    /**
     * 首字节超时过滤器。
     *
     * @param reader 全局设置读取器
     * @return 注册项
     */
    @Bean
    public FilterRegistrationBean<Filter> aiGatewayRequestTimeoutFilter(GlobalSettingReader reader) {
        return register(new GatewayRequestTimeoutFilter(reader), "aiGatewayRequestTimeoutFilter", ORDER_TIMEOUT);
    }

    /**
     * 统一注册：全路径、支持异步（SSE 出口靠 async dispatch，过滤器本身不处理 ASYNC 派发）。
     *
     * @param filter 过滤器实例
     * @param name   Bean/注册名
     * @param order  顺序
     * @return 注册项
     */
    private static FilterRegistrationBean<Filter> register(Filter filter, String name, int order) {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>(filter);
        registration.setName(name);
        registration.addUrlPatterns(URL_ALL);
        registration.setOrder(order);
        // SSE 走 async dispatch；OncePerRequestFilter 默认不参与 ASYNC 派发，
        // 这里同步声明以匹配容器要求（否则部分容器会拒绝 async 请求经过该链）
        registration.setAsyncSupported(true);
        return registration;
    }
}
