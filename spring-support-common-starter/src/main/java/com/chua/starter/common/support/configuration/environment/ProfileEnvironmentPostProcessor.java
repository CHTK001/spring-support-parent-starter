package com.chua.starter.common.support.configuration.environment;

import lombok.extern.slf4j.Slf4j;
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
        String enabledValue = environment.getProperty(ENABLED_KEY, "true");
        if (!"true".equalsIgnoreCase(enabledValue)) {
            log.debug("[OPS配置]已禁用 ({}={})", ENABLED_KEY, enabledValue);
            return;
        }

        String active = environment.getProperty("spring.profiles.active");
        log.info("[OPS配置]开始加载环境目录配置，当前环境: {}", active);

        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        MutablePropertySources propertySources = environment.getPropertySources();

        // 收集所有加载的配置文件名（用于日志）
        List<String> defaultLoadedConfigs = new ArrayList<>();
        List<String> profileLoadedConfigs = new ArrayList<>();

        // 合并后的配置：key=文件名，value=合并后的属性
        Map<String, Map<String, Object>> mergedConfigs = new LinkedHashMap<>();

        // 1. 先加载兜底配置（优先级低）
        loadConfigsToMap(resolver, DEFAULT_PROFILE_DIR, mergedConfigs, defaultLoadedConfigs);

        // 2. 再加载环境配置（优先级高，合并到兜底配置）
        if (active != null && !active.isBlank()) {
            String profileDir = DIR_PREFIX + active;
            loadConfigsToMap(resolver, profileDir, mergedConfigs, profileLoadedConfigs);
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
            log.info("[OPS配置]加载环境配置 {}/，共 {} 个: {}", DIR_PREFIX + active, profileLoadedConfigs.size(), profileLoadedConfigs);
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
                                  List<String> loadedConfigs) {
        try {
            Resource[] resources = resolver.getResources("classpath:/" + profileDir + "/*");
            log.debug("[OPS配置]扫描 {}/ 目录，发现 {} 个资源", profileDir, resources.length);

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
                    log.debug("[OPS配置]加载配置: {}/{}", profileDir, filename);
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

