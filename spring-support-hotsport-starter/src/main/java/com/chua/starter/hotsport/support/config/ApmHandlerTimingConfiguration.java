package com.chua.starter.hotsport.support.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 控制器方法耗时采集自动配置。
 *
 * <p>与 {@link SlowCallAutoConfiguration} 共用同一个开关
 * {@code plugin.hotsport.request-record.enabled}（默认启用）：传输记录要能还原
 * 「首层 HTTP → 子层 class.method」，就必须同时注册本拦截器，两者是一套采集链。</p>
 *
 * <p>拦截器顺序设为最低优先级，排在业务拦截器之后，避免影响鉴权/限流等既有逻辑。</p>
 *
 * @author CH
 * @since 4.1.0
 */
@Slf4j
@AutoConfiguration(after = SlowCallAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "plugin.hotsport.request-record", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ApmHandlerTimingConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new ApmHandlerTimingInterceptor())
                .addPathPatterns("/**")
                .order(Integer.MAX_VALUE);
        log.info("[Hotsport] 控制器方法耗时拦截器已注册（用于还原 class.method 调用层级）");
    }
}
