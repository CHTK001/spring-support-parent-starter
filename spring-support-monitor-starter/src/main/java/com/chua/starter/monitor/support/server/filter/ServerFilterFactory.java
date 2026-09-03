package com.chua.starter.monitor.support.server.filter;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.starter.monitor.support.server.model.ServerFilterDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 服务管理 —— ServerFilter 插件工厂。
 *
 * <p>依据 {@link ServerFilterDescriptor} 实例化过滤器：</p>
 * <ul>
 *   <li><b>file-storage 预设</b>：反射创建 filesystem-starter 的
 *       {@code FileStorageServerFilter}（classpath 存在该模块时可用），
 *       params 映射到 {@code FileStorageSetting}，并自动发现 Spring 容器中的
 *       {@code FileStorage} Bean 注册为存储桶，使 HTTP 服务器变成文件服务器</li>
 *   <li><b>通用插件</b>：type 视为 ServerFilter 全限定类名，反射实例化
 *       （优先无参构造，其次 (ServerSetting) 构造），params 经
 *       {@code init(ServerFilterConfig)} 注入</li>
 * </ul>
 *
 * @author CH
 * @since 4.0.0.42
 */
@Slf4j
@Component
public class ServerFilterFactory {

    /**
     * 文件服务器预设别名
     */
    public static final String FILE_STORAGE_ALIAS = "file-storage";
    /**
     * FileStorageServerFilter 全限定类名（spring-support-filesystem-starter）
     */
    public static final String FILE_STORAGE_FILTER_CLASS = "com.chua.starter.filesystem.support.server.FileStorageServerFilter";
    /**
     * FileStorageSetting 全限定类名（spring-support-filesystem-starter）
     */
    private static final String FILE_STORAGE_SETTING_CLASS = "com.chua.starter.filesystem.support.server.FileStorageSetting";
    /**
     * FileStorage 全限定类名（common-starter）
     */
    private static final String FILE_STORAGE_CLASS = "com.chua.common.support.storage.FileStorage";

    /**
     * FileStorageSetting 布尔开关映射的参数名
     */
    private static final List<String> SETTING_BOOLEAN_KEYS = List.of(
            "openPreview", "openDownload", "openRange", "openWatermark", "openRemoteFile", "openWebjars");

    /**
     * 应用上下文
     */
    private final ApplicationContext applicationContext;

    /**
     * 创建插件工厂
     *
     * @param applicationContext 应用上下文
     */
    @Autowired
    public ServerFilterFactory(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 按描述列表实例化全部过滤器（按 order 升序返回）。
     *
     * @param descriptors 插件描述列表
     * @param setting     所属服务器配置
     * @return 过滤器实例列表
     * @throws IllegalArgumentException 某插件无法实例化时抛出（携带明确原因）
     */
    public List<ServerFilter> createFilters(List<ServerFilterDescriptor> descriptors, ServerSetting setting) {
        List<ServerFilter> filters = new ArrayList<>();
        if (descriptors == null || descriptors.isEmpty()) {
            return filters;
        }
        for (ServerFilterDescriptor descriptor : descriptors) {
            if (descriptor == null || Boolean.FALSE.equals(descriptor.getEnabled())) {
                continue;
            }
            ServerFilter delegate = instantiate(descriptor, setting);
            DelegatingServerFilter filter = new DelegatingServerFilter(
                    delegate,
                    descriptor.getName() != null ? descriptor.getName() : delegate.getFilterId(),
                    descriptor.getOrder(),
                    !Boolean.FALSE.equals(descriptor.getEnabled()));
            try {
                filter.init(new DefaultServerFilterConfig(
                        descriptor.getName(), descriptor.getParams(), setting));
            } catch (Exception e) {
                throw new IllegalArgumentException("过滤器 [" + descriptor.getName() + "] 初始化失败: " + e.getMessage(), e);
            }
            filters.add(filter);
        }
        filters.sort(Comparator.comparingInt(ServerFilter::getOrder));
        return filters;
    }

    /**
     * 实例化单个插件。
     *
     * @param descriptor 插件描述
     * @param setting    所属服务器配置
     * @return 过滤器实例
     */
    private ServerFilter instantiate(ServerFilterDescriptor descriptor, ServerSetting setting) {
        String type = descriptor.resolveType();
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("ServerFilter 描述缺少 name/type: " + descriptor);
        }
        if (FILE_STORAGE_ALIAS.equalsIgnoreCase(type) || FILE_STORAGE_FILTER_CLASS.equals(type)) {
            return createFileStorageFilter(descriptor);
        }
        return createGenericFilter(type, descriptor, setting);
    }

    /**
     * 反射创建 FileStorageServerFilter（可选插件，classpath 含 spring-support-filesystem-starter 时可用）。
     *
     * @param descriptor 插件描述
     * @return 过滤器实例
     */
    private ServerFilter createFileStorageFilter(ServerFilterDescriptor descriptor) {
        Class<?> filterClass;
        Class<?> settingClass;
        try {
            filterClass = Class.forName(FILE_STORAGE_FILTER_CLASS);
            settingClass = Class.forName(FILE_STORAGE_SETTING_CLASS);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "file-storage 插件不可用: classpath 缺少 spring-support-filesystem-starter ("
                            + FILE_STORAGE_FILTER_CLASS + ")", e);
        }
        try {
            Object storageSetting = settingClass.getDeclaredConstructor().newInstance();
            applySettingParams(storageSetting, descriptor.getParams());
            Object filter = filterClass.getDeclaredConstructor(settingClass).newInstance(storageSetting);
            wireStorages(filterClass, filter);
            log.info("[ServerFilterFactory] file-storage 插件已创建: name={}, params={}",
                    descriptor.getName(), descriptor.getParams());
            return (ServerFilter) filter;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("file-storage 插件实例化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 将 params 映射到 FileStorageSetting。
     *
     * @param storageSetting FileStorageSetting 实例
     * @param params         参数
     */
    private void applySettingParams(Object storageSetting, Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            if (value == null) {
                continue;
            }
            String setter = "set" + Character.toUpperCase(key.charAt(0)) + key.substring(1);
            try {
                if (SETTING_BOOLEAN_KEYS.contains(key)) {
                    Method method = storageSetting.getClass().getMethod(setter, boolean.class);
                    method.invoke(storageSetting, parseBoolean(value));
                } else if ("watermark".equals(key)) {
                    Method method = storageSetting.getClass().getMethod(setter, String.class);
                    method.invoke(storageSetting, value);
                } else {
                    log.warn("[ServerFilterFactory] file-storage 忽略未知参数: {}={}", key, value);
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("file-storage 参数映射失败: " + key + "=" + value
                        + " (" + e.getMessage() + ")", e);
            }
        }
    }

    /**
     * 自动发现 Spring 容器中的 FileStorage Bean 并注册为存储桶。
     *
     * @param filterClass 过滤器类
     * @param filter      过滤器实例
     */
    private void wireStorages(Class<?> filterClass, Object filter) {
        try {
            Class<?> storageClass = Class.forName(FILE_STORAGE_CLASS);
            String[] beanNames = applicationContext.getBeanNamesForType(storageClass);
            if (beanNames.length == 0) {
                log.info("[ServerFilterFactory] 容器中未发现 FileStorage Bean, 文件服务器暂无存储桶");
                return;
            }
            Method addStorage = filterClass.getMethod("addFileStorage", String.class, storageClass);
            for (String beanName : beanNames) {
                Object storage = applicationContext.getBean(beanName);
                addStorage.invoke(filter, beanName, storage);
                log.info("[ServerFilterFactory] 注册文件存储桶: {} -> {}", beanName, storage.getClass().getSimpleName());
            }
        } catch (ClassNotFoundException e) {
            log.warn("[ServerFilterFactory] FileStorage 类型不可用, 跳过存储桶注册: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("[ServerFilterFactory] 存储桶注册失败(可忽略): {}", e.getMessage());
        }
    }

    /**
     * 反射创建通用 ServerFilter 插件（type 为全限定类名）。
     *
     * @param type       全限定类名
     * @param descriptor 插件描述
     * @param setting    所属服务器配置
     * @return 过滤器实例
     */
    private ServerFilter createGenericFilter(String type, ServerFilterDescriptor descriptor, ServerSetting setting) {
        Class<?> clazz;
        try {
            clazz = Class.forName(type);
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(
                    "ServerFilter 插件 [" + descriptor.getName() + "] 类型不可用: " + type
                            + " (支持预设别名 file-storage 或全限定类名)", e);
        }
        try {
            Object instance = newInstance(clazz, setting);
            if (!(instance instanceof ServerFilter filter)) {
                throw new IllegalArgumentException("类型不是 ServerFilter 实现: " + type);
            }
            return filter;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "ServerFilter 插件 [" + descriptor.getName() + "] 实例化失败: " + type + " - " + e.getMessage(), e);
        }
    }

    /**
     * 按构造器优先级实例化：无参构造 → (ServerSetting) 构造。
     *
     * @param clazz   目标类
     * @param setting 服务器配置
     * @return 实例
     * @throws Exception 反射异常
     */
    private Object newInstance(Class<?> clazz, ServerSetting setting) throws Exception {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException ignored) {
            // 尝试 (ServerSetting) 构造
        }
        try {
            return clazz.getDeclaredConstructor(ServerSetting.class).newInstance(setting);
        } catch (NoSuchMethodException e) {
            throw new IllegalArgumentException(clazz.getName() + " 缺少无参或 (ServerSetting) 构造器");
        }
    }

    /**
     * 解析布尔参数（true/1/yes/on 均为真）。
     *
     * @param value 原始值
     * @return 布尔结果
     */
    private boolean parseBoolean(String value) {
        String v = value.trim().toLowerCase();
        return v.isEmpty() || "true".equals(v) || "1".equals(v) || "yes".equals(v) || "on".equals(v);
    }
}
