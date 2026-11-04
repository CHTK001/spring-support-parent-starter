package com.chua.starter.apm.skywalking.config;

import com.chua.skywalking.support.client.SkywalkingSettings;
import com.chua.skywalking.support.config.SkywalkingAutoConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 把 {@code plugin.apm.skywalking.*} 覆盖到进程级 SkyWalking 设置上。
 * <p>
 * 声明在 utils 的 {@link SkywalkingAutoConfiguration} 之后执行，因此同名项以
 * {@code plugin.apm.skywalking.*} 为准；未配置时保持 {@code skywalking.*} 的效果。
 * </p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@AutoConfiguration(after = SkywalkingAutoConfiguration.class)
@ConditionalOnClass(SkywalkingSettings.class)
@ConditionalOnProperty(prefix = "plugin.apm.skywalking", name = "url")
@EnableConfigurationProperties(SkywalkingApmProperties.class)
public class SkywalkingApmAutoConfiguration implements InitializingBean {

    private final SkywalkingApmProperties properties;

    /**
     * 构造配置桥接
     *
     * @param properties {@code plugin.apm.skywalking.*} 配置
     */
    public SkywalkingApmAutoConfiguration(SkywalkingApmProperties properties) {
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        SkywalkingSettings.apply(properties.getUrl(), properties.getUsername(), properties.getPassword(),
                properties.getTimeout(), SkywalkingSettings.enabled());
        log.info("[APM] SkyWalking OAP 地址（plugin.apm.skywalking.url）: {}", SkywalkingSettings.url());
    }
}
