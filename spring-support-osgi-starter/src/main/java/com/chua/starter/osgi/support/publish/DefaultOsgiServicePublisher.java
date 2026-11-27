// [P3C A20 豁免] A20/A30/A31: 含日志/界面/SQL 字符串字面量等非业务魔法值场景，豁免。
package com.chua.starter.osgi.support.publish;

import com.chua.common.support.spi.annotations.Spi;
import com.chua.osgi.support.FelixOsgiLauncher;
import lombok.extern.slf4j.Slf4j;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import com.chua.common.support.utils.CollectionUtils;

/**
 * 默认 OSGi 服务发布门面：委托 {@link FelixOsgiLauncher} 的框架
 * {@link BundleContext} 完成服务注册，并跟踪注册句柄以支持按实例注销。
 *
 * <p>Felix 框架未运行时（未启用服务器设施或启动失败）所有发布操作为 no-op。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
@Slf4j
public class DefaultOsgiServicePublisher implements OsgiServicePublisher, DisposableBean {

    /**
     * Felix 启动器提供者（延迟解析，框架未装配时得到空）。
     */
    private final ObjectProvider<FelixOsgiLauncher> launcherProvider;

    /**
     * 服务实例 → 其在 OSGi 注册表中的注册句柄（用于按实例注销）。
     */
    private final Map<Object, List<ServiceRegistration<?>>> registrations = new ConcurrentHashMap<>();

    /**
     * 构造发布门面。
     *
     * @param launcherProvider Felix 启动器提供者
     */
    public DefaultOsgiServicePublisher(ObjectProvider<FelixOsgiLauncher> launcherProvider) {
        this.launcherProvider = launcherProvider;
    }

    @Override
    public boolean isActive() {
        FelixOsgiLauncher launcher = launcherProvider.getIfAvailable();
        return launcher != null && launcher.isActive();
    }

    @Override
    public <T> boolean publish(Class<T> type, T service) {
        return publish(type, service, null);
    }

    @Override
    public <T> boolean publish(Class<T> type, T service, Map<String, Object> properties) {
        if (type == null || service == null) {
            return false;
        }
        // P4 约束：只有显式标注 @Spi 的服务才允许发布为 OSGi 服务
        if (!AnnotatedElementUtils.hasAnnotation(service.getClass(), Spi.class)) {
            log.warn("[OSGi] 拒绝发布服务 {}: 实例 {} 未标注 @Spi（服务发布侧 @Spi 约束）",
                    type.getName(), service.getClass().getName());
            return false;
        }
        FelixOsgiLauncher launcher = launcherProvider.getIfAvailable();
        if (launcher == null || !launcher.isActive()) {
            log.debug("[OSGi] Felix 框架未运行，跳过服务发布: {}", type.getName());
            return false;
        }
        try {
            BundleContext bundleContext = launcher.getFrameworkBundleContext();
            ServiceRegistration<T> registration = bundleContext.registerService(type, service,
                    properties == null ? null : new Hashtable<>(properties));
            registrations.computeIfAbsent(service, key -> new CopyOnWriteArrayList<>()).add(registration);
            log.info("[OSGi] 服务已发布: {}（实例 {}）", type.getName(), service.getClass().getName());
            return true;
        } catch (Exception e) {
            log.warn("[OSGi] 服务发布失败: {} - {}", type.getName(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean unpublish(Object service) {
        List<ServiceRegistration<?>> list = registrations.remove(service);
        if (CollectionUtils.isEmpty(list)) {
            return false;
        }
        for (ServiceRegistration<?> registration : list) {
            try {
                registration.unregister();
            } catch (Exception e) {
                log.debug("[OSGi] 服务注销跳过（注册可能已失效）: {}", e.getMessage());
            }
        }
        return true;
    }

    /**
     * 容器关闭时注销全部由本门面发布的服务，避免关闭序中悬挂注册。
     */
    @Override
    public void destroy() {
        for (Object service : registrations.keySet()) {
            unpublish(service);
        }
    }
}
