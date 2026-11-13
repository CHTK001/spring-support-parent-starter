package com.chua.starter.device.support.timeseries.impl;

import com.chua.starter.device.support.timeseries.DeviceTimeSeriesEngine;
import com.chua.starter.device.support.timeseries.DeviceTimeSeriesProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis 时序引擎工厂。
 *
 * <p>device-starter 把 spring-boot-starter-data-redis 声明为 {@code optional}：
 * 宿主持有 redis 时本模块才用得上它。为了在「没引入 redis」的宿主上也能正常启动，
 * 这里用 {@link Class#forName(String)} 先探测类型，探测不到就返回明确不可用的占位引擎，
 * 而不是让整个模块因为 {@code NoClassDefFoundError} 起不来。</p>
 *
 * @since 4.0.0.55
 * @author CH
 */
@Slf4j
public final class RedisTimeSeriesEngineFactory {

    /**
     * Redis 模板类型名（编译期依赖可选，这里只做运行时探测）
     */
    private static final String TEMPLATE_CLASS = "org.springframework.data.redis.core.StringRedisTemplate";

    /**
     * 工具类不允许实例化。
     */
    private RedisTimeSeriesEngineFactory() {
    }

    /**
     * 创建 Redis 时序引擎。
     *
     * @param properties 时序配置
     * @param context    Spring 上下文（用于取宿主的 StringRedisTemplate）
     * @return Redis 引擎；宿主没有 redis 时返回不可用占位引擎
     */
    public static DeviceTimeSeriesEngine create(DeviceTimeSeriesProperties properties, ApplicationContext context) {
        try {
            Class<?> templateType = Class.forName(TEMPLATE_CLASS);
            ObjectProvider<?> provider = context.getBeanProvider(templateType);
            Object template = provider.getIfAvailable();
            if (template == null) {
                return new DisabledTimeSeriesEngine("redis",
                        "宿主未配置 spring.data.redis（找不到 StringRedisTemplate Bean）");
            }
            return new RedisTimeSeriesEngine(properties, (StringRedisTemplate) template);
        } catch (ClassNotFoundException ex) {
            log.info("[Device][Series] 宿主未引入 spring-data-redis，Redis 时序引擎不可用");
            return new DisabledTimeSeriesEngine("redis",
                    "宿主未引入 spring-data-redis，无法使用 Redis 时序引擎");
        }
    }
}
