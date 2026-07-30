package com.chua.starter.strategy.util;

import com.chua.common.support.lang.json.Json;
import com.chua.starter.strategy.entity.SysCircuitBreakerConfiguration;
import com.chua.starter.strategy.entity.SysDebounceConfiguration;
import com.chua.starter.strategy.entity.SysLimitConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 策略配置 JSON 文件加载器
 * <p>
 * 扫描 {@code classpath*:strategy/*.json} 加载策略配置，
 * 作为数据库配置的替代方案（默认使用 JSON 文件）。
 * </p>
 *
 * @author CH
 * @since 2025-12-28
 */
@Slf4j
public class StrategyFileConfigLoader {

    private static final String LIMITS_PATH = "classpath*:strategy/limits.json";
    private static final String CIRCUIT_BREAKERS_PATH = "classpath*:strategy/circuit-breakers.json";
    private static final String DEBOUNCES_PATH = "classpath*:strategy/debounces.json";

    private final PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * 加载限流配置
     *
     * @return 限流配置列表
     */
    public List<SysLimitConfiguration> loadLimits() {
        return loadConfigs(LIMITS_PATH, SysLimitConfiguration.class);
    }

    /**
     * 加载熔断配置
     *
     * @return 熔断配置列表
     */
    public List<SysCircuitBreakerConfiguration> loadCircuitBreakers() {
        return loadConfigs(CIRCUIT_BREAKERS_PATH, SysCircuitBreakerConfiguration.class);
    }

    /**
     * 加载防抖配置
     *
     * @return 防抖配置列表
     */
    public List<SysDebounceConfiguration> loadDebounces() {
        return loadConfigs(DEBOUNCES_PATH, SysDebounceConfiguration.class);
    }

    @SuppressWarnings("unchecked")
    private <T> List<T> loadConfigs(String path, Class<T> clazz) {
        try {
            Resource[] resources = resolver.getResources(path);
            if (resources.length == 0) {
                log.debug("未找到策略配置文件: {}", path);
                return Collections.emptyList();
            }

            List<T> result = new ArrayList<>();
            for (Resource resource : resources) {
                try (var is = resource.getInputStream()) {
                    List<T> items = Json.fromJsonToList(is, clazz);
                    result.addAll(items);
                    log.debug("已加载策略配置文件: {} ({} 条规则)", resource.getFilename(), items.size());
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("加载策略配置文件失败: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
