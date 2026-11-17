package com.chua.starter.common.support.log;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 链路日志汇聚自动装配。
 *
 * <p>仅在 {@code plugin.log.portal.enabled=true} 时生效：创建一个
 * {@link TracePortalAppender}（其 {@code start()} 会挂到 logback root），
 * 应用关闭时 {@code stop()} 摘除并收口残余缓冲。默认关闭，不触碰任何现有
 * console/file/async appender 与日志 pattern。</p>
 *
 * @author CH
 * @since 4.0.0
 */
@EnableConfigurationProperties(TracePortalProperties.class)
@ConditionalOnProperty(name = "plugin.log.portal.enabled", havingValue = "true", matchIfMissing = false)
public class TracePortalConfiguration {

    /**
     * 日志。
     */
    private static final Logger log = LoggerFactory.getLogger(TracePortalConfiguration.class);

    /**
     * 汇聚属性。
     */
    private final TracePortalProperties properties;

    /**
     * 构造。
     *
     * @param properties 属性
     */
    public TracePortalConfiguration(TracePortalProperties properties) {
        this.properties = properties;
    }

    /**
     * 创建并启动汇聚 Appender（start 由本方法显式触发，确保挂到 root）。
     *
     * @return appender 实例（供容器在关闭时调用 stop）
     */
    @Bean(destroyMethod = "stop")
    public TracePortalAppender tracePortalAppender() {
        TracePortalAppender appender = new TracePortalAppender();
        appender.setOutputPath(properties.getFile());
        appender.setPortalConfig(properties.toConfig());
        appender.start();
        log.info(">>>>>>> 链路日志汇聚已启用 [plugin.log.portal.enabled=true], 输出: {}",
                properties.getFile());
        return appender;
    }

    /**
     * 兜底：容器未回调 destroyMethod 时也收口残余缓冲。
     */
    @PreDestroy
    public void flushRemaining() {
        try {
            TraceLogPortal.flushAll();
        } catch (Throwable ignore) {
            // 忽略
        }
    }
}
