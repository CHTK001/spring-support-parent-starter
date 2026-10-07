package com.chua.starter.monitor.support.server.filter;

import com.chua.common.support.network.server.ServerSetting;
import com.chua.common.support.network.server.filter.ServerFilter;
import com.chua.common.support.network.server.filter.discovery.ServiceDiscoveryServerFilter;
import com.chua.common.support.reflection.ReflectUtils;
import com.chua.common.support.spi.ServiceProvider;
import com.chua.starter.monitor.support.server.model.ServerFilterDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 服务管理 —— 服务端过滤器 插件工厂。
 *
 * <p>依据 {@link ServerFilterDescriptor} 实例化过滤器：</p>
 * <ul>
 *   <li><b>file-storage 预设</b>：反射创建 filesystem-starter 的
 *       {@code FileStorageServerFilter}（classpath 存在该模块时可用），
 * 参数 映射到 {@code FileStorageSetting}，并自动发现 Spring 容器中的
 *       {@code FileStorage} Bean 注册为存储桶，使 HTTP 服务器变成文件服务器</li>
 *   <li><b>service-discovery 预设</b>：以 {@code (String discoveryName)} 构造框架的
 *       {@code ServiceDiscoveryServerFilter}（该类未注册为 ServerFilter SPI，通用路径不可用），
 *       protocol/balance/scatterId/excludeServerId/routes 经 setter 落位</li>
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
     * 服务发现预设别名（框架 ServiceDiscoveryServerFilter 未注册为 ServerFilter SPI，按别名装配）
     */
    public static final String SERVICE_DISCOVERY_ALIAS = "service-discovery";
    /**
     * 服务发现服务端过滤器 全限定类名（utils-support-common-starter）
     */
    public static final String SERVICE_DISCOVERY_FILTER_CLASS =
            "com.chua.common.support.network.server.filter.discovery.ServiceDiscoveryServerFilter";
    /**
     * 服务发现实现缺省使用的 ServiceDiscovery SPI 名
     */
    private static final String DEFAULT_DISCOVERY_NAME = "peerMesh";
    /**
     * 发现实现名的可接受键：平台约定 {@code discoveryName} 与框架 init 原生读取的 {@code discovery.name}
     */
    private static final Set<String> DISCOVERY_NAME_KEYS = Set.of("discoveryName", "discovery.name");
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
     *
     * <p>必须与 {@code ServerManageServiceImpl#fileStorageParams} 声明的 schema 保持一致：
     * 此处缺失的键会被 {@link #applySettingParams} 记 WARN 后丢弃，schema 却仍显示可配。</p>
     */
    private static final List<String> SETTING_BOOLEAN_KEYS = List.of(
            "openPreview", "openDownload", "openRange", "openWatermark", "openRemoteFile", "openThirdPreview",
            "openWebjars", "layout", "showFormat", "showSize", "showTime");

    /**
     * 文件storagesetting 字符串参数映射的参数名
     *
     * <p>与 {@link #SETTING_BOOLEAN_KEYS} 同源约束，须与 schema 声明保持一致。</p>
     */
    private static final List<String> SETTING_STRING_KEYS = List.of("watermark", "layoutType", "imageFilter");

    /**
     * 枚举型字符串参数的枚举类简单名（定义在 {@code FileStorageSetting} 的嵌套枚举里）。
     *
     * <p>只有 {@code layoutType} 在此校验；{@code imageFilter} 的合法值取决于 ImageFilter SPI
     * 实际注册了哪些扩展名，由运行期的 {@code FileStorageServerFilter} 用 SPI 名单校验，
     * 启动路径拿不到该名单，故不在这里重复。</p>
     */
    private static final Map<String, String> SETTING_ENUM_TYPES = Map.of(
            "layoutType", "LayoutType");

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
        if (SERVICE_DISCOVERY_ALIAS.equalsIgnoreCase(type) || SERVICE_DISCOVERY_FILTER_CLASS.equals(type)) {
            return createServiceDiscoveryFilter(descriptor);
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
     * @param type       SPI 名称(如 url-mapping/shell-url-mapping), 不含内置预设别名
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
            throw new IllegalArgumentException("file-storage 插件实例化失败: " + e.getMessage(), e);
        }
    }

    /**
     * 创建服务发现过滤器。
     *
     * <p>框架的 {@code ServiceDiscoveryServerFilter} 只提供 {@code (String)} / {@code (ServiceDiscovery)}
     * 构造且未注册为 ServerFilter SPI，通用实例化路径（无参或 (ServerSetting) 构造）拿不到它，
     * 故与 file-storage 一样按预设别名显式装配。参数经 setter 落位：{@code init} 阶段只会按
     * {@code discoveryName} 解析并拉起 ServiceDiscovery，不会读取 params。</p>
     *
     * @param descriptor 插件描述
     * @return 过滤器实例
     */
    private ServerFilter createServiceDiscoveryFilter(ServerFilterDescriptor descriptor) {
        Map<String, String> params = descriptor.getParams() == null ? Map.of() : descriptor.getParams();
        String discoveryName = discoveryNameOf(params);
        ServiceDiscoveryServerFilter filter = new ServiceDiscoveryServerFilter(discoveryName);
        int routes = 0;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue() == null ? "" : entry.getValue().trim();
            if (value.isEmpty() || DISCOVERY_NAME_KEYS.contains(key)) {
                continue;
            }
            switch (key) {
                case "protocol" -> filter.setProtocol(value);
                case "balance" -> filter.setBalance(value);
                case "scatterId" -> filter.setScatterId(value);
                case "excludeServerId" -> filter.setExcludeServerId(value);
                case "routes" -> routes = applyDiscoveryRoutes(filter, value);
                default -> log.warn("[ServerFilterFactory] service-discovery 忽略未知参数: {}={}", key, value);
            }
        }
        log.info("[ServerFilterFactory] service-discovery 插件已创建: name={}, discoveryName={}, routes={}",
                descriptor.getName(), discoveryName, routes);
        return filter;
    }

    /**
     * 取发现实现 SPI 名：{@code discoveryName} 为平台约定键，
     * {@code discovery.name} 为框架 {@code init} 原生读取的键，两者等价。
     *
     * @param params 插件参数
     * @return 发现实现 SPI 名
     */
    private String discoveryNameOf(Map<String, String> params) {
        for (String key : DISCOVERY_NAME_KEYS) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return DEFAULT_DISCOVERY_NAME;
    }

    /**
     * 解析发现路由参数（{@code /api/order=ORDER-SVC,/api/user=USER-SVC}）并逐条登记。
     *
     * @param filter  发现过滤器
     * @param routes  路由串
     * @return 登记成功的路由条数
     */
    private int applyDiscoveryRoutes(ServiceDiscoveryServerFilter filter, String routes) {
        int registered = 0;
        for (String pair : routes.split("[,;\\n]")) {
            int split = pair.indexOf('=');
            if (split <= 0 || split == pair.length() - 1) {
                log.warn("[ServerFilterFactory] service-discovery 忽略非法路由: {}", pair);
                continue;
            }
            filter.addRoute(pair.substring(0, split).trim(), pair.substring(split + 1).trim());
            registered++;
        }
        return registered;
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
 // 按 setter 名称动态定位方法；需将"方法不存在/调用失败"抛成带参数上下文的异常，
 // ReflectUtils 入口在失败时静默返回 null 会丢失该语义，故保留原生反射（见同行豁免标记）
                if (SETTING_BOOLEAN_KEYS.contains(key)) {
                    Method method = storageSetting.getClass().getMethod(setter, boolean.class); // [P3C 1.10 豁免] 动态 setter 定位需保留失败即抛语义，ReflectUtils 静默降级不适用
                    method.invoke(storageSetting, parseBoolean(value));
                } else if (SETTING_STRING_KEYS.contains(key)) {
                    validateEnumParam(key, value, storageSetting);
                    Method method = storageSetting.getClass().getMethod(setter, String.class); // [P3C 1.10 豁免] 动态 setter 定位需保留失败即抛语义，ReflectUtils 静默降级不适用
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
     * 校验枚举型字符串参数取值合法。
     *
     * <p>运行期的热更新由插件自身校验（{@code FileStorageServerFilter#applyStringSetting}），
     * 但启动与过滤器重建走的是本方法 {@link #applySettingParams}。此处不拦一道的话，
     * 非法值会被直接 set 进 setting：节点照常启动、端口照常监听，只是布局永远落到 else 分支，
     * 直到用户在页面上看到一个下拉框里根本不存在的选项才发现。</p>
     *
     * @param key            参数名
     * @param value          参数值
     * @param storageSetting 目标 setting 实例（用于定位其嵌套枚举类）
     * @throws IllegalArgumentException 取值不在枚举常量集合内
     */
    private void validateEnumParam(String key, String value, Object storageSetting) {
        String enumSimpleName = SETTING_ENUM_TYPES.get(key);
        if (enumSimpleName == null) {
            return;
        }
        Class<?> enumClass;
        try {
            // [P3C 1.10 豁免] 需按 setting 实例的运行时类定位其嵌套枚举，ReflectUtils 无对应入口
            enumClass = Class.forName(storageSetting.getClass().getName() + "$" + enumSimpleName);
        } catch (ClassNotFoundException e) {
            log.debug("[ServerFilterFactory] 未找到枚举类，跳过校验: {}${}", storageSetting.getClass().getName(), enumSimpleName);
            return;
        }
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null) {
            return;
        }
        String trimmed = value == null ? "" : value.trim();
        for (Object constant : constants) {
            if (String.valueOf(constant).equalsIgnoreCase(trimmed)) {
                return;
            }
        }
        throw new IllegalArgumentException(key + " 取值非法: " + value + "，可选值: " + Arrays.toString(constants));
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
 // 先试无参构造、再试 (ServerSetting) 构造的优先级探测，ReflectUtils 无法表达该语义
        try {
            return clazz.getDeclaredConstructor().newInstance(); // [P3C 1.10 豁免] 无参构造缺省需回落 (ServerSetting) 构造，ReflectUtils.instantiate 静默返回 null 无法区分缺省与失败
        } catch (NoSuchMethodException ignored) {
            // 尝试 (服务端setting) 构造
        }
        try {
            return clazz.getDeclaredConstructor(ServerSetting.class).newInstance(setting); // [P3C 1.10 豁免] 需按接口类型精确匹配构造器并在缺省时抛出明确异常，ReflectUtils 按实参类型推断不可靠
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
