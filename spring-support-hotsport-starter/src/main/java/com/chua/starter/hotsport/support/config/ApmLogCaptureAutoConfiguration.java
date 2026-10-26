package com.chua.starter.hotsport.support.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 应用日志采集自动配置 — 把宿主真实日志接入 APM 存储。
 *
 * <p>开关：{@code plugin.hotsport.log-capture.enabled=true}（默认启用）。
 * 级别：{@code plugin.hotsport.log-capture.min-level=INFO}。
 * 调用方信息：{@code plugin.hotsport.log-capture.caller-data=true}。</p>
 *
 * <p>仅在 logback 存在时生效；宿主使用其它日志实现时本配置整体不加载，
 * 保证 APM 不会因为日志实现差异导致宿主启动失败。</p>
 *
 * @author CH
 * @since 4.1.0
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(name = "ch.qos.logback.classic.spi.ILoggingEvent")
@ConditionalOnProperty(prefix = "plugin.hotsport.log-capture", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ApmLogCaptureAutoConfiguration {

    /**
     * 注册日志采集 appender（销毁时自动从 root logger 摘除）。
     *
     * @param minLevel         最低采集级别
     * @param callerData       是否采集调用方类名/方法名
     * @param maxMessageLength 单条消息最大长度
     * @return 日志采集 appender
     */
    @Bean(destroyMethod = "stop")
    public ApmLogAppender apmLogAppender(
            @Value("${plugin.hotsport.log-capture.min-level:INFO}") String minLevel,
            @Value("${plugin.hotsport.log-capture.caller-data:true}") boolean callerData,
            @Value("${plugin.hotsport.log-capture.max-message-length:4096}") int maxMessageLength) {
        ApmLogAppender appender = new ApmLogAppender(minLevel, callerData, maxMessageLength);
        appender.start();
        if (appender.isStarted()) {
            log.info("[Hotsport] 应用日志采集已启用，级别 >= {}（真实日志写入 APM 存储）", minLevel);
        } else {
            log.warn("[Hotsport] 应用日志采集未启动（logback 上下文不可用），日志页将为空");
        }
        return appender;
    }
}
