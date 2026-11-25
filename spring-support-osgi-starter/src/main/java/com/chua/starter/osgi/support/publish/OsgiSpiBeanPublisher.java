// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.osgi.support.publish;

import com.chua.common.support.spi.annotations.Spi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;

import java.util.Map;

/**
 * @Spi Bean 自动发布器（P4：服务发布带 @Spi 约束）。
 *
 * <p>应用就绪后扫描 Spring 容器中所有标注 {@link Spi} 的 Bean，
 * 按其实现的业务接口逐一把实例发布为 OSGi 服务。仅当 OSGi 服务器设施启用
 * （{@code plugin.osgi.server.enabled=true}）且 Felix 框架运行中才会实际发布；
 * 其余情况为 no-op。</p>
 *
 * <p>注册类型取 Bean 实现的全部业务接口（跨类层级收集），排除 JDK / Jakarta /
 * Spring / OSGi / Lombok 等框架接口，避免噪声注册；没有可用业务接口的 Bean 跳过。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class OsgiSpiBeanPublisher implements ApplicationListener<ApplicationReadyEvent> {

    /**
     * 服务发布门面。
     */
    private final OsgiServicePublisher publisher;

    /**
     * Spring 应用上下文（用于枚举 @Spi Bean）。
     */
    private final ApplicationContext applicationContext;

    /**
     * 构造自动发布器。
     *
     * @param publisher          服务发布门面
     * @param applicationContext 应用上下文
     */
    public OsgiSpiBeanPublisher(OsgiServicePublisher publisher, ApplicationContext applicationContext) {
        this.publisher = publisher;
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!publisher.isActive()) {
            log.info("[OSGi] Felix 框架未运行，跳过 @Spi Bean 自动发布");
            return;
        }
        Map<String, Object> beans = applicationContext.getBeansWithAnnotation(Spi.class);
        int published = 0;
        for (Object bean : beans.values()) {
            for (Class<?> type : SpiPublishSupport.publishableInterfaces(bean.getClass())) {
                if (publishSafely(type, bean)) {
                    published++;
                }
            }
        }
        log.info("[OSGi] @Spi Bean 自动发布完成：候选 {} 个，发布 {} 个服务注册", beans.size(), published);
    }

    /**
     * 按捕获类型安全发布（泛型捕获无法直接匹配 {@code Class<?>} 与 {@code Object}）。
     *
     * @param type 服务接口类型
     * @param bean 服务实例
     * @param <T>  服务类型
     * @return 发布成功返回 {@code true}
     */
    private <T> boolean publishSafely(Class<T> type, Object bean) {
        return publisher.publish(type, type.cast(bean));
    }

}
