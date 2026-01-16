package com.chua.starter.strategy.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chua.starter.strategy.entity.SysCircuitBreakerConfiguration;
import com.chua.starter.strategy.mapper.SysCircuitBreakerConfigurationMapper;
import com.chua.starter.strategy.service.SysCircuitBreakerConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.List;

/**
 * 熔断配置服务实现
 *
 * @author CH
 * @version 1.0.0
 * @since 2025-12-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysCircuitBreakerConfigurationServiceImpl 
        extends ServiceImpl<SysCircuitBreakerConfigurationMapper, SysCircuitBreakerConfiguration>
        implements SysCircuitBreakerConfigurationService {

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Override
    public IPage<SysCircuitBreakerConfiguration> pageForStrategy(IPage<SysCircuitBreakerConfiguration> page, 
                                                                  SysCircuitBreakerConfiguration entity) {
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
    public List<SysCircuitBreakerConfiguration> listEnabledConfigurations() {
        LambdaQueryWrapper<SysCircuitBreakerConfiguration> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysCircuitBreakerConfiguration::getSysCircuitBreakerStatus, 1);
        wrapper.orderByAsc(SysCircuitBreakerConfiguration::getSysCircuitBreakerSort);
        return list(wrapper);
    }

    @Override
    public SysCircuitBreakerConfiguration getByPath(String path) {
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
        log.info("开始刷新熔断配置到 Resilience4j...");
        List<SysCircuitBreakerConfiguration> configs = listEnabledConfigurations();

        for (SysCircuitBreakerConfiguration config : configs) {
            try {
                // TODO: 调用 Resilience4j 动态更新熔断器配置
                log.debug("刷新熔断规则: {} -> {}", config.getSysCircuitBreakerName(), config.getSysCircuitBreakerPath());
            } catch (Exception e) {
                log.error("刷新熔断规则失败: {}", config.getSysCircuitBreakerName(), e);
            }
        }

        log.info("熔断配置刷新完成，共 {} 条规则", configs.size());
    }
}
