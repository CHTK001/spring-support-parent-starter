package com.chua.report.client.starter.configuration;

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
 * endpoint配置
 * @author CH
 * @since 2024/9/13
 */
@ComponentScan("com.chua.report.client.starter.jpom.agent")
@ConditionalOnMissingClass("com.chua.starter.monitor.jpom.JpomApplication")
public class CustomEndpointConfiguration implements WebMvcConfigurer {


    @Autowired
    private MeterRegistry meterRegistry;

    /**
     * map
     * @return MapEndpoint
     */
    @Bean("mapEndpoint-1")
    @ConditionalOnMissingBean
    public MapEndpoint mapEndpoint() {
        return new MapEndpoint();
    }
    /**
     * threadEndpoint
     * @return ThreadEndpoint
     */
    @Bean("threadEndpoint-1")
    @ConditionalOnMissingBean
    public ThreadEndpoint threadEndpoint() {
        return new ThreadEndpoint();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new UrlMetricsInterceptor(meterRegistry));
    }


    public static class UrlMetricsInterceptor implements HandlerInterceptor {

        private final MeterRegistry registry;
        private final ConcurrentReferenceHashMap<String, Timer.Sample> timerSamples = new ConcurrentReferenceHashMap<>();

        public UrlMetricsInterceptor(MeterRegistry registry) {
            this.registry = registry;
        }
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String uri = request.getRequestURI();

            // 开始计时
            timerSamples.put(uri, Timer.start(registry));

            // 记录访问次数
            long startTime = System.currentTimeMillis();
            Counter.builder(
                            "http.request.url"
                    )
                    .tag("method", request.getMethod())
                    .tag("path", request.getRequestURI())
                    .tag("status", String.valueOf(response.getStatus()))
                    .tag("cost", String.valueOf(System.currentTimeMillis() - startTime))
                    .register(registry)
                    .increment();
            return true;
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
            String uri = request.getRequestURI();
            Timer.Sample sample = timerSamples.get(uri);

            if (sample != null) {
                // 记录最后一次耗时
                sample.stop(Timer.builder("http.request.duration.last")
                        .description("Last request duration in milliseconds")
                        .tag("uri", uri)
                        .tag("method", request.getMethod())
                        .register(registry));

                // 记录平均耗时（可选）
                sample.stop(Timer.builder("http.request.duration")
                        .description("Request duration in milliseconds")
                        .tag("uri", uri)
                        .tag("method", request.getMethod())
                        .publishPercentiles(0.5, 0.95) // 50%和95%百分位
                        .register(registry));

                timerSamples.remove(uri);
            }
        }
    }
}
