package com.chua.starter.monitor.support.server.filter;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.spi.ServiceProvider;
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
* 服务管理 —— 服务端过滤器 插件工厂。
*
* <p>依据 {@link ServerFilterDescriptor} 实例化过滤器：</p>
* <ul>
*   <li><b>file-storage 预设</b>：反射创建 filesystem-starter 的
*       {@code FileStorageServerFilter}（classpath 存在该模块时可用），
* 参数 映射到 {@code FileStorageSetting}，并自动发现 Spring 容器中的
*       {@code FileStorage} Bean 注册为存储桶，使 HTTP 服务器变成文件服务器</li>
*   <li><b>通用插件</b>：type 视为 ServerFilter 全限定类名，反射实例化
* （优先无参构造，其次 (服务端setting) 构造），参数 经
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
    * 文件storage服务端过滤器 全限定类名（spring-支持-filesystem-启动）
    */
    public static final String FILE_STORAGE_FILTER_CLASS = "com.chua.starter.filesystem.support.server.FileStorageServerFilter";
    /**
    * 文件storagesetting 全限定类名（spring-支持-filesystem-启动）
    */
    private static final String FILE_STORAGE_SETTING_CLASS = "com.chua.starter.filesystem.support.server.FileStorageSetting";
    /**
    * 文件storage 全限定类名（common-starter）
    */
    private static final String FILE_STORAGE_CLASS = "com.chua.common.support.storage.FileStorage";

    /**
    * 文件storagesetting 布尔开关映射的参数名
    */
    private static final List<String> SETTING_BOOLEAN_KEYS = List.of(
            "openPreview", "openDownload", "openRange", "openWatermark", "openRemoteFile", "openWebjars", "layout");

    /**
    * 文件storagesetting 字符串参数映射的参数名
    */
    private static final List<String> SETTING_STRING_KEYS = List.of("watermark", "layoutType");

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
    * 按描述列表实例化全部过滤器（按 订单 升序返回）。
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
                /**
                * illegal参数异常。
                * @param descriptor.getName( descriptor.获取名称(
                * @return illegal参数异常的结果
                */
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
            /**
            * illegal参数异常。
            * @param descriptor descriptor
            * @return illegal参数异常的结果
            */
            throw new IllegalArgumentException("ServerFilter 描述缺少 name/type: " + descriptor);
        }
        if (FILE_STORAGE_ALIAS.equalsIgnoreCase(type) || FILE_STORAGE_FILTER_CLASS.equals(type)) {
            return createFileStorageFilter(descriptor);
        }
        // 非全限定类名(不含点)时, 先尝试按 SPI 名称实例化, 失败回退 FQCN
        if (type.indexOf('.') == -1) {
            ServerFilter spiFilter = createSpiFilter(type, descriptor, setting);
            if (spiFilter != null) {
                return spiFilter;
            }
        }
        return createGenericFilter(type, descriptor, setting);
    }

    /**
    * 按 ServerFilter SPI 名称实例化过滤器。
    *
    * <p>优先尝试无参构造（插件自行在 {@code init} 阶段接收参数），
    * 失败再尝试 {@code (ServerSetting)} 构造。</p>
    *
    * @param type       SPI 名称(如 url-mapping/shell-url-mapping/service-discovery/reverse-proxy)
    * @param descriptor 插件描述
    * @param setting    所属服务器配置
    * @return 过滤器实例, SPI 未注册或实例化失败返回 空
    */
    private ServerFilter createSpiFilter(String type, ServerFilterDescriptor descriptor, ServerSetting setting) {
        ServerFilter filter = tryCreateSpi(type);
        if (filter == null) {
            filter = tryCreateSpi(type, setting);
        }
        if (filter == null) {
            return null;
        }
        log.info("[ServerFilterFactory] SPI 过滤器已创建: name={}, type={}", descriptor.getName(), type);
        return filter;
    }

    /**
    * 尝试按 SPI 名称与构造参数实例化，异常时返回 空 以便上层回退。
    *
    * @param type 类型
    * @param args 构造参数
    * @return 过滤器实例或 空
    */
    private ServerFilter tryCreateSpi(String type, Object... args) {
        try {
            return ServiceProvider.of(ServerFilter.class).getNewExtension(type, args);
        } catch (Exception e) {
            log.debug("[ServerFilterFactory] SPI 过滤器实例化失败: {} - {}", type, e.getMessage());
            return null;
        }
    }

    /**
    * 反射创建 文件storage服务端过滤器（可选插件，类路径 含 spring-支持-filesystem-启动 时可用）。
    *
    * @param descriptor 插件描述
    * @return 过滤器实例
    */
    private ServerFilter createFileStorageFilter(ServerFilterDescriptor descriptor) {
        Class<?> filterClass = ReflectUtils.forName(FILE_STORAGE_FILTER_CLASS);
        Class<?> settingClass = ReflectUtils.forName(FILE_STORAGE_SETTING_CLASS);
        if (filterClass == null || settingClass == null) {
            throw new IllegalArgumentException(
                    "file-storage 插件不可用: classpath 缺少 spring-support-filesystem-starter ("
                            + FILE_STORAGE_FILTER_CLASS + ")");
        }
        try {
            Object storageSetting = ReflectUtils.instantiate(settingClass);
            /**
            * applysetting参数。
            * @param storageSetting storagesetting
            * @param descriptor.getParams( descriptor.获取参数(
            */
            applySettingParams(storageSetting, descriptor.getParams());
            Object filter = ReflectUtils.instantiate(filterClass, storageSetting);
            /**
            * wirestorages。
            * @param filterClass 过滤器类
            * @param filter 过滤器
            */
            wireStorages(filterClass, filter);
            log.info("[ServerFilterFactory] file-storage 插件已创建: name={}, params={}",
                    descriptor.getName(), descriptor.getParams());
            return (ServerFilter) filter;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            /**
            * illegal参数异常。
            * @param e.getMessage( e.获取消息(
            * @return illegal参数异常的结果
            */
            throw new IllegalArgumentException("file-storage 插件实例化失败: " + e.getMessage(), e);
        }
    }

    /**
    * 将 参数 映射到 文件storagesetting。
    *
    * @param storageSetting 文件storagesetting 实例
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
                    // [P3C 1.10 豁免] 按 setter 名称动态定位方法，ReflectUtils.invoke 需预知返回类型，此处保持原生反射
                    Method method = storageSetting.getClass().getMethod(setter, boolean.class);
                    method.invoke(storageSetting, parseBoolean(value));
                } else if (SETTING_STRING_KEYS.contains(key)) {
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
    * 自动发现 Spring 容器中的 文件storage Bean 并注册为存储桶。
    *
    * @param filterClass 过滤器类
    * @param filter      过滤器实例
    */
    private void wireStorages(Class<?> filterClass, Object filter) {
        try {
            Class<?> storageClass = ReflectUtils.forName(FILE_STORAGE_CLASS);
            if (storageClass == null) {
                log.warn("[ServerFilterFactory] FileStorage 类型不可用, 跳过存储桶注册");
                return;
            }
            // [P3C 1.10 豁免] 跨包反射调用 addFileStorage，第三方框架内部签名，保持原生反射
            Method addStorage = filterClass.getMethod("addFileStorage", String.class, storageClass);

            // 1) 优先注册 Spring 容器中直接暴露的 文件storage Bean
            int registered = 0;
            String[] beanNames = applicationContext.getBeanNamesForType(storageClass);
            for (String beanName : beanNames) {
                Object storage = applicationContext.getBean(beanName);
                addStorage.invoke(filter, beanName, storage);
                registered++;
                log.info("[ServerFilterFactory] 注册文件存储桶(Bean): {} -> {}", beanName, storage.getClass().getSimpleName());
            }

            // 2) 兜底: 文件storagetemplate 的 storage映射 常以内部 映射 形式持有 提供者,
            // 并不注册为独立 Bean —— 从模板取桶, 使 文件-storage 过滤器真正有后端可用
            try {
                Object template = applicationContext.getBean("fileStorageTemplate");
                @SuppressWarnings("unchecked")
                Map<String, Object> storageMap = (Map<String, Object>)
                        ReflectUtils.invoke(template, "getStorageMap", Map.class);
                if (storageMap != null) {
                    for (Map.Entry<String, Object> entry : storageMap.entrySet()) {
                        if (storageClass.isInstance(entry.getValue())) {
                            addStorage.invoke(filter, entry.getKey(), entry.getValue());
                            registered++;
                            log.info("[ServerFilterFactory] 注册文件存储桶(Template): {} -> {}",
                                    entry.getKey(), entry.getValue().getClass().getSimpleName());
                        }
                    }
                }
            } catch (Exception ignored) {
                // 文件storagetemplate Bean 不存在(非 filesystem-启动 场景)时忽略
            }

            if (registered == 0) {
                log.info("[ServerFilterFactory] 未发现任何 FileStorage 存储桶, 文件服务器暂无后端");
            }
        } catch (Exception e) {
            log.warn("[ServerFilterFactory] 存储桶注册失败(可忽略): {}", e.getMessage());
        }
    }

    /**
    * 反射创建通用 服务端过滤器 插件（类型 为全限定类名）。
    *
    * @param type       全限定类名
    * @param descriptor 插件描述
    * @param setting    所属服务器配置
    * @return 过滤器实例
    */
    private ServerFilter createGenericFilter(String type, ServerFilterDescriptor descriptor, ServerSetting setting) {
        Class<?> clazz = ReflectUtils.forName(type);
        if (clazz == null) {
            throw new IllegalArgumentException(
                    "ServerFilter 插件 [" + descriptor.getName() + "] 类型不可用: " + type
                            + " (支持预设别名 file-storage 或全限定类名)");
        }
        try {
            Object instance = newInstance(clazz, setting);
            if (!(instance instanceof ServerFilter filter)) {
                /**
                * illegal参数异常。
                * @param type 类型
                * @return illegal参数异常的结果
                */
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
    * 按构造器优先级实例化：无参构造 → (服务端setting) 构造。
    *
    * @param clazz   目标类
    * @param setting 服务器配置
    * @return 实例
    * @throws Exception 反射异常
    */
    private Object newInstance(Class<?> clazz, ServerSetting setting) throws Exception {
        // [P3C 1.10 豁免] 先试无参构造、再试 (ServerSetting) 构造的优先级探测，
        // ReflectUtils.instantiate 失败时静默返回 null，无法区分"无该构造器"与"实例化失败"，保持原生反射
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (NoSuchMethodException ignored) {
            // 尝试 (服务端setting) 构造
        }
        try {
            return clazz.getDeclaredConstructor(ServerSetting.class).newInstance(setting);
        } catch (NoSuchMethodException e) {
            /**
            * illegal参数异常。
            * @param clazz.getName( clazz.获取名称(
            * @return illegal参数异常的结果
            */
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
