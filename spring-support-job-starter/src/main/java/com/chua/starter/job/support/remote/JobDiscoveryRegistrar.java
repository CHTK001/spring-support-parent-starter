package com.chua.starter.job.support.remote;

import com.chua.starter.job.support.handler.JobHandlerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.util.ClassUtils;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 任务发现注册器。
 * <p>
 * 在所有单例初始化完成后，如果发现服务存在，将本地注册的 {@code @Job} 处理器
 * 重新注册到发现服务的元数据中，使调度中心能够通过发现服务发现本客户端可执行的远程任务。
 * </p>
 *
 * <h3>工作原理</h3>
 * <ol>
 *     <li>{@link SmartInitializingSingleton#afterSingletonsInstantiated()} 确保所有 {@code @Job} bean 已扫描完毕</li>
 *     <li>通过反射检测 {@code DiscoveryService} 是否在类路径上</li>
 *     <li>若存在，读取 {@code JobHandlerFactory} 中所有已注册的处理器名称</li>
 *     <li>构建元数据字符串并通过反射调用 {@code ServiceDiscovery.registerService()} 更新注册信息</li>
 * </ol>
 *
 * <p>
 * 当类路径上不存在发现服务时，本注册器自动跳过，不产生任何影响。
 * </p>
 *
 * @author CH
 * @since 2024/03/11
 */
@Slf4j
@RequiredArgsConstructor
public class JobDiscoveryRegistrar implements SmartInitializingSingleton {

    private static final String DISCOVERY_SERVICE_CLASS = "com.chua.starter.discovery.support.service.DiscoveryService";
    private static final String SERVICE_DISCOVERY_CLASS = "com.chua.common.support.network.discovery.ServiceDiscovery";
    private static final String DISCOVERY_CLASS = "com.chua.common.support.network.discovery.Discovery";

    private final ListableBeanFactory beanFactory;
    private final Environment environment;

    @Override
    public void afterSingletonsInstantiated() {
        Set<String> handlerNames = JobHandlerFactory.getInstance().keys();
        if (handlerNames.isEmpty()) {
            return;
        }

        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader == null) {
            classLoader = JobDiscoveryRegistrar.class.getClassLoader();
        }

        if (!ClassUtils.isPresent(SERVICE_DISCOVERY_CLASS, classLoader)) {
            log.debug("[JobDiscovery] 发现服务 SPI 不可用，跳过任务注册");
            return;
        }

        try {
            Object discoveryService = resolveDiscoveryService(classLoader);
            if (discoveryService == null) {
                log.debug("[JobDiscovery] 未找到 DiscoveryService Bean，跳过任务注册");
                return;
            }

            registerJobMetadata(classLoader, discoveryService, handlerNames);
        } catch (Exception e) {
            log.warn("[JobDiscovery] 注册任务元数据失败: {}", e.getMessage());
        }
    }

    /**
     * 获取 DiscoveryService Bean。
     * <p>
     * 通过反射从 BeanFactory 中获取 DiscoveryService 实例，避免编译期依赖。
     * </p>
     */
    private Object resolveDiscoveryService(ClassLoader classLoader) throws Exception {
        Class<?> discoveryServiceType = ClassUtils.forName(DISCOVERY_SERVICE_CLASS, classLoader);
        Map<String, ?> beans = beanFactory.getBeansOfType(discoveryServiceType);
        if (beans.isEmpty()) {
            return null;
        }
        return beans.values().iterator().next();
    }

    /**
     * 将任务处理器名称作为元数据重新注册到发现服务。
     * <p>
     * 读取 DiscoveryService 下所有注册的命名空间，对每个命名空间重新注册
     * 并携带 jobs 元数据，使调度中心能感知本客户端可执行的远程任务。
     * </p>
     */
    @SuppressWarnings("unchecked")
    private void registerJobMetadata(ClassLoader classLoader, Object discoveryService,
                                     Set<String> handlerNames) throws Exception {
        Class<?> serviceDiscoveryClass = ClassUtils.forName(SERVICE_DISCOVERY_CLASS, classLoader);
        Class<?> discoveryClass = ClassUtils.forName(DISCOVERY_CLASS, classLoader);

        // 构建 jobs 元数据: "handler1,handler2,..."
        String jobsValue = String.join(",", handlerNames);
        log.info("[JobDiscovery] 注册 {} 个远程任务处理器到发现服务: {}", handlerNames.size(), jobsValue);

        // 构建 job_configs 元数据: JSON 格式的配置映射
        Map<String, Map<String, String>> allConfigs = JobHandlerFactory.getAllConfigs();
        String jobConfigsValue = allConfigs.isEmpty() ? null : com.chua.common.support.lang.json.Json.toJson(allConfigs);

        // 从 DiscoveryService 中获取 underlying ServiceDiscovery 实例
        // DiscoveryService 持有一个 defaultServiceDiscovery 字段
        java.lang.reflect.Field defaultField = discoveryService.getClass()
                .getDeclaredField("defaultServiceDiscovery");
        defaultField.setAccessible(true);
        Object defaultServiceDiscovery = defaultField.get(discoveryService);
        if (defaultServiceDiscovery == null) {
            return;
        }

        // 获取所有已注册的命名空间路径
        // 通过 DiscoveryService.getDiscoveryAll(path) 获取已注册的服务
        // 但由于我们需要的是 path，而不是结果，所以要从 Environment 读取
        String namespaces = environment.getProperty("plugin.discovery.properties[0].node[0].namespace", "");
        if (namespaces.isBlank()) {
            log.debug("[JobDiscovery] 未找到命名空间配置 (plugin.discovery.*)，跳过");
            return;
        }

        // 获取所有已注册的 Discovery 实例
        Method getDiscoveryAll = discoveryService.getClass().getMethod("getDiscoveryAll", String.class, String.class);
        String protocol = environment.getProperty("plugin.discovery.properties[0].protocol", "");
        Set<Object> discoveries;
        if (!protocol.isBlank()) {
            discoveries = (Set<Object>) getDiscoveryAll.invoke(discoveryService, protocol, namespaces);
        } else {
            Method getDiscoveryAllSingle = discoveryService.getClass().getMethod("getDiscoveryAll", String.class);
            discoveries = (Set<Object>) getDiscoveryAllSingle.invoke(discoveryService, namespaces);
        }

        if (discoveries == null || discoveries.isEmpty()) {
            log.debug("[JobDiscovery] 命名空间 {} 下无已注册服务", namespaces);
            return;
        }

        // 获取 registerService 方法
        Method registerService = serviceDiscoveryClass.getMethod("registerService", String.class, discoveryClass);

        // 为每个已注册的 Discovery 实例更新 jobs 元数据
        Method metadataMethod = discoveryClass.getMethod("getMetadata");
        for (Object discovery : discoveries) {
            Map<String, String> metadata = (Map<String, String>) metadataMethod.invoke(discovery);
            if (metadata == null) {
                // 如果 metadata 为空，通过 setter 设置
                metadata = new LinkedHashMap<>();
                metadata.put("jobs", jobsValue);
                if (jobConfigsValue != null) {
                    metadata.put("job_configs", jobConfigsValue);
                }
                Method setMetadata = discoveryClass.getMethod("setMetadata", Map.class);
                setMetadata.invoke(discovery, metadata);
            } else {
                metadata.put("jobs", jobsValue);
                if (jobConfigsValue != null) {
                    metadata.put("job_configs", jobConfigsValue);
                }
            }

            // 重新注册以更新元数据
            Method getUriSpec = discoveryClass.getMethod("getUriSpec");
            String uriSpec = (String) getUriSpec.invoke(discovery);
            registerService.invoke(defaultServiceDiscovery, uriSpec, discovery);
            log.debug("[JobDiscovery] 更新发现服务元数据: path={}, jobs={}", uriSpec, jobsValue);
        }
    }
}
