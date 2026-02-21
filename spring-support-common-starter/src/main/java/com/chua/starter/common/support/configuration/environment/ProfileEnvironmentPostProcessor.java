package com.chua.starter.common.support.configuration.environment;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.env.PropertiesPropertySourceLoader;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.boot.origin.OriginTrackedValue;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.IOException;
import java.util.*;

import lombok.extern.slf4j.Slf4j;

/**
 * DevOps 环境配置后置处理器
 * <p>
 * 用于根据当前激活的 Profile 动态加载 ops 目录下的配置文件。
 * </p>
 * <p>
 * 功能说明：
 * <ul>
 *     <li>自动扫描 classpath 下 ops-{profile}/ 和 ops-default/ 目录</li>
 *     <li>配置优先级（低 → 高）：ops-default/ < ops-{profile}/</li>
 *     <li>支持属性级深度合并，而非文件级覆盖</li>
 *     <li>可通过 plugin.ops-config.enabled=false 禁用</li>
 * </ul>
 * </p>
 * <p>
 * 配置文件结构示例：
 * <pre>
 * resources/
 *   ├── ops-default/
 *   │   └── application-mybatis.yml  # 兜底配置（所有环境共用）
 *   ├── ops-dev/
 *   │   └── application-mybatis.yml  # dev环境配置（合并到兜底配置）
 *   └── ops-prod/
 *       └── application-mybatis.yml  # prod环境配置（合并到兜底配置）
 * </pre>
 * </p>
 *
 * @author CH
 * @since 2024/12/4
 * @version 2.0.0
 */
@Slf4j
public class ProfileEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
        /**
     * 目录前缀
     */
    private static final String DIR_PREFIX = "ops-";

    /**
     * 兜底配置目录名
     */
    private static final String DEFAULT_PROFILE_DIR = DIR_PREFIX + "default";

    /**
     * 启用开关配置键
     */
    private static final String ENABLED_KEY = "plugin.ops-config.enabled";

    /**
     * 支持的配置文件扩展名
     */
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("yml", "yaml", "properties");

    /**
     * YAML配置加载器
     */
    private final YamlPropertySourceLoader yamlLoader = new YamlPropertySourceLoader();

    /**
     * Properties配置加载器
     */
    private final PropertiesPropertySourceLoader propertiesLoader = new PropertiesPropertySourceLoader();

    @Override
    @SuppressWarnings("ALL")
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        // 检查是否启用
        var enabledValue = environment.getProperty(ENABLED_KEY, "true");
        if (!"true".equalsIgnoreCase(enabledValue)) {
            log.debug("[OPS配置]已禁用 ({}={})", ENABLED_KEY, enabledValue);
            return;
        }

        // 只基于 spring.profiles.active 计算激活的环境目录
        var activeProfiles = new LinkedHashSet<String>();
        var activeProperty = environment.getProperty("spring.profiles.active");
        if (activeProperty != null && !activeProperty.isBlank()) {
            Arrays.stream(activeProperty.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(activeProfiles::add);
        }

        // 只基于 spring.profiles.include 决定加载哪些 application-xxx 配置
        var includeProfiles = new LinkedHashSet<String>();
        var includeProperty = environment.getProperty("spring.profiles.include");
        if (includeProperty != null && !includeProperty.isBlank()) {
            Arrays.stream(includeProperty.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .forEach(includeProfiles::add);
        }

        log.info("[OPS配置]开始加载环境目录配置，当前 active 环境列表: {}，include 配置列表: {}", activeProfiles, includeProfiles);

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MutablePropertySources propertySources = environment.getPropertySources();

        // 收集所有加载的配置文件名（用于日志）
        List<String> defaultLoadedConfigs = new ArrayList<>();
        List<String> profileLoadedConfigs = new ArrayList<>();

        // 合并后的配置：key=文件名，value=合并后的属性
        Map<String, Map<String, Object>> mergedConfigs = new LinkedHashMap<>();

        // 1. 计算需要加载的目录列表：
        //    - 始终加载兜底目录 ops-default/（所有环境共用）
        //    - 环境配置目录仅基于 spring.profiles.active：每个 active 生成一个 ops-{profile}/
        var directories = new LinkedHashSet<String>();
        // 兜底目录
        directories.add(DEFAULT_PROFILE_DIR);
        // 环境目录（仅来源于 spring.profiles.active）
        for (var profile : activeProfiles) {
            directories.add(DIR_PREFIX + profile);
        }

        // 2. 逐个目录加载配置：
        //    - 先处理兜底目录，后处理具体环境目录，保证优先级正确
        for (var dir : directories) {
            if (DEFAULT_PROFILE_DIR.equals(dir)) {
                loadConfigsToMap(resolver, dir, mergedConfigs, defaultLoadedConfigs, includeProfiles);
            } else {
                loadConfigsToMap(resolver, dir, mergedConfigs, profileLoadedConfigs, includeProfiles);
            }
        }

        // 3. 注册合并后的配置到 Environment
        for (Map.Entry<String, Map<String, Object>> entry : mergedConfigs.entrySet()) {
            String filename = entry.getKey();
            Map<String, Object> properties = entry.getValue();
            String sourceName = "ops-config-" + filename;

            if (propertySources.contains(sourceName)) {
                continue;
            }

            // 使用 addLast，确保优先级低于业务配置和配置中心
            propertySources.addLast(new MapPropertySource(sourceName, properties));
            log.debug("[OPS配置]注册配置: {}", sourceName);
        }

        // 日志输出
        if (!defaultLoadedConfigs.isEmpty()) {
            log.info("[OPS配置]加载兜底配置 {}/，共 {} 个: {}", DEFAULT_PROFILE_DIR, defaultLoadedConfigs.size(), defaultLoadedConfigs);
        }
        if (!profileLoadedConfigs.isEmpty()) {
            log.info("[OPS配置]加载环境配置目录前缀 {}，共 {} 个: {}", DIR_PREFIX, profileLoadedConfigs.size(), profileLoadedConfigs);
        }
        if (defaultLoadedConfigs.isEmpty() && profileLoadedConfigs.isEmpty()) {
            log.debug("[OPS配置]未找到任何 ops 目录配置");
        }
    }

    /**
     * 加载指定目录的配置到 Map，支持属性级合并
     *
     * @param resolver      资源解析器
     * @param profileDir    目录名
     * @param mergedConfigs 合并后的配置 Map
     * @param loadedConfigs 已加载的配置列表（用于日志）
     */
    private void loadConfigsToMap(PathMatchingResourcePatternResolver resolver,
                                  String profileDir,
                                  Map<String, Map<String, Object>> mergedConfigs,
                                  List<String> loadedConfigs,
                                  Set<String> includeProfiles) {
        // 未配置 spring.profiles.include 时不加载任何 application-xxx 配置
        if (includeProfiles == null || includeProfiles.isEmpty()) {
            log.info("[OPS配置]spring.profiles.include 未配置，跳过目录: {}/", profileDir);
            return;
        }

        try {
            Resource[] resources = resolver.getResources("classpath:/" + profileDir + "/*");
            log.info("[OPS配置]扫描 {}/ 目录，发现 {} 个资源", profileDir, resources.length);

            // 按文件名排序
            Arrays.sort(resources, Comparator.comparing(r -> {
                try {
                    return r.getFilename() != null ? r.getFilename() : "";
                } catch (Exception e) {
                    return "";
                }
            }));

            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !isSupportedFile(filename)) {
                    continue;
                }

                // 只加载 application-{include}.xxx 格式，且 include 在 spring.profiles.include 列表中
                if (!isIncludedConfig(filename, includeProfiles)) {
                    continue;
                }

                try {
                    Map<String, Object> props = loadResourceAsMap(resource);
                    if (props.isEmpty()) {
                        continue;
                    }

                    // 合并到已有配置
                    mergedConfigs.compute(filename, (k, existing) -> {
                        if (existing == null) {
                            return new LinkedHashMap<>(props);
                        }
                        // 深度合并：新配置覆盖旧配置
                        deepMerge(existing, props);
                        return existing;
                    });

                    loadedConfigs.add(filename);
                    log.info("[OPS配置]加载配置: {}/{}", profileDir, filename);
                } catch (IOException e) {
                    log.warn("[OPS配置]加载配置失败: {}/{}, 原因: {}", profileDir, filename, e.getMessage());
                }
            }
        } catch (IOException e) {
            log.debug("[OPS配置]目录不存在或扫描失败: {}/", profileDir);
        }
    }

    /**
     * 加载资源文件为平坦的 Map
     *
     * @param resource 资源文件
     * @return 属性 Map
     * @throws IOException 加载失败时抛出
     */
    private Map<String, Object> loadResourceAsMap(Resource resource) throws IOException {
        String filename = resource.getFilename();
        if (filename == null) {
            return Collections.emptyMap();
        }

        List<PropertySource<?>> sources;
        String lowerFilename = filename.toLowerCase();
        String sourceName = "temp-" + filename;

        if (lowerFilename.endsWith(".yml") || lowerFilename.endsWith(".yaml")) {
            sources = yamlLoader.load(sourceName, resource);
        } else if (lowerFilename.endsWith(".properties")) {
            sources = propertiesLoader.load(sourceName, resource);
        } else {
            return Collections.emptyMap();
        }

        // 合并所有 PropertySource
        Map<String, Object> result = new LinkedHashMap<>();
        for (PropertySource<?> source : sources) {
            if (source.getSource() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> sourceMap = (Map<String, Object>) source.getSource();
                // 解包 OriginTrackedValue
                sourceMap.forEach((key, value) -> result.put(key, unwrapValue(value)));
            }
        }
        return result;
    }

    /**
     * 判断配置文件名是否属于 spring.profiles.include 指定的 application-xxx 配置
     *
     * @param filename       文件名
     * @param includeProfiles include 列表
     * @return 是否需要加载
     */
    private boolean isIncludedConfig(String filename, Set<String> includeProfiles) {
        if (includeProfiles == null || includeProfiles.isEmpty()) {
            return false;
        }
        if (!filename.startsWith("application-")) {
            return false;
        }
        int dotIndex = filename.indexOf('.');
        if (dotIndex <= "application-".length()) {
            return false;
        }
        String includeName = filename.substring("application-".length(), dotIndex);
        return includeProfiles.contains(includeName);
    }

    /**
     * 深度合并 Map，新值覆盖旧值
     *
     * @param target 目标 Map
     * @param source 源 Map
     */
    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object newValue = entry.getValue();
            Object oldValue = target.get(key);

            if (oldValue instanceof Map && newValue instanceof Map) {
                // 递归合并嵌套 Map
                deepMerge((Map<String, Object>) oldValue, (Map<String, Object>) newValue);
            } else {
                // 直接覆盖
                target.put(key, newValue);
            }
        }
    }

    /**
     * 解包 OriginTrackedValue
     *
     * @param value 原始值
     * @return 解包后的值
     */
    private Object unwrapValue(Object value) {
        if (value instanceof OriginTrackedValue) {
            return ((OriginTrackedValue) value).getValue();
        }
        return value;
    }

    /**
     * 检查文件是否为支持的配置文件
     *
     * @param filename 文件名
     * @return 是否支持
     */
    private boolean isSupportedFile(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0) {
            return false;
        }
        String extension = filename.substring(dotIndex + 1).toLowerCase();
        return SUPPORTED_EXTENSIONS.contains(extension);
    }

    @Override
    public int getOrder() {
        // 在 ConfigDataEnvironmentPostProcessor 之后执行
        // 优先级低于业务配置和配置中心
        return ConfigDataEnvironmentPostProcessor.ORDER + 100;
    }
}

