package com.chua.starter.osgi.support.publish;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * SPI 发布公共工具：收集可发布给 OSGi 的业务接口。
 *
 * <p>供 Spring Bean 自动发布器（{@link OsgiSpiBeanPublisher}）与
 * 外部 bundle 自动发布器（{@link BundleSpiAutoPublisher}）共用，
 * 保证两条发布链路的业务接口口径完全一致（跨类层级收集、去重、
 * 排除 JDK / Jakarta / Spring / OSGi / Lombok 等框架接口）。</p>
 *
 * @author CH
 * @since 4.0.0.51
 */
final class SpiPublishSupport {

    private SpiPublishSupport() {
    }

    /**
     * 收集类可发布给 OSGi 的业务接口（跨类层级、去重、排除框架接口）。
     *
     * @param clazz 实现类类型
     * @return 可发布接口列表
     */
    static List<Class<?>> publishableInterfaces(Class<?> clazz) {
        Set<Class<?>> result = new LinkedHashSet<>();
        for (Class<?> current = clazz; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Class<?> iface : current.getInterfaces()) {
                collectInterfaces(iface, result);
            }
        }
        result.removeIf(SpiPublishSupport::isExcluded);
        return new ArrayList<>(result);
    }

    /**
     * 递归收集接口及其父接口。
     *
     * @param iface  接口
     * @param result 收集结果
     */
    private static void collectInterfaces(Class<?> iface, Set<Class<?>> result) {
        if (isExcluded(iface)) {
            return;
        }
        result.add(iface);
        for (Class<?> parent : iface.getInterfaces()) {
            collectInterfaces(parent, result);
        }
    }

    /**
     * 是否为不应发布的框架接口。
     *
     * @param type 接口类型
     * @return 应排除返回 {@code true}
     */
    private static boolean isExcluded(Class<?> type) {
        String name = type.getName();
        return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("jakarta.")
                || name.startsWith("org.springframework.") || name.startsWith("org.osgi.")
                || name.startsWith("lombok.");
    }
}
