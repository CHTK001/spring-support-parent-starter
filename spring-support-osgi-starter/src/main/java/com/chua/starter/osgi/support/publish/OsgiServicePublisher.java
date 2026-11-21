package com.chua.starter.osgi.support.publish;

import java.util.Map;

/**
 * OSGi 服务发布门面（SPI 注册门面）。
 *
 * <p>把 Spring 容器中的 Bean 发布为 OSGi 服务，供 bundle 侧消费；
 * 发布后的服务同时会被 {@code OsgiBeanDefinitionRegister} 只读桥接投影回
 * {@code ObjectContext}，形成「Spring Bean → OSGi 服务 → 容器扩展对象」的闭环。</p>
 *
 * <p>本门面始终可用：宿主未启用 OSGi 服务器设施（{@code plugin.osgi.server.enabled=false}，
 * 默认）或 Felix 框架未运行时，发布操作为 no-op 并返回 {@code false}，不抛异常。</p>
 *
 * <p><b>@Spi 约束（P4）</b>：仅允许发布标注了
 * {@code com.chua.common.support.spi.annotations.Spi} 的服务实例，
 * 未标注的实例会被拒绝，避免把任意 Bean 灌入 OSGi 服务注册表。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
public interface OsgiServicePublisher {

    /**
     * OSGi 框架是否正在运行（可发布服务）。
     *
     * @return 框架已启动返回 {@code true}
     */
    boolean isActive();

    /**
     * 发布服务到 OSGi 容器。
     *
     * @param type    服务接口类型
     * @param service 服务实例
     * @param <T>     服务类型
     * @return 发布成功返回 {@code true}；框架未运行、实例未标注 {@code @Spi} 等情况返回 {@code false}
     */
    <T> boolean publish(Class<T> type, T service);

    /**
     * 发布带属性的服务到 OSGi 容器。
     *
     * @param type       服务接口类型
     * @param service    服务实例
     * @param properties 服务属性，可为 {@code null}
     * @param <T>        服务类型
     * @return 发布成功返回 {@code true}
     */
    <T> boolean publish(Class<T> type, T service, Map<String, Object> properties);

    /**
     * 注销服务。
     *
     * @param service 服务实例
     * @return 至少注销一个注册返回 {@code true}
     */
    boolean unpublish(Object service);
}
