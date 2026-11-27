package com.chua.starter.datasource.configuration;

import com.chua.datasource.support.engine.CachingEngine;
import com.chua.datasource.support.engine.JdbcEngine;
import com.chua.starter.datasource.properties.EngineCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Engine 读缓存自动配置。
 *
 * <p>开启 {@code plugin.datasource.engine.cache.enabled=true} 后，容器中唯一的 {@link JdbcEngine} Bean
 * 会被自动装饰成 {@link CachingEngine}，无需调用方手工 {@code new CachingEngine(delegate)}。
 * 底层引擎 Bean 保持原样（迁移、DDL、连接管理等写入场景仍直接使用它），
 * 装饰器以 {@code @Primary} 暴露，按 {@code Engine} 类型注入即可拿到缓存版本。</p>
 *
 * <p>后台刷新由 {@code plugin.datasource.engine.cache.refresh-interval-seconds} 控制：
 * 默认 {@code 0} 不启动线程；大于 {@code 0} 时按固定间隔重装载已跟踪的表，
 * 用于自动吸收「外部进程直接改库」带来的不一致，被超限旁路的表也会每轮重探。</p>
 *
 * <p>示例：</p>
 * <pre>
 * plugin.h2.enable=true
 * plugin.datasource.engine.cache.enabled=true
 * plugin.datasource.engine.cache.tables=sys_user,sys_dict
 * plugin.datasource.engine.cache.refresh-interval-seconds=30
 * </pre>
 *
 * <p>本自动配置默认关闭；关闭时不注册任何 Bean，不创建任何线程，
 * 既有引擎行为保持不变。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
@AutoConfiguration
@ConditionalOnClass(CachingEngine.class)
@ConditionalOnProperty(prefix = EngineCacheProperties.PRE, name = "enabled", havingValue = "true")
@EnableConfigurationProperties(EngineCacheProperties.class)
@AutoConfigureAfter({H2EngineAutoConfiguration.class, SqliteEngineAutoConfiguration.class})
public class EngineCacheAutoConfiguration {

    /**
     * 注册缓存装饰器：包装容器中的 JdbcEngine，并按属性调整容量、存活时间与刷新间隔。
     *
     * <p>标记 {@code @Primary}，使按 {@code Engine} 类型注入的调用方拿到缓存版本；
     * 按具体引擎类型（如 {@code H2Engine}）注入的调用方仍拿到原始 Bean。</p>
     *
     * @param properties     缓存配置
     * @param engineProvider 容器中的 JdbcEngine 候选
     * @param beanFactory    用于按名字取指定 delegate
     * @return 缓存装饰器
     */
    @Bean
    @Primary
    @ConditionalOnMissingBean(CachingEngine.class)
    public CachingEngine cachingEngine(EngineCacheProperties properties,
                                       ObjectProvider<JdbcEngine> engineProvider,
                                       ListableBeanFactory beanFactory) {
        JdbcEngine delegate = resolveDelegate(properties, engineProvider, beanFactory);
        CachingEngine engine = new CachingEngine(delegate, true);
        long refreshInterval = apply(properties, engine);
        log.info("[engine-cache] 读缓存已启用，delegate={}，tables={}，maxRows={}，maxTotalRows={}，ttl={}s，"
                        + "refresh={}s{}，wheel={}槽/{}ms",
                delegate.getClass().getSimpleName(), properties.getTables(), properties.getMaxRows(),
                properties.getMaxTotalRows(), properties.getTtlSeconds(), refreshInterval,
                properties.getRefreshIntervalSeconds() == null ? "（内部默认）" : "",
                engine.getRefreshWheelSlots(), engine.getRefreshWheelTickMillis());
        return engine;
    }

    /**
     * 解析被装饰的引擎：优先取显式 bean 名，否则要求容器中只有一个 JdbcEngine。
     *
     * @param properties     缓存配置
     * @param engineProvider 容器中的 JdbcEngine 候选
     * @param beanFactory    用于按名字取指定 delegate
     * @return 被装饰的引擎
     * @throws IllegalStateException 未找到候选或候选不唯一且未指定 bean 名时抛出
     */
    private static JdbcEngine resolveDelegate(EngineCacheProperties properties,
                                              ObjectProvider<JdbcEngine> engineProvider,
                                              ListableBeanFactory beanFactory) {
        String beanName = properties.getDelegateBeanName();
        if (beanName != null && !beanName.isBlank()) {
            return beanFactory.getBean(beanName.trim(), JdbcEngine.class);
        }
        List<JdbcEngine> candidates = engineProvider.orderedStream().toList();
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if (candidates.isEmpty()) {
            throw new IllegalStateException("[" + EngineCacheProperties.PRE + "] 已开启，但容器中没有 JdbcEngine Bean；"
                    + "请先启用底层引擎（如 plugin.h2.enable=true）或关闭缓存");
        }
        Map<String, JdbcEngine> named = new LinkedHashMap<>();
        for (String name : beanFactory.getBeanNamesForType(JdbcEngine.class)) {
            named.put(name, beanFactory.getBean(name, JdbcEngine.class));
        }
        throw new IllegalStateException("[" + EngineCacheProperties.PRE + "] 检测到多个 JdbcEngine Bean " + named.keySet()
                + "，请用 " + EngineCacheProperties.PRE + ".delegate-bean-name 指定要缓存的引擎");
    }

    /**
     * 把配置落到装饰器上；{@code <=0} 的容量项保持默认值。
     *
     * <p>刷新间隔的缺省语义：未配置（{@code null}）时取内部默认值
     * {@link EngineCacheProperties#DEFAULT_REFRESH_INTERVAL_SECONDS}，让「只配置底层引擎」也能自动刷新；
     * 显式配置 {@code 0} 表示关闭定时刷新。</p>
     *
     * @param properties 缓存配置
     * @param engine     装饰器
     * @return 实际生效的刷新间隔秒数（{@code 0} 表示未开启定时刷新）
     */
    private static long apply(EngineCacheProperties properties, CachingEngine engine) {
        if (properties.getTables() != null && !properties.getTables().isEmpty()) {
            engine.setCacheTables(properties.getTables());
        }
        if (properties.getMaxRows() > 0) {
            engine.setMaxRows(properties.getMaxRows());
        }
        if (properties.getMaxTotalRows() > 0) {
            engine.setMaxTotalRows(properties.getMaxTotalRows());
        }
        if (properties.getTtlSeconds() >= 0) {
            engine.setTtlSeconds(properties.getTtlSeconds());
        }
        // 时间轮参数先落，最后再设置间隔：间隔最后落只启动一次时间轮，避免按中间参数反复重建
        if (properties.getRefreshWheelSlots() != null) {
            engine.setRefreshWheelSlots(properties.getRefreshWheelSlots());
        }
        if (properties.getRefreshWheelTickMillis() != null) {
            engine.setRefreshWheelTickMillis(properties.getRefreshWheelTickMillis());
        }
        Long configuredRefresh = properties.getRefreshIntervalSeconds();
        long refreshInterval = configuredRefresh == null
                ? EngineCacheProperties.DEFAULT_REFRESH_INTERVAL_SECONDS
                : configuredRefresh;
        engine.setRefreshIntervalSeconds(refreshInterval);
        return refreshInterval;
    }
}
