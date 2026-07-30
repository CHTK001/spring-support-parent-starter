package com.chua.starter.strategy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.strategy.config.StrategyProperties;
import com.chua.starter.strategy.entity.SysCircuitBreakerConfiguration;
import com.chua.starter.strategy.mapper.SysCircuitBreakerConfigurationMapper;
import com.chua.starter.strategy.service.SysCircuitBreakerConfigurationService;
import com.chua.starter.strategy.util.StrategyFileConfigLoader;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 熔断配置服务实现
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Slf4j
@Service
public class SysCircuitBreakerConfigurationServiceImpl
        extends ServiceImpl<SysCircuitBreakerConfigurationMapper, SysCircuitBreakerConfiguration>
        implements SysCircuitBreakerConfigurationService {

    /**
     * 内存缓存：路径 -> 熔断配置
     */
    private final Map<String, SysCircuitBreakerConfiguration> circuitBreakerConfigCache = new ConcurrentHashMap<>();

    /**
     * 路径匹配器
     */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * 策略配置属性
     */
    private final StrategyProperties strategyProperties;

    /**
     * JSON 文件配置加载器
     */
    private final StrategyFileConfigLoader fileConfigLoader = new StrategyFileConfigLoader();

    public SysCircuitBreakerConfigurationServiceImpl(StrategyProperties strategyProperties) {
        this.strategyProperties = strategyProperties;
    }

    /**
     * 应用启动时加载配置到内存
     */
    @PostConstruct
    public void init() {
        try {
            refreshCircuitBreakers();
        } catch (Exception e) {
            log.error("熔断配置初始化失败", e);
        }
    }

    @Override
    public IPage<SysCircuitBreakerConfiguration> pageForStrategy(IPage<SysCircuitBreakerConfiguration> page,
                                                                  SysCircuitBreakerConfiguration entity) {
        if ("file".equals(strategyProperties.getConfigSource())) {
            throw new UnsupportedOperationException("JSON文件模式下不允许分页查询");
        }
        LambdaQueryWrapper<SysCircuitBreakerConfiguration> wrapper = new LambdaQueryWrapper<>();

        // 按熔断器名称模糊查询
        if (StringUtils.isNotBlank(entity.getSysCircuitBreakerName())) {
            wrapper.like(SysCircuitBreakerConfiguration::getSysCircuitBreakerName, entity.getSysCircuitBreakerName());
        }

        // 按接口路径模糊查询
        if (StringUtils.isNotBlank(entity.getSysCircuitBreakerPath())) {
            wrapper.like(SysCircuitBreakerConfiguration::getSysCircuitBreakerPath, entity.getSysCircuitBreakerPath());
        }

        // 按状态查询
        if (entity.getSysCircuitBreakerStatus() != null) {
            wrapper.eq(SysCircuitBreakerConfiguration::getSysCircuitBreakerStatus, entity.getSysCircuitBreakerStatus());
        }

        // 按排序值排序
        wrapper.orderByAsc(SysCircuitBreakerConfiguration::getSysCircuitBreakerSort);

        return page(page, wrapper);
    }

    @Override
    public List<SysCircuitBreakerConfiguration> list() {
        if ("file".equals(strategyProperties.getConfigSource())) {
            return new ArrayList<>(circuitBreakerConfigCache.values());
        }
        return super.list();
    }

    @Override
    public List<SysCircuitBreakerConfiguration> listEnabledConfigurations() {
        try {
            if ("file".equals(strategyProperties.getConfigSource())) {
                List<SysCircuitBreakerConfiguration> list = new ArrayList<>();
                for (SysCircuitBreakerConfiguration config : circuitBreakerConfigCache.values()) {
                    if (config.getSysCircuitBreakerStatus() != null && config.getSysCircuitBreakerStatus() == 1) {
                        list.add(config);
                    }
                }
                list.sort(Comparator.comparing(SysCircuitBreakerConfiguration::getSysCircuitBreakerSort,
                        Comparator.nullsLast(Comparator.naturalOrder())));
                return list;
            }
            LambdaQueryWrapper<SysCircuitBreakerConfiguration> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(SysCircuitBreakerConfiguration::getSysCircuitBreakerStatus, 1);
            wrapper.orderByAsc(SysCircuitBreakerConfiguration::getSysCircuitBreakerSort);
            return list(wrapper);
        } catch (Exception e) {
            log.warn("查询熔断配置失败，当前请求将跳过熔断配置匹配", e);
            return Collections.emptyList();
        }
    }

    @Override
    public SysCircuitBreakerConfiguration getByPath(String path) {
        if ("file".equals(strategyProperties.getConfigSource())) {
            // 优先精确匹配
            if (circuitBreakerConfigCache.containsKey(path)) {
                return circuitBreakerConfigCache.get(path);
            }
            // Ant风格模式匹配，按排序值优先级
            return circuitBreakerConfigCache.values().stream()
                    .filter(config -> config.getSysCircuitBreakerStatus() != null
                            && config.getSysCircuitBreakerStatus() == 1)
                    .filter(config -> pathMatcher.match(config.getSysCircuitBreakerPath(), path))
                    .min(Comparator.comparing(SysCircuitBreakerConfiguration::getSysCircuitBreakerSort,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .orElse(null);
        }

        // 获取所有启用的配置
        List<SysCircuitBreakerConfiguration> configs = listEnabledConfigurations();

        // 按排序值查找匹配的配置
        for (SysCircuitBreakerConfiguration config : configs) {
            if (pathMatcher.match(config.getSysCircuitBreakerPath(), path)) {
                log.debug("路径 {} 匹配熔断规则: {}", path, config.getSysCircuitBreakerName());
                return config;
            }
        }

        return null;
    }

    @Override
    public void refreshCircuitBreakers() {
        circuitBreakerConfigCache.clear();

        if ("file".equals(strategyProperties.getConfigSource())) {
            List<SysCircuitBreakerConfiguration> configs = fileConfigLoader.loadCircuitBreakers();
            for (SysCircuitBreakerConfiguration config : configs) {
                if (config.getSysCircuitBreakerStatus() != null && config.getSysCircuitBreakerStatus() == 1) {
                    circuitBreakerConfigCache.put(config.getSysCircuitBreakerPath(), config);
                }
            }
            log.info("熔断配置刷新完成（文件模式），共加载 {} 个配置", configs.size());
            return;
        }

        log.info("开始刷新熔断配置到 Resilience4j...");
        List<SysCircuitBreakerConfiguration> configs = listEnabledConfigurations();

        for (SysCircuitBreakerConfiguration config : configs) {
            try {
                log.debug("刷新熔断规则: {} -> {}", config.getSysCircuitBreakerName(), config.getSysCircuitBreakerPath());
            } catch (Exception e) {
                log.error("刷新熔断规则失败: {}", config.getSysCircuitBreakerName(), e);
            }
        }

        log.info("熔断配置刷新完成，共 {} 条规则", configs.size());
    }

    @Override
    public boolean save(SysCircuitBreakerConfiguration entity) {
        if ("file".equals(strategyProperties.getConfigSource())) {
            throw new UnsupportedOperationException("JSON文件模式下不允许写操作");
        }
        boolean result = super.save(entity);
        if (result && entity.getSysCircuitBreakerStatus() != null && entity.getSysCircuitBreakerStatus() == 1) {
            circuitBreakerConfigCache.put(entity.getSysCircuitBreakerPath(), entity);
        }
        return result;
    }

    @Override
    public boolean updateById(SysCircuitBreakerConfiguration entity) {
        if ("file".equals(strategyProperties.getConfigSource())) {
            throw new UnsupportedOperationException("JSON文件模式下不允许写操作");
        }
        boolean result = super.updateById(entity);
        if (result) {
            if (entity.getSysCircuitBreakerStatus() != null && entity.getSysCircuitBreakerStatus() == 1) {
                circuitBreakerConfigCache.put(entity.getSysCircuitBreakerPath(), entity);
            } else {
                circuitBreakerConfigCache.remove(entity.getSysCircuitBreakerPath());
            }
        }
        return result;
    }

    @Override
    public boolean removeById(java.io.Serializable id) {
        if ("file".equals(strategyProperties.getConfigSource())) {
            throw new UnsupportedOperationException("JSON文件模式下不允许写操作");
        }
        SysCircuitBreakerConfiguration config = getById(id);
        boolean result = super.removeById(id);
        if (result && config != null) {
            circuitBreakerConfigCache.remove(config.getSysCircuitBreakerPath());
        }
        return result;
    }
}
