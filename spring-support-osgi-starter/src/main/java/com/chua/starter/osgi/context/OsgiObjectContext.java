package com.chua.starter.osgi.context;

import com.chua.common.support.objects.DefaultObjectContext;
import com.chua.common.support.objects.ObjectContextConfig;
import com.chua.common.support.objects.definition.BeanDefinition;
import com.chua.common.support.osgi.OsgiLauncher;
import lombok.extern.slf4j.Slf4j;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集成 OSGi 的对象上下文。
 *
 * <p>基于 {@link DefaultObjectContext} 扩展，在本地 Bean 注册表查询之前，
 * 优先实时查询 OSGi 服务注册表。所有 OSGi 查询均为实时操作
 * （每次调用直接委托 {@link OsgiLauncher#getService}/{@link OsgiLauncher#getServices}），
 * 不做任何缓存，保证 OSGi 侧动态安装/卸载 bundle 注册的服务立即可见。</p>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
public class OsgiObjectContext extends DefaultObjectContext {

    /**
     * OSGi 启动器引用。
     */
    private final OsgiLauncher osgiLauncher;

    /**
     * 构造对象上下文。
     *
     * @param config      容器配置
     * @param osgiLauncher OSGi 启动器
     */
    public OsgiObjectContext(ObjectContextConfig config, OsgiLauncher osgiLauncher) {
        super(config);
        this.osgiLauncher = osgiLauncher;
        init(config);
    }

    /**
     * 判断 OSGi 是否激活。
     *
     * @return 已激活返回 true
     */
    public boolean isOsgiActive() {
        return osgiLauncher != null && osgiLauncher.isActive();
    }

    /**
     * 获取 OSGi 启动器。
     *
     * @return OSGi 启动器
     */
    public OsgiLauncher getOsgiLauncher() {
        return osgiLauncher;
    }

    @Override
    public <T> T getBeanOfType(Class<T> type) {
        // 1. 实时查询 OSGi 服务注册表（不缓存）
        if (isOsgiActive() && type != null) {
            try {
                T service = osgiLauncher.getService(type);
                if (service != null) {
                    return service;
                }
            } catch (Exception e) {
                log.trace("[OsgiObjectContext] OSGi getService({}) failed: {}", type.getSimpleName(), e.getMessage());
            }
        }
        // 2. 回落本地注册表
        return super.getBeanOfType(type);
    }

    @Override
    public <T> Map<String, T> getBeanOfTypes(Class<T> type) {
        Map<String, T> result = new LinkedHashMap<>();
        // 1. 实时查询 OSGi 服务注册表（不缓存）
        if (isOsgiActive() && type != null) {
            try {
                List<T> services = osgiLauncher.getServices(type);
                if (services != null) {
                    for (int i = 0; i < services.size(); i++) {
                        result.put("osgi-" + type.getSimpleName() + "-" + i, services.get(i));
                    }
                }
            } catch (Exception e) {
                log.trace("[OsgiObjectContext] OSGi getServices({}) failed: {}", type.getSimpleName(), e.getMessage());
            }
        }
        // 2. 合并本地注册表
        result.putAll(super.getBeanOfTypes(type));
        return result;
    }

    @Override
    public <T> Collection<T> getBeanOfTypeCollection(Class<T> type) {
        return getBeanOfTypes(type).values();
    }

    @Override
    public boolean containsBean(String name) {
        if (isOsgiActive() && name != null && name.startsWith("osgi-")) {
            return true;
        }
        return super.containsBean(name);
    }

    @Override
    public <T> T getBean(String name, Class<T> type) {
        // OSGi 服务名以 osgi- 前缀标识，直接实时查询
        if (isOsgiActive() && name != null && name.startsWith("osgi-") && type != null) {
            try {
                T service = osgiLauncher.getService(type);
                if (service != null) {
                    return service;
                }
            } catch (Exception e) {
                log.trace("[OsgiObjectContext] OSGi getBean('{}') failed: {}", name, e.getMessage());
            }
        }
        return super.getBean(name, type);
    }

    /**
     * 解析 OSGi 服务 Bean 定义（供外部按类型遍历 OSGi 服务）。
     *
     * @param type 服务类型
     * @return OSGi 服务 Bean 定义列表
     */
    public Collection<BeanDefinition> getOsgiBeanDefinitions(Class<?> type) {
        Map<String, Object> beans = new LinkedHashMap<>();
        if (isOsgiActive() && type != null) {
            try {
                List<?> services = osgiLauncher.getServices(type);
                if (services != null) {
                    for (int i = 0; i < services.size(); i++) {
                        beans.put("osgi-" + type.getSimpleName() + "-" + i, services.get(i));
                    }
                }
            } catch (Exception e) {
                log.trace("[OsgiObjectContext] getOsgiBeanDefinitions({}) failed: {}", type.getSimpleName(), e.getMessage());
            }
        }
        return beans.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(entry -> new com.chua.common.support.objects.definition.SingletonBeanDefinition(
                        com.chua.common.support.objects.definition.TypeBeanDefinition.of(type), entry.getValue()))
                .map(def -> (BeanDefinition) def)
                .toList();
    }
}
