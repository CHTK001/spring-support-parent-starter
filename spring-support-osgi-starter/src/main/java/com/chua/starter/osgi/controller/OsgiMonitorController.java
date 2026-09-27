package com.chua.starter.osgi.controller;

import com.chua.common.support.osgi.OsgiBundle;
import com.chua.common.support.osgi.OsgiLauncher;
import com.chua.common.support.osgi.OsgiServiceDescriptor;
import com.chua.osgi.support.FelixOsgiLauncher;
import com.chua.osgi.support.register.impl.OsgiBeanDefinitionRegister;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OSGi 运行时只读观测端点。
 *
 * <p>暴露 bundle 清单、服务清单、框架统计与单个 bundle 详情，便于在生产环境
 * 无需进入 SSH/Telnet shell 即可确认插件化运行状态。</p>
 *
 * <p>本端点仅提供查询能力，不暴露安装、卸载、升级等写操作——
 * 变更类操作需经远程管理通道（受独立凭据保护）执行，避免把高危操作
 * 暴露在仅靠 HTTP 层防护的接口上。</p>
 *
 * <p>仅在 {@code plugin.osgi.endpoint.enabled=true} 且 classpath 中存在 spring-web 时装配。</p>
 *
 * @author CH
 * @since 4.0.0.43
 */
@Slf4j
@RestController
@RequestMapping("/osgi")
public class OsgiMonitorController {

    /**
     * osgi 启动器。
     */
    private final OsgiLauncher launcher;

    /**
     * Bean 桥接注册器，可能未装配。
     */
    private final OsgiBeanDefinitionRegister register;

    /**
     * 构造 osgi 观测端点。
     *
     * @param launcher osgi 启动器
     * @param register Bean 桥接注册器，可为 {@code null}
     */
    public OsgiMonitorController(OsgiLauncher launcher, OsgiBeanDefinitionRegister register) {
        this.launcher = launcher;
        this.register = register;
    }

    /**
     * 获取框架统计信息。
     *
     * @return 统计信息
     */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> stats = new LinkedHashMap<>(launcher.getFrameworkStats());
        stats.put("registeredServiceTypes", launcher.getRegisteredServiceTypes());
        return stats;
    }

    /**
     * 获取全部已安装 bundle 清单。
     *
     * @return bundle 清单
     */
    @GetMapping("/bundles")
    public List<Map<String, Object>> bundles() {
        return launcher.getBundles().stream().map(this::describe).toList();
    }

    /**
     * 获取单个 bundle 详情。
     *
     * @param symbolicName bundle 符号名
     * @return bundle 详情，不存在时返回 {@code null}
     */
    @GetMapping("/bundles/{symbolicName}")
    public Map<String, Object> bundle(@PathVariable String symbolicName) {
        OsgiBundle bundle = launcher.getBundle(symbolicName);
        if (bundle == null) {
            return null;
        }
        Map<String, Object> detail = describe(bundle);
        detail.put("headers", bundle.getHeaders());
        detail.put("registeredServiceTypes", bundle.getRegisteredServiceTypes());
        return detail;
    }

    /**
     * 获取服务注册表中的全部服务登记。
     *
     * <p>结果已按 {@code service.ranking} 降序排列，首项即当前生效的服务。</p>
     *
     * @return 服务登记清单
     */
    @GetMapping("/services")
    public List<Map<String, Object>> services() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (OsgiServiceDescriptor descriptor : launcher.getServiceDescriptors()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", descriptor.typeName());
            item.put("pid", descriptor.pid());
            item.put("implClass", descriptor.implClassName());
            item.put("stableId", descriptor.stableId());
            item.put("osgiServiceId", descriptor.osgiServiceId());
            item.put("ranking", descriptor.ranking());
            item.put("properties", descriptor.properties());
            result.add(item);
        }
        return result;
    }

    /**
     * 获取容器侧桥接快照状态。
     *
     * <p>返回桥接定义的当前视图（扩展对象）、框架版本号与验收拒绝记录，
     * 用于确认「OSGi 侧变化是否已传导到容器侧」。</p>
     *
     * @return 快照状态
     */
    @GetMapping("/snapshot")
    public Map<String, Object> snapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("frameworkActive", launcher.isActive());
        result.put("autoStartFailures", launcher.getFrameworkStats().get("autoStartFailures"));
        if (register == null) {
            result.put("bridgeAvailable", false);
            return result;
        }
        result.put("bridgeAvailable", true);
        result.put("frameworkVersion", register.getFrameworkVersion());
        result.put("acceptanceEnabled", register.isAcceptanceEnabled());
        result.put("invalidateOnBundleLifecycle", register.isInvalidateOnBundleLifecycle());
        result.put("definitionCount", register.getBeanDefinitionNames().size());
        result.put("definitions", register.getBeanDefinitionNames());
        result.put("rejected", register.getRejectedSnapshots());
        return result;
    }

    /**
     * 获取框架上下文原始对象，便于框架外组件按名查找服务。
     *
     * @return 框架上下文描述信息
     */
    @GetMapping("/context")
    public Map<String, Object> context() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("active", launcher.isActive());
        result.put("serviceTypes", launcher.getRegisteredServiceTypes());
        if (launcher instanceof FelixOsgiLauncher felix) {
            result.put("bundleContextAvailable", felix.getFrameworkBundleContext() != null);
        } else {
            result.put("bundleContextAvailable", false);
        }
        return result;
    }

    /**
     * 组装 bundle 描述信息。
     *
     * @param bundle bundle
     * @return 描述信息
     */
    private Map<String, Object> describe(OsgiBundle bundle) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("bundleId", bundle.getBundleId());
        item.put("symbolicName", bundle.getSymbolicName());
        item.put("version", bundle.getVersion());
        item.put("state", bundle.getState());
        item.put("location", bundle.getLocation());
        return item;
    }
}
