package com.chua.report.client.starter.configuration;

import com.chua.report.client.starter.environment.ReportDiscoveryEnvironment;
import com.chua.report.client.starter.spring.endpoint.MapEndpoint;
import com.chua.report.client.starter.spring.endpoint.ThreadEndpoint;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 自定义端点配置类
 * 用于配置和注册各种监控端点以及请求拦截器
 *
 * @author CH
 * @since 2024/9/13
 */
@ComponentScan("com.chua.report.client.starter.jpom.agent")
@ConditionalOnMissingClass("com.chua.starter.monitor.jpom.JpomApplication")
public class CustomEndpointConfiguration implements WebMvcConfigurer {

    /**
     * MeterRegistry用于注册和管理各种监控指标
     */
    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * 创建ReportDiscoveryEnvironment Bean
     * 用于报告发现环境的配置
     *
     * @return ReportDiscoveryEnvironment实例
     */
    @Bean
    @ConditionalOnMissingBean
    public ReportDiscoveryEnvironment reportDiscoveryEnvironment() {
        return new ReportDiscoveryEnvironment();
    }

    /**
     * 创建MapEndpoint Bean
     * 用于提供内存映射相关信息的端点
     *
     * @return MapEndpoint实例
     */
    @Bean("mapEndpoint-1")
    @ConditionalOnMissingBean
    public MapEndpoint mapEndpoint() {
        return new MapEndpoint();
    }

    /**
     * 创建ThreadEndpoint Bean
     * 用于提供线程相关信息的端点
     *
     * @return ThreadEndpoint实例
     */
    @Bean("threadEndpoint-1")
    @ConditionalOnMissingBean
    public ThreadEndpoint threadEndpoint() {
        return new ThreadEndpoint();
    }

    /**
     * 添加拦截器到注册表中
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UrlMetricsInterceptor(meterRegistry));
    }

    /**
     * URL指标拦截器
     * 用于收集HTTP请求的各种指标，包括请求次数、响应时间等
     */
    public static class UrlMetricsInterceptor implements HandlerInterceptor {

        /**
         * MeterRegistry实例，用于注册监控指标
         */
        private final MeterRegistry registry;

        /**
         * 用于存储URI与计时器样本的映射关系
         * 使用ConcurrentReferenceHashMap以提高并发性能并避免内存泄漏
         */
        private final ConcurrentReferenceHashMap<String, Timer.Sample> timerSamples = new ConcurrentReferenceHashMap<>();

        /**
         * 构造函数
         *
         * @param registry MeterRegistry实例
         */
        public UrlMetricsInterceptor(MeterRegistry registry) {
            this.registry = registry;
        }

        /**
         * 在请求处理之前调用
         * 用于开始计时并记录请求次数
         *
         * @param request  HTTP请求对象
         * @param response HTTP响应对象
         * @param handler  处理器对象
         * @return 是否继续执行后续拦截器和处理器
         */
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String uri = request.getRequestURI();

            // 开始计时并将样本存储到映射中
            timerSamples.put(uri, Timer.start(registry));

            // 记录访问次数
            long startTime = System.currentTimeMillis();
            Counter.builder("http.request.url")
                    .tag("method", request.getMethod()) // HTTP方法标签
                    .tag("path", request.getRequestURI()) // 请求路径标签
                    .tag("status", String.valueOf(response.getStatus())) // 响应状态标签
                    .tag("cost", String.valueOf(System.currentTimeMillis() - startTime)) // 耗时标签
                    .register(registry)
                    .increment();
            return true;
        }

        /**
         * 在请求处理完成之后调用
         * 用于停止计时并记录请求持续时间
         *
         * @param request  HTTP请求对象
         * @param response HTTP响应对象
         * @param handler  处理器对象
         * @param ex       异常对象（如果有的话）
         */
        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
            String uri = request.getRequestURI();
            Timer.Sample sample = timerSamples.get(uri);

            if (sample != null) {
                // 记录最后一次请求耗时
                sample.stop(Timer.builder("http.request.duration.last")
                        .description("最后一次请求持续时间（毫秒）")
                        .tag("uri", uri) // URI标签
                        .tag("method", request.getMethod()) // HTTP方法标签
                        .register(registry));

                // 记录请求持续时间的统计信息（包括百分位数）
                sample.stop(Timer.builder("http.request.duration")
                        .description("请求持续时间（毫秒）")
                        .tag("uri", uri) // URI标签
                        .tag("method", request.getMethod()) // HTTP方法标签
                        .publishPercentiles(0.5, 0.95) // 发布50%和95%百分位数
                        .register(registry));

                // 从映射中移除已处理的样本
                timerSamples.remove(uri);
            }
        }
    }
}
